package com.shoplocker.fssai.scraper;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.shoplocker.fssai.dto.FssaiResponse;
import com.shoplocker.fssai.exception.FssaiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FssaiScraper {

    private static final Logger log = LoggerFactory.getLogger(FssaiScraper.class);

    private final Browser browser;
    private final String portalUrl;
    private final String downloadPath;

    public FssaiScraper(Browser browser,
                        @Value("${fssai.portal.url}") String portalUrl,
                        @Value("${fssai.download.path}") String downloadPath) {
        this.browser = browser;
        this.portalUrl = portalUrl;
        this.downloadPath = downloadPath;
    }

    public FssaiResponse fetchLicenseData(String licenseNumber) {
        log.info("Starting scrape for license: {}", licenseNumber);
        BrowserContext context = null;
        try {
            context = browser.newContext();
            Page page = context.newPage();

            log.debug("Navigating to FoSCoS portal: {}", portalUrl);
            page.navigate(portalUrl);
            page.waitForLoadState(LoadState.NETWORKIDLE);

            handleLanguageSelection(page);

            searchByLicenseNumber(page, licenseNumber);

            waitForSearchResults(page);

            String businessName = extractText(page, "Business Name");
            String address = extractText(page, "Address");
            String status = extractText(page, "Status");
            String validityDate = extractText(page, "Validity");
            String licenseType = extractText(page, "License Type");

            String pdfPath = downloadCertificate(page, licenseNumber);

            log.info("Successfully scraped data for license: {}", licenseNumber);
            return FssaiResponse.success(
                    licenseNumber, businessName, address,
                    status, validityDate, licenseType, pdfPath);

        } catch (Exception e) {
            log.error("Error scraping license {}: {}", licenseNumber, e.getMessage());
            throw new FssaiException("Failed to fetch license data from FSSAI portal: " + e.getMessage(), e);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private void handleLanguageSelection(Page page) {
        try {
            Locator englishBtn = page.locator("button:has-text('English')");
            if (englishBtn.isVisible()) {
                englishBtn.click();
                log.debug("Selected English language");
                page.waitForTimeout(2000);
            }
        } catch (Exception e) {
            log.debug("Language selection not needed or failed: {}", e.getMessage());
        }
    }

    private void searchByLicenseNumber(Page page, String licenseNumber) {
        try {
            Locator searchInput = page.locator("input[type='text'], input[placeholder*='license' i], input[placeholder*='FSSAI' i], input[id*='search' i], input[name*='search' i]");
            if (searchInput.count() == 0) {
                searchInput = page.locator("input").first();
            }
            searchInput.fill(licenseNumber);
            page.waitForTimeout(1000);

            Locator searchBtn = page.locator("button:has-text('Search'), button:has-text('Submit'), button[type='submit']");
            if (searchBtn.count() == 0) {
                searchBtn = page.locator("button").first();
            }
            searchBtn.click();
            page.waitForTimeout(3000);
        } catch (Exception e) {
            throw new FssaiException("Failed to search for license number: " + e.getMessage(), e);
        }
    }

    private void waitForSearchResults(Page page) {
        try {
            page.waitForSelector("table, .details, .result, .license-info, .data", new Page.WaitForSelectorOptions()
                    .setTimeout(15000));
        } catch (Exception e) {
            log.debug("Search results table wait failed, continuing...");
        }
        page.waitForTimeout(2000);
    }

    private String extractText(Page page, String label) {
        try {
            String xpath = String.format("//*[contains(text(),'%s')]/following::td[1] | //*[contains(text(),'%s')]/following::div[1] | //th[contains(text(),'%s')]/following::td[1]", label, label, label);
            Locator locator = page.locator("xpath=" + xpath);
            if (locator.count() > 0) {
                return locator.first().textContent().trim();
            }

            Locator labelLocator = page.locator("text=" + label);
            if (labelLocator.count() > 0) {
                Locator parent = labelLocator.first().locator("..");
                if (parent.count() > 0) {
                    String text = parent.first().textContent().trim();
                    text = text.replace(label, "").trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
            }

            log.warn("Could not extract field: {}", label);
            return "N/A";
        } catch (Exception e) {
            log.warn("Error extracting field {}: {}", label, e.getMessage());
            return "N/A";
        }
    }

    private String downloadCertificate(Page page, String licenseNumber) {
        try {
            Path downloadDir = Paths.get(downloadPath);
            Files.createDirectories(downloadDir);

            Locator downloadLink = findDownloadLink(page);

            if (downloadLink != null && downloadLink.count() > 0) {
                final Locator link = downloadLink;
                Download download = page.waitForDownload(() -> link.first().click());
                String fileName = licenseNumber + ".pdf";
                Path filePath = downloadDir.resolve(fileName);
                download.saveAs(filePath);
                log.info("Certificate downloaded to: {}", filePath);
                return filePath.toString();
            } else {
                log.warn("No download link found for certificate");
                return null;
            }
        } catch (Exception e) {
            log.warn("Failed to download certificate: {}", e.getMessage());
            return null;
        }
    }

    private Locator findDownloadLink(Page page) {
        Locator link = page.locator("a:has-text('Download'), a:has-text('Certificate'), a:has-text('PDF'), a[href$='.pdf'], a[href*='download']");
        if (link.count() == 0) {
            link = page.locator("a").filter(new Locator.FilterOptions().setHasText("Download"));
        }
        return link;
    }
}

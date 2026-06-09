package com.shoplocker.fssai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

@Configuration
public class PlaywrightConfig {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightConfig.class);

    @Value("${fssai.scraper.timeout-seconds:60}")
    private int timeoutSeconds;

    @Lazy
    @Bean(destroyMethod = "close")
    public Playwright playwright() {
        log.info("Initializing Playwright...");
        return Playwright.create();
    }

    @Lazy
    @Bean(destroyMethod = "close")
    public Browser browser(Playwright playwright) {
        log.info("Launching Chromium browser...");
        return playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setTimeout(timeoutSeconds * 1000L));
    }
}

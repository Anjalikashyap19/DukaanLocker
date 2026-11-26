package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.FssaiCertificateData;
import com.shoplocker.fssai.entity.BusinessScale;
import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentStatus;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.OnboardingJob;
import com.shoplocker.fssai.entity.OnboardingJobStatus;
import com.shoplocker.fssai.entity.Role;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.event.AccountCredentialsEvent;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.OnboardingJobRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.util.DlIds;
import com.shoplocker.fssai.util.FssaiCertificateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns one FSSAI certificate file from the feed into an owner account, a shop
 * and a stored FSSAI document.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Create/find the {@link OnboardingJob} row (unique file name = idempotency).</li>
 *   <li>Download the PDF from {@code app.onboarding.pdf-base-url}.</li>
 *   <li>Read its text layer and parse it with {@link FssaiCertificateParser}.</li>
 *   <li>Stop when the certificate does not carry the owner's name, mobile and
 *       email — many Form C PDFs don't, and those must not become accounts.</li>
 *   <li>Stop when the mobile or email is already registered.</li>
 *   <li>Otherwise create user + shop + documents in one transaction, storing the
 *       same PDF as the shop's FSSAI document, then publish the credentials
 *       event (mailed after commit).</li>
 * </ol>
 *
 * <p>{@code msmeUser} stays false on purpose: {@code AuthService.login} rejects
 * email+password login for MSME accounts, which would lock every auto-created
 * owner out of the app.</p>
 */
@Service
public class FssaiOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(FssaiOnboardingService.class);

    private static final Pattern UNSAFE_FILE_CHARS = Pattern.compile("[^A-Za-z0-9._-]");
    private static final Pattern HONORIFIC = Pattern.compile(
            "^(?:MR|MRS|MS|MISS|MASTER|SHRI|SMT|SRI|DR|M/S)\\s*\\.?\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final String PASSWORD_SUFFIX = "@123";
    private static final String ONBOARDING_CATEGORY = "FOOD MANUFACTURING & PROCESSING";
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OnboardingJobRepository jobRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final DocumentRepository documentRepository;
    private final RequiredDocumentService requiredDocumentService;
    private final LocalFileStorageService storageService;
    private final PdfPreprocessor pdfPreprocessor;
    private final FssaiVerificationService fssaiVerificationService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.onboarding.pdf-base-url:https://iadv.shop/fssai/}")
    private String pdfBaseUrl;

    @Value("${app.onboarding.dry-run:true}")
    private boolean dryRun;

    /** Self-reference so {@link #createAccount} runs through the transactional proxy. */
    @Lazy
    @Autowired
    private FssaiOnboardingService self;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public FssaiOnboardingService(OnboardingJobRepository jobRepository,
                                  UserRepository userRepository,
                                  ShopRepository shopRepository,
                                  DocumentRepository documentRepository,
                                  RequiredDocumentService requiredDocumentService,
                                  LocalFileStorageService storageService,
                                  PdfPreprocessor pdfPreprocessor,
                                  FssaiVerificationService fssaiVerificationService,
                                  PasswordEncoder passwordEncoder,
                                  ApplicationEventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.shopRepository = shopRepository;
        this.documentRepository = documentRepository;
        this.requiredDocumentService = requiredDocumentService;
        this.storageService = storageService;
        this.pdfPreprocessor = pdfPreprocessor;
        this.fssaiVerificationService = fssaiVerificationService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes one certificate file. Safe to call repeatedly for the same file:
     * completed and skipped jobs, and jobs that already burned their retries, are
     * left untouched.
     */
    public void process(String rawFileName) {
        String fileName = rawFileName == null ? null : rawFileName.trim();
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        OnboardingJob job = jobRepository.findByFileName(fileName).orElse(null);
        if (job == null) {
            try {
                job = jobRepository.save(new OnboardingJob(fileName));
            } catch (DataIntegrityViolationException e) {
                log.debug("Onboarding job for {} was created concurrently", fileName);
                return;
            }
        } else if (job.getStatus() != OnboardingJobStatus.FAILED
                || job.getAttempts() >= MAX_ATTEMPTS) {
            return;
        }

        job.setAttempts(job.getAttempts() + 1);
        job.setStatus(OnboardingJobStatus.PROCESSING);
        job.setDetail(null);
        job = jobRepository.save(job);

        try {
            byte[] pdf = download(fileName);
            String text = pdfPreprocessor.extractTextLayer(pdf, false).orElse(null);
            if (text == null) {
                finish(job, OnboardingJobStatus.FAILED,
                        "Could not read a text layer from the PDF (scanned or corrupted)");
                return;
            }

            FssaiCertificateData data = FssaiCertificateParser.parse(text, fileName);
            job.setLicenseNumber(trimTo(data.getLicenseNumber(), 32));

            boolean emailPending = !hasUsableEmail(data.getEmailId());
            boolean mobilePending = !hasUsableMobile(data.getMobileNumber());
            if (emailPending || mobilePending) {
                if (!FssaiCertificateData.isRealValue(data.getOwnerName())) {
                    data.setOwnerName(placeholderOwnerName(data.getShopName()));
                }
                if (emailPending) {
                    data.setEmailId(generatePlaceholderEmail(data.getOwnerName()));
                }
                if (mobilePending) {
                    data.setMobileNumber(generatePlaceholderMobile());
                }
                log.info("Onboarding {} uses generated contact details (email={}, mobile={})",
                        fileName, emailPending, mobilePending);
            }

            if (userRepository.existsByMobileNumber(data.getMobileNumber())) {
                finish(job, OnboardingJobStatus.SKIPPED_DUPLICATE,
                        "Mobile number already registered");
                return;
            }
            Optional<User> existingEmail = userRepository.findByEmailId(
                    data.getEmailId().trim().toLowerCase(Locale.ROOT));
            if (existingEmail.isPresent()) {
                finish(job, OnboardingJobStatus.SKIPPED_DUPLICATE,
                        "Email already registered");
                return;
            }

            if (dryRun) {
                log.info("Onboarding DRY RUN {}: owner={} mobile={} email={} shop={} kob={}",
                        fileName, data.getOwnerName(), maskMobile(data.getMobileNumber()),
                        maskEmail(data.getEmailId()), data.getShopName(), data.getKindOfBusiness());
                finish(job, OnboardingJobStatus.DRY_RUN, String.format(
                        "Dry run: would create owner %s, shop %s, licence %s",
                        data.getOwnerName(), data.getShopName(), data.getLicenseNumber()));
                return;
            }

            Created created = self.createAccount(job.getId(), data, pdf, fileName,
                    emailPending, mobilePending);
            job.setUserId(created.userId());
            job.setShopId(created.shopId());
            boolean generatedContact = emailPending || mobilePending;
            finish(job, OnboardingJobStatus.COMPLETED, generatedContact
                    ? "Account created with generated contact details; awaiting owner verification"
                    : "Account and shop created; credentials email queued");
            log.info("Onboarded {} -> user {} shop {} licence {} (generatedContact={})",
                    fileName, created.userId(), created.shopId(), data.getLicenseNumber(), generatedContact);
        } catch (Exception e) {
            log.error("Onboarding failed for {}: {}", fileName, e.getMessage(), e);
            finish(job, OnboardingJobStatus.FAILED, e.getMessage() == null
                    ? "Unexpected error"
                    : e.getMessage());
        }
    }

    /**
     * End-of-day catch-up: give every job that failed today a fresh retry budget
     * and return its file name so the scheduler can reprocess it even when it has
     * already rolled off the feed.
     */
    public List<String> requeueFailedJobsForToday() {
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<OnboardingJob> failed = jobRepository.findByStatusAndCreatedAtBetween(
                OnboardingJobStatus.FAILED, start, start.plusDays(1));
        List<String> fileNames = new ArrayList<>();
        for (OnboardingJob job : failed) {
            job.setAttempts(0);
            jobRepository.save(job);
            fileNames.add(job.getFileName());
        }
        return fileNames;
    }

    /** One-time backfill: re-queue every failed job regardless of when it was created. */
    public List<String> requeueAllFailedJobs() {
        List<OnboardingJob> failed = jobRepository.findByStatus(OnboardingJobStatus.FAILED);
        List<String> fileNames = new ArrayList<>();
        for (OnboardingJob job : failed) {
            job.setAttempts(0);
            jobRepository.save(job);
            fileNames.add(job.getFileName());
        }
        return fileNames;
    }

    /** Result of the transactional creation step. */
    public record Created(Long userId, Long shopId) {
    }

    /**
     * Creates the owner, shop and document rows atomically, then publishes the
     * credentials event. Called via the self proxy so {@code @Transactional}
     * applies; a constraint violation rolls the whole account back.
     */
    @Transactional
    public Created createAccount(Long jobId, FssaiCertificateData data, byte[] pdf, String fileName,
                                 boolean emailPending, boolean mobilePending) {
        String rawPassword = passwordFor(data.getOwnerName());

        User user = new User();
        user.setUserName(data.getOwnerName().trim());
        user.setMobileNumber(data.getMobileNumber().trim());
        user.setEmailId(data.getEmailId().trim().toLowerCase(Locale.ROOT));
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        // Must stay false: MSME users are blocked from email+password login.
        user.setMsmeUser(false);
        user.setContactEmailPending(emailPending);
        user.setContactMobilePending(mobilePending);
        User savedUser = userRepository.save(user);
        savedUser.setDlId(DlIds.forUser(savedUser.getId()));
        savedUser = userRepository.save(savedUser);

        String category = ONBOARDING_CATEGORY;
        BusinessScale scale = resolveScale(data.getLicenseCategory());
        String state = FssaiCertificateParser.detectState(data.getPremisesAddress());
        if (state == null) {
            state = FssaiCertificateData.isRealValue(data.getState()) ? data.getState().trim() : "India";
        }
        String city = FssaiCertificateData.isRealValue(data.getDistrict())
                ? data.getDistrict().trim()
                : "Not Specified";
        String shopName = FssaiCertificateData.isRealValue(data.getShopName())
                ? data.getShopName().trim()
                : "FSSAI Licensed Business";

        Shop shop = new Shop();
        shop.setShopName(shopName);
        shop.setOwnerName(savedUser.getUserName());
        shop.setMobile(savedUser.getMobileNumber());
        shop.setCategory(category.toUpperCase(Locale.ROOT));
        shop.setScale(scale);
        shop.setState(state);
        shop.setCity(city);
        shop.setAddress(data.getPremisesAddress() != null
                ? data.getPremisesAddress()
                : data.getRegisteredAddress());
        shop.setPincode(data.getPincode());
        shop.setOwner(savedUser);
        shop.setPrimary(true);
        shop.setIsFssaiApplicable(true);
        shop.setFssaReturnApplicable(requiresAnnualReturn(data.getKindOfBusiness(), category));
        Shop savedShop = shopRepository.save(shop);

        String documentFileName = safeFileName(fileName);
        String fileUrl = storageService.uploadFile(
                pdf,
                "application/pdf",
                savedUser.getId(),
                savedShop.getId(),
                "fssai_food_license/" + documentFileName);
        String contentHash = sha256Hex(pdf);

        LocalDateTime issuedOn = fssaiVerificationService.parseExpiryDate(data.getIssuedOn());
        LocalDateTime expiryDate = fssaiVerificationService.parseExpiryDate(data.getValidUpto());
        LocalDateTime now = LocalDateTime.now();

        Set<DocumentType> requiredTypes = new LinkedHashSet<>(
                requiredDocumentService.getRequiredDocuments(shop.getCategory(), scale));
        requiredTypes.add(DocumentType.FSSAI_FOOD_LICENSE);

        for (DocumentType type : requiredTypes) {
            Document document = new Document(savedShop, type);
            if (type == DocumentType.FSSAI_FOOD_LICENSE) {
                document.setStatus(DocumentStatus.UPLOADED);
                document.setFileName(fileName);
                document.setFileUrl(fileUrl);
                document.setDocumentNumber(data.getLicenseNumber());
                document.setContentHash(contentHash);
                document.setKindOfBusiness(data.getKindOfBusiness());
                document.setIssueDate(issuedOn != null ? issuedOn : now);
                document.setExpiryDate(expiryDate);
                document.setUploadedAt(now);
                document.setUpdatedAt(now);
            }
            documentRepository.save(document);
        }

        // Published inside the transaction so the mail listener runs only after commit.
        eventPublisher.publishEvent(new AccountCredentialsEvent(
                jobId,
                savedUser.getId(),
                savedUser.getEmailId(),
                rawPassword,
                savedUser.getUserName(),
                savedShop.getShopName(),
                data.getLicenseNumber(),
                emailPending || mobilePending));

        return new Created(savedUser.getId(), savedShop.getId());
    }

    private void finish(OnboardingJob job, OnboardingJobStatus status, String detail) {
        job.setStatus(status);
        job.setDetail(trimTo(detail, 500));
        jobRepository.save(job);
    }

    private byte[] download(String fileName) {
        String base = pdfBaseUrl.endsWith("/") ? pdfBaseUrl : pdfBaseUrl + "/";
        String url = base + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Certificate download failed (HTTP "
                        + response.statusCode() + ")");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new IllegalStateException("Certificate download was empty");
            }
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Certificate download interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Certificate download failed: " + e.getMessage(), e);
        }
    }

    /**
     * Central licence -> LARGE, State licence -> SMALL, Basic registration ->
     * MICRO; unknown shapes default to MICRO (the safest document set).
     */
    private BusinessScale resolveScale(String licenseCategory) {
        if (licenseCategory == null) {
            return BusinessScale.MICRO;
        }
        String value = licenseCategory.toLowerCase(Locale.ROOT);
        if (value.contains("central")) {
            return BusinessScale.LARGE;
        }
        if (value.contains("state")) {
            return BusinessScale.SMALL;
        }
        return BusinessScale.MICRO;
    }

    /** Mirrors the annual-return rule used for FSSAI documents elsewhere. */
    private boolean requiresAnnualReturn(String kindOfBusiness, String category) {
        String kob = kindOfBusiness == null ? "" : kindOfBusiness.toLowerCase(Locale.ROOT);
        if (kob.contains("merchant export")) {
            return false;
        }
        if (kob.matches(".*(manufactur|repack|relabel|importer|imports).*")) {
            return true;
        }
        return kob.isBlank() && category != null
                && category.toUpperCase(Locale.ROOT).contains("MANUFACTURING");
    }

    private static boolean hasUsableEmail(String email) {
        return email != null && email.contains("@") && FssaiCertificateData.isRealValue(email);
    }

    private static boolean hasUsableMobile(String mobile) {
        return mobile != null && mobile.matches("^[6-9]\\d{9}$");
    }

    private static String placeholderOwnerName(String shopName) {
        return FssaiCertificateData.isRealValue(shopName) ? shopName.trim() : "Owner";
    }

    /** ownerfirstname@gmail.com, with a numeric suffix only when that address is taken. */
    String generatePlaceholderEmail(String ownerName) {
        String[] parts = ownerName == null ? new String[0] : ownerName.trim().split("\\s+");
        String first = parts.length == 0 ? "" : parts[0].replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (first.length() < 3 || first.chars().noneMatch(Character::isLetter)) {
            first = "owner";
        }
        String candidate = first + "@gmail.com";
        int suffix = 1;
        while (userRepository.existsByEmailId(candidate)) {
            candidate = first + suffix + "@gmail.com";
            suffix++;
            if (suffix > 10000) {
                throw new IllegalStateException("Could not allocate a placeholder email for " + first);
            }
        }
        return candidate;
    }

    /** A unique, non-existent 10-digit mobile number for placeholder accounts. */
    String generatePlaceholderMobile() {
        for (int attempt = 0; attempt < 200; attempt++) {
            StringBuilder builder = new StringBuilder(10);
            builder.append(6 + RANDOM.nextInt(4));
            for (int i = 1; i < 10; i++) {
                builder.append(RANDOM.nextInt(10));
            }
            String mobile = builder.toString();
            if (!userRepository.existsByMobileNumber(mobile)) {
                return mobile;
            }
        }
        throw new IllegalStateException("Could not allocate a unique placeholder mobile number");
    }

    /** First name of the owner (honorifics dropped) plus the fixed suffix. */
    static String passwordFor(String ownerName) {
        String name = ownerName == null ? "" : HONORIFIC.matcher(ownerName.trim()).replaceFirst("").trim();
        String[] parts = name.split("\\s+");
        String first = parts.length == 0 ? "" : parts[0].replaceAll("[^A-Za-z]", "");
        return (first.isEmpty() ? "User" : first) + PASSWORD_SUFFIX;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash the certificate", e);
        }
    }

    private static String safeFileName(String fileName) {
        String name = UNSAFE_FILE_CHARS.matcher(fileName).replaceAll("_");
        return name.isBlank() ? "fssai_certificate.pdf" : name;
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String maskMobile(String mobile) {
        return mobile == null || mobile.length() < 4
                ? "***"
                : mobile.substring(0, 2) + "***" + mobile.substring(mobile.length() - 3);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = at > 2 ? email.substring(0, 2) + "***" : "***";
        return local + email.substring(at);
    }
}

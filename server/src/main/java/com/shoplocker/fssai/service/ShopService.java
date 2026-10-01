package com.shoplocker.fssai.service;

import java.text.Normalizer;
import java.util.List;

import com.shoplocker.fssai.dto.CreateShopRequest;
import com.shoplocker.fssai.dto.DocumentResponse;
import com.shoplocker.fssai.dto.ShopResponse;
import com.shoplocker.fssai.dto.UpdateShopRequest;
import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class ShopService {

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RequiredDocumentService requiredDocumentService;

    @Autowired
    private LocalFileStorageService localFileStorageService;

    /**
     * Parses the scale field into a {@link BusinessScale}. Accepts the enum name
     * ("SMALL") as well as older client payloads that send the dropdown label
     * ("Small ( &lt; 20 lac/year)") — only the leading word is considered.
     */
    private BusinessScale parseScale(String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT);
        int firstSpace = key.indexOf(' ');
        if (firstSpace > 0) {
            key = key.substring(0, firstSpace);
        }
        try {
            return BusinessScale.valueOf(key);
        } catch (IllegalArgumentException e) {
            throw new FssaiException("Invalid scale: " + raw, FailureCode.INVALID_REQUEST);
        }
    }

    /**
     * Rejects a shop whose name already exists for the same owner. Scoped per
     * owner (user id) so different accounts may use the same business name;
     * the owner name is implied by the account and is not part of the key.
     *
     * <p>The branch is deliberately NOT part of the key — it is a display
     * label, not an identity. Requiring a different branch name to add a
     * "new" business was the hole that let identical shops through, because
     * the Android form auto-fills branch from a map suggestion and two
     * attempts easily disagree. {@code excludeShopId} lets a shop keep its
     * own name on edit.
     *
     * <p>Names are compared after {@link #normalize(String)}: Unicode NFD,
     * diacritics stripped, punctuation folded to spaces, whitespace
     * collapsed, lowercased. So "Caf\u00e9-X" and "Cafe X" collide.
     *
     * <p>A match is exact equality <b>or</b> a token subset in either direction,
     * so the shorter of two names cannot be used to sneak a second copy past the
     * check: "Sehgal" collides with "Sehgal Automobiles" and vice versa, while
     * "Anjali General Store" and "Anjali Electronics" still do not (neither
     * word set contains the other). See {@link #isSameBusinessName}.
     *
     * <p>App-level check only — deliberately no DB unique constraint, so
     * pre-existing duplicates in the database are left untouched.
     *
     * @param branchName unused: a branch is only a label, never a second business
     */
    public void assertNoDuplicateShop(Long ownerId, String shopName, String branchName, Long excludeShopId) {
        String wanted = normalize(shopName);
        if (wanted.isEmpty()) {
            return;
        }
        for (Shop existing : shopRepository.findByOwnerId(ownerId)) {
            if (excludeShopId != null && excludeShopId.equals(existing.getId())) {
                continue;
            }
            if (isSameBusinessName(shopName, existing.getShopName())) {
                String display = existing.getShopName() == null ? "" : existing.getShopName().trim();
                throw new FssaiException(
                        "A business named '" + display + "' already exists for your account",
                        FailureCode.DUPLICATE_SHOP);
            }
        }
    }

    /**
     * True when two business names denote the same business: equal word sets,
     * or one word set contained in the other.
     *
     * <p>Both inputs are folded through {@link #normalize} first, so casing,
     * accents, punctuation and stray whitespace are already gone by the time
     * words are compared. A name that folds to nothing is treated as absent
     * rather than as "matches everything".
     *
     * <p>No token filtering is applied on purpose: a one-letter difference in
     * stop-word handling would reopen the exact hole this exists to close.
     * The cost is that "Sehgal Automobiles Spare Parts" is rejected against
     * "Sehgal Automobiles", which is the intended reading of "one account, one
     * business per name".
     */
    static boolean isSameBusinessName(String left, String right) {
        List<String> leftTokens = tokens(normalize(left));
        List<String> rightTokens = tokens(normalize(right));
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }
        return leftTokens.equals(rightTokens)
                || leftTokens.containsAll(rightTokens)
                || rightTokens.containsAll(leftTokens);
    }

    /**
     * Splits an already-{@link #normalize normalized} name into its words.
     * Returns an empty list for null/blank input.
     */
    private static List<String> tokens(String folded) {
        if (folded == null || folded.isEmpty()) {
            return List.of();
        }
        return List.of(folded.split(" "));
    }

    /**
     * Folds a business name to a comparable form: decompose Unicode (NFD),
     * drop combining marks (accents), replace every non-alphanumeric run
     * with a single space, collapse whitespace, lowercase, trim. Never
     * returns null; a name that folds to nothing is treated as absent by
     * {@link #assertNoDuplicateShop}.
     */
    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String noMarks = decomposed.replaceAll("\\p{M}+", "");
        String folded = noMarks.replaceAll("[^\\p{L}\\p{N}]+", " ");
        return folded.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    @Transactional
    public ShopResponse updateShop(Long id, UpdateShopRequest request) {

        Shop shop = getShopById(id);

        if (request.getShopName() != null) {
            shop.setShopName(request.getShopName());
        }
        if (request.getOwnerName() != null) {
            shop.setOwnerName(request.getOwnerName());
        }
        if (request.getMobile() != null) {
            // Check for duplicate mobile, excluding the current shop
            if (shopRepository.existsByMobileAndIdNot(request.getMobile(), id)) {
                throw new FssaiException("Mobile number already exists", FailureCode.DUPLICATE_MOBILE);
            }
            shop.setMobile(request.getMobile());
        }
        if (request.getCategory() != null) {
            shop.setCategory(request.getCategory().toUpperCase());
        }
        if (request.getScale() != null) {
            shop.setScale(parseScale(request.getScale()));
        }
        if (request.getState() != null) {
            shop.setState(request.getState());
        }
        if (request.getCity() != null) {
            shop.setCity(request.getCity());
        }
        if (request.getBranchName() != null) {
            shop.setBranchName(request.getBranchName());
        }
        if (request.getAddress() != null) {
            shop.setAddress(request.getAddress());
        }
        if (request.getPincode() != null) {
            shop.setPincode(request.getPincode());
        }

        // Same rule as create: a rename must not collide with another shop of
        // the same owner (the shop being edited is excluded from the check).
        assertNoDuplicateShop(shop.getOwner().getId(), shop.getShopName(), shop.getBranchName(), id);

        Shop updated = shopRepository.save(shop);

        return toShopResponse(updated);
    }

    @Transactional
    public ShopResponse createShop(CreateShopRequest request, String userEmail) {


        User owner = userRepository.findByEmailId(userEmail)
                .orElseThrow(() -> new FssaiException("User not found: " + userEmail, FailureCode.USER_NOT_FOUND));

        if (owner.getRole() != Role.ADMIN) {
            throw new FssaiException("Only ADMIN users can create shops", FailureCode.FORBIDDEN);
        }

        BusinessScale scale = parseScale(request.getScale());

        assertNoDuplicateShop(owner.getId(), request.getShopName(), request.getBranchName(), null);

        Shop shop = new Shop();
        shop.setShopName(request.getShopName());
        shop.setOwnerName(request.getOwnerName());
        shop.setMobile(request.getMobile());
        shop.setCategory(request.getCategory().toUpperCase());
        shop.setScale(scale);
        shop.setState(request.getState());
        shop.setCity(request.getCity());
        shop.setBranchName(request.getBranchName());
        shop.setAddress(request.getAddress());
        shop.setPincode(request.getPincode());
        shop.setOwner(owner);

        Shop saved = shopRepository.save(shop);

        return toShopResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> getMyShops(String userEmail) {
        // Try case-insensitive lookup: first by normalized (lowercase) emailId,
        // then by original case, then by mobile — covers all account types
        // including MSME users whose emailId may be stored in any case.
        String normalized = userEmail.trim().toLowerCase();
        User owner = userRepository.findByEmailId(normalized)
                .or(() -> userRepository.findByEmailId(userEmail.trim()))
                .or(() -> userRepository.findByMobileNumber(normalized))
                .orElseThrow(() -> new FssaiException("User not found", FailureCode.USER_NOT_FOUND));

        List<Shop> shops = shopRepository.findByOwnerId(owner.getId());
        return shops.stream().map(this::toShopResponse).toList();
    }

    public ShopResponse getShopResponseById(Long id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new FssaiException("Shop not found: " + id, FailureCode.SHOP_NOT_FOUND));
        return toShopResponse(shop);
    }

    public Shop getShopById(Long id) {
        return shopRepository.findById(id)
                .orElseThrow(() -> new FssaiException("Shop not found: " + id, FailureCode.SHOP_NOT_FOUND));
    }

    /**
     * Returns the document checklist for a shop: uploaded documents merged with required
     * documents not yet uploaded.
     */
    public List<DocumentResponse> getShopDocuments(Long shopId) {
        Shop shop = getShopById(shopId);

        // Get required document types for this shop
        Set<DocumentType> requiredTypes = requiredDocumentService.getRequiredDocuments(
                shop.getCategory(), shop.getScale());

        // Get already uploaded documents
        List<Document> uploadedDocs = documentRepository.findByShopId(shopId);

        // Build response: merge uploaded docs with NOT_UPLOADED entries for missing required docs
        List<DocumentResponse> result = new ArrayList<>();

        for (DocumentType type : requiredTypes) {
            Optional<Document> existing = uploadedDocs.stream()
                    .filter(doc -> doc.getDocumentType() == type)
                    .findFirst();

            if (existing.isPresent()) {
                result.add(toDocumentResponse(existing.get()));
            } else {
                result.add(new DocumentResponse(
                        null, shopId, type,
                        null, null, null,
                        null, null,
                        DocumentStatus.NOT_UPLOADED, 0,
                        null, null));
            }
        }

        return result;
    }

    /**
     * Rejects an upload whose bytes are already filed under a different shop of
     * the same account.
     *
     * <p>This is the backstop for the accidental-duplicate case: a user creates
     * a second copy of an existing business, then re-attaches the very same
     * certificate to it. The type check and the ownership check both pass
     * legitimately there — the document really is theirs and really is the right
     * kind — so only content equality catches it.
     *
     * <p>A {@code null} or blank hash means "nothing to compare against" (a
     * server-generated certificate, or a row written before the column existed)
     * and is always accepted.
     *
     * <p>Read-only and self-contained: safe to call from a controller with no
     * surrounding transaction. The transaction is what makes reading the
     * colliding row's lazy {@code shop.shopName} for the error message legal.
     */
    @Transactional(readOnly = true)
    public void assertDocumentNotReused(Long shopId, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return;
        }
        documentRepository.findByContentHashForOtherShopOfSameOwner(contentHash, shopId)
                .stream()
                .findFirst()
                .ifPresent(existing -> {
                    String otherShop = existing.getShop() == null ? "another shop" : existing.getShop().getShopName();
                    throw new FssaiException(
                            "This document is already filed under another of your businesses ('"
                                    + otherShop + "'). A document belongs to only one business.",
                            FailureCode.DUPLICATE_DOCUMENT);
                });
    }

    /**
     * Handles first-time upload or re-upload of a document for a shop.
     * Increments version on re-upload. Does NOT delete old records.
     *
     * @param contentHash SHA-256 of the stored file, or null when the bytes are
     *                    not available to the caller (server-generated
     *                    certificates). Persisted so the next upload to another
     *                    shop can be recognised as the same physical document —
     *                    the caller is responsible for having already run
     *                    {@link #assertDocumentNotReused}.
     */
    @Transactional
    public DocumentResponse uploadOrReuploadDocument(Long shopId, DocumentType documentType,
                                                      String fileName, String fileUrl,
                                                      String documentNumber,
                                                      java.time.LocalDateTime issueDate,
                                                      java.time.LocalDateTime expiryDate,
                                                      String contentHash) {
        Shop shop = getShopById(shopId);

        Optional<Document> existing = documentRepository.findByShopIdAndDocumentType(shopId, documentType);

        Document doc;
        boolean isNew = false;

        if (existing.isPresent()) {
            doc = existing.get();
            doc.setVersion(doc.getVersion() + 1);
        } else {
            doc = new Document(shop, documentType);
            isNew = true;
        }

        doc.setFileName(fileName);
        doc.setFileUrl(fileUrl);
        doc.setDocumentNumber(documentNumber);
        doc.setContentHash(contentHash);
        doc.setIssueDate(issueDate);
        doc.setExpiryDate(expiryDate);
        doc.setStatus(statusForUploadedDoc(expiryDate));

        if (isNew) {
            doc.setUploadedAt(java.time.LocalDateTime.now());
        }
        doc.setUpdatedAt(java.time.LocalDateTime.now());

        Document saved = documentRepository.save(doc);
        return toDocumentResponse(saved);
    }

    /**
     * A file that arrives with an expiry date already in the past is EXPIRED the
     * moment it is uploaded, not UPLOADED - otherwise the document reads as fine
     * in the locker until the expiry scheduler next runs and corrects it.
     */
    private DocumentStatus statusForUploadedDoc(java.time.LocalDateTime expiryDate) {
        if (expiryDate != null && expiryDate.toLocalDate().isBefore(java.time.LocalDate.now())) {
            return DocumentStatus.EXPIRED;
        }
        return DocumentStatus.UPLOADED;
    }

    public ShopResponse toShopResponse(Shop shop) {
        return new ShopResponse(
                shop.getId(),
                shop.getShopName(),
                shop.getOwnerName(),
                shop.getMobile(),
                shop.getCategory(),
                shop.getScale(),
                shop.getState(),
                shop.getCity(),
                shop.getBranchName(),
                shop.getAddress(),
                shop.getPincode(),
                shop.getOwner() != null ? shop.getOwner().getId() : null,
                shop.getOwner() != null ? shop.getOwner().getEmailId() : null,
                shop.getCreatedAt(),
                shop.getUpdatedAt()
        );
    }

    public DocumentResponse toDocumentResponse(Document doc) {
        String fileUrl = doc.getFileUrl();
        String exposedUrl = fileUrl == null || fileUrl.isBlank() ? null
                : localFileStorageService.extractObjectKeyFromFileUrl(fileUrl);
        return new DocumentResponse(
                doc.getId(),
                doc.getShop().getId(),
                doc.getDocumentType(),
                doc.getFileName(),
                exposedUrl,
                doc.getDocumentNumber(),
                doc.getIssueDate(),
                doc.getExpiryDate(),
                doc.getStatus(),
                doc.getVersion(),
                doc.getUploadedAt(),
                doc.getUpdatedAt()
        );
    }
}

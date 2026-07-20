package com.shoplocker.fssai.service;

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
            try {
                shop.setScale(BusinessScale.valueOf(request.getScale().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new FssaiException("Invalid scale: " + request.getScale(), FailureCode.INVALID_REQUEST);
            }
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

        Shop updated = shopRepository.save(shop);

        return toShopResponse(updated);
    }


    @Autowired
    private S3Service s3Service;

    @Transactional
    public ShopResponse createShop(CreateShopRequest request, String userEmail) {
        if (shopRepository.existsByMobile(request.getMobile())) {
            throw new FssaiException("Mobile number already exists", FailureCode.DUPLICATE_MOBILE);
        }

        User owner = userRepository.findByEmailId(userEmail)
                .orElseThrow(() -> new FssaiException("User not found: " + userEmail, FailureCode.USER_NOT_FOUND));

        if (owner.getRole() != Role.ADMIN) {
            throw new FssaiException("Only ADMIN users can create shops", FailureCode.FORBIDDEN);
        }

        BusinessScale scale;
        try {
            scale = BusinessScale.valueOf(request.getScale().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new FssaiException("Invalid scale: " + request.getScale(), FailureCode.INVALID_REQUEST);
        }

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

    public List<ShopResponse> getMyShops(String userEmail) {
        User owner = userRepository.findByEmailId(userEmail)
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
     * Handles first-time upload or re-upload of a document for a shop.
     * Increments version on re-upload. Does NOT delete old records.
     */
    @Transactional
    public DocumentResponse uploadOrReuploadDocument(Long shopId, DocumentType documentType,
                                                      String fileName, String fileUrl,
                                                      String documentNumber,
                                                      java.time.LocalDateTime issueDate,
                                                      java.time.LocalDateTime expiryDate) {
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
        doc.setIssueDate(issueDate);
        doc.setExpiryDate(expiryDate);
        doc.setStatus(DocumentStatus.UPLOADED);

        if (isNew) {
            doc.setUploadedAt(java.time.LocalDateTime.now());
        }
        doc.setUpdatedAt(java.time.LocalDateTime.now());

        Document saved = documentRepository.save(doc);
        return toDocumentResponse(saved);
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
        return new DocumentResponse(
                doc.getId(),
                doc.getShop().getId(),
                doc.getDocumentType(),
                doc.getFileName(),
                doc.getFileUrl(),
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

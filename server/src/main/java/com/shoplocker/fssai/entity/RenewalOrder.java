package com.shoplocker.fssai.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A user's request to have DukaanLocker renew an expiring/expired document.
 * Ops fulfills it and uploads the certificate into the user's DL-ID locker
 * folder (documents/{dlId}_{shopId}/...).
 */
@Entity
@Table(name = "renewal_orders")
public class RenewalOrder {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(nullable = false, length = 30)
    private String status = STATUS_REQUESTED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Snapshot of the customer's DL ID at request time (ops reference). */
    @Column(name = "dl_id", length = 10)
    private String dlId;

    @Column(name = "requested_at", updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public RenewalOrder() {}

    public RenewalOrder(User user, Shop shop, DocumentType documentType, Long documentId, String dlId) {
        this.user = user;
        this.shop = shop;
        this.documentType = documentType;
        this.documentId = documentId;
        this.dlId = dlId;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (requestedAt == null) requestedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDlId() { return dlId; }
    public void setDlId(String dlId) { this.dlId = dlId; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.shoplocker.fssai.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks which managers are assigned to which shops, by which admin, and whether the assignment
 * is still active. This is preferred over a raw @ManyToMany because it carries assignment metadata
 * (assignedByAdmin, assignedAt, active) that is needed for auditing and deactivation.
 */
@Entity
@Table(name = "manager_shop_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"manager_id", "shop_id"}))
public class ManagerShopAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_admin_id", nullable = false)
    private User assignedByAdmin;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }

    public ManagerShopAssignment() {}

    public ManagerShopAssignment(User manager, Shop shop, User assignedByAdmin) {
        this.manager = manager;
        this.shop = shop;
        this.assignedByAdmin = assignedByAdmin;
        this.active = true;
    }

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getManager() { return manager; }
    public void setManager(User manager) { this.manager = manager; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public User getAssignedByAdmin() { return assignedByAdmin; }
    public void setAssignedByAdmin(User assignedByAdmin) { this.assignedByAdmin = assignedByAdmin; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "organizer_profiles")
@Data
@NoArgsConstructor
@ToString(exclude = "user")
@EqualsAndHashCode(exclude = "user")
public class OrganizerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false, length = 20)
    private String status = "pending";   // pending | approved | rejected

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Feature 14: Payout bank/UPI details ──────────────────────────────────

    @Column(name = "bank_account_number", length = 30)
    @JsonProperty("bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 15)
    @JsonProperty("bank_ifsc")
    private String bankIfsc;

    @Column(name = "upi_id", length = 100)
    @JsonProperty("upi_id")
    private String upiId;

    /** 'bank' | 'upi' — the preferred payout channel. */
    @Column(name = "payout_method", length = 10)
    @JsonProperty("payout_method")
    private String payoutMethod;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

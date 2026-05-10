package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a payout request from an organizer to the platform.
 *
 * status lifecycle:
 *   requested  → organizer submitted a payout request
 *   processing → admin is processing the payment (Razorpay / NEFT initiated)
 *   paid       → payment confirmed; settled_at is stamped
 *   rejected   → admin rejected the request with an admin_note
 *
 * amount = organizer's net earnings for the period
 *        = SUM(ticket_amount - discount_amount) for paid, active bookings
 * platform_fee = SUM(convenience_fee + gst_amount) — for reference only
 */
@Entity
@Table(name = "organizer_payouts")
@Data
public class OrganizerPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK → users.id (the organizer's user row). */
    @Column(name = "organizer_id", nullable = false)
    @JsonProperty("organizer_id")
    private Long organizerId;

    /** Net amount payable to the organizer. */
    @Column(nullable = false)
    private Double amount;

    /** Inclusive start of the payout period. */
    @Column(name = "from_date", nullable = false)
    @JsonProperty("from_date")
    private LocalDate fromDate;

    /** Inclusive end of the payout period. */
    @Column(name = "to_date", nullable = false)
    @JsonProperty("to_date")
    private LocalDate toDate;

    /** requested | processing | paid | rejected */
    @Column(length = 20)
    private String status = "requested";

    /** Number of bookings included in this payout. */
    @Column(name = "booking_count")
    @JsonProperty("booking_count")
    private Integer bookingCount = 0;

    /** Platform's share (convenience + GST) from the same bookings — informational. */
    @Column(name = "platform_fee")
    @JsonProperty("platform_fee")
    private Double platformFee = 0.0;

    /** Razorpay payout ID if paid via Razorpay Payouts API. */
    @Column(name = "razorpay_payout_id", length = 255)
    @JsonProperty("razorpay_payout_id")
    private String razorpayPayoutId;

    /** Admin note (reason for rejection, or payment reference note). */
    @Column(name = "admin_note", columnDefinition = "TEXT")
    @JsonProperty("admin_note")
    private String adminNote;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    @JsonProperty("requested_at")
    private LocalDateTime requestedAt;

    /** Stamped when status transitions to 'paid'. */
    @Column(name = "settled_at")
    @JsonProperty("settled_at")
    private LocalDateTime settledAt;
}

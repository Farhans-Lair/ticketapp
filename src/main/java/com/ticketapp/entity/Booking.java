package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@ToString(exclude = "event")
@EqualsAndHashCode(exclude = "event")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    @JsonProperty("user_id")
    private Long userId;

    @Column(name = "event_id")
    @JsonProperty("event_id")
    private Long eventId;

    @Column(name = "tickets_booked")
    @JsonProperty("tickets_booked")
    private Integer ticketsBooked;

    @Column(name = "price_per_ticket")
    @JsonProperty("price_per_ticket")
    private Double pricePerTicket;

    @Column(name = "ticket_amount")
    @JsonProperty("ticket_amount")
    private Double ticketAmount;

    @Column(name = "convenience_fee")
    @JsonProperty("convenience_fee")
    private Double convenienceFee;

    @Column(name = "gst_amount")
    @JsonProperty("gst_amount")
    private Double gstAmount;

    @Column(name = "total_paid")
    @JsonProperty("total_paid")
    private Double totalPaid;

    @Column(name = "selected_seats", columnDefinition = "TEXT")
    @JsonProperty("selected_seats")
    private String selectedSeats;

    @Column(name = "payment_status", length = 20)
    @JsonProperty("payment_status")
    private String paymentStatus;

    @Column(name = "razorpay_order_id")
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "booking_date")
    @JsonProperty("booking_date")
    private LocalDateTime bookingDate = LocalDateTime.now();

    @Column(name = "ticket_pdf_s3_key", length = 512)
    @JsonProperty("ticket_pdf_s3_key")
    private String ticketPdfS3Key;

    // ── Cancellation fields ────────────────────────────────────────────────────

    /**
     * active       → booking is live
     * cancelled    → cancelled with no refund (or refund failed)
     * refund_pending → refund initiated in Razorpay, awaiting confirmation
     * refunded     → refund confirmed via webhook
     */
    @Column(name = "cancellation_status", length = 20)
    @JsonProperty("cancellation_status")
    private String cancellationStatus = "active";

    @Column(name = "refund_amount")
    @JsonProperty("refund_amount")
    private Double refundAmount;

    @Column(name = "razorpay_refund_id")
    @JsonProperty("razorpay_refund_id")
    private String razorpayRefundId;

    @Column(name = "cancelled_at")
    @JsonProperty("cancelled_at")
    private LocalDateTime cancelledAt;

    /** 5% of (ticket_amount + convenience_fee) */
    @Column(name = "cancellation_fee")
    @JsonProperty("cancellation_fee")
    private Double cancellationFee;

    /** 5% GST on cancellation_fee */
    @Column(name = "cancellation_fee_gst")
    @JsonProperty("cancellation_fee_gst")
    private Double cancellationFeeGst;

    /**
     * hours_before value of the matched policy tier.
     * >= 72 → high tier (full refund minus cancellation charge).
     * <  72 → low tier  (partial refund based on refund_percent).
     */
    @Column(name = "applied_tier_hours")
    @JsonProperty("applied_tier_hours")
    private Integer appliedTierHours;

    @Column(name = "cancellation_invoice_s3_key", length = 512)
    @JsonProperty("cancellation_invoice_s3_key")
    private String cancellationInvoiceS3Key;

    // ── Relationships ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    @JsonIgnore
    private Event event;
}

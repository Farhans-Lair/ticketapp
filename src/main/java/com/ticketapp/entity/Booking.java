package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
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

    @Column(name = "selected_seats")
    @JsonProperty("selected_seats")
    private String selectedSeats;

    @Column(name = "payment_status")
    @JsonProperty("payment_status")
    private String paymentStatus;

    @Column(name = "razorpay_order_id")
    @JsonProperty("razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    @JsonProperty("razorpay_payment_id")
    private String razorpayPaymentId;

    // ✅ FIX 1: booking date (used everywhere)
    @Column(name = "booking_date")
    @JsonProperty("booking_date")
    private LocalDateTime bookingDate = LocalDateTime.now();

    // ✅ FIX 2: ticket PDF S3 key (used in controllers)
    @Column(name = "ticket_pdf_s3_key")
    @JsonProperty("ticket_pdf_s3_key")
    private String ticketPdfS3Key;

    // ✅ FIX 3: event relationship (used in BookingController)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;
}
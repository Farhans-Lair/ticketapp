package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@ToString(exclude = "event")
@EqualsAndHashCode(exclude = "event")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    // @JsonIgnore prevents Jackson from serializing the lazy Hibernate proxy,
    // which causes ERR_INCOMPLETE_CHUNKED_ENCODING on the revenue endpoint.
    // @ToString/@EqualsAndHashCode exclude prevents Lombok from touching the
    // proxy in generated methods, which triggers LazyInitializationException.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", insertable = false, updatable = false)
    private Event event;

    @Column(name = "price_per_ticket")
    private Double pricePerTicket;

    @Column(name = "tickets_booked", nullable = false)
    private Integer ticketsBooked;

    @Column(name = "ticket_amount", nullable = false)
    private Double ticketAmount;

    @Column(name = "convenience_fee", nullable = false)
    private Double convenienceFee;

    @Column(name = "gst_amount", nullable = false)
    private Double gstAmount;

    @Column(name = "total_paid", nullable = false)
    private Double totalPaid;

    @Column(name = "selected_seats", columnDefinition = "TEXT")
    private String selectedSeats;

    @Column(name = "razorpay_order_id", length = 255)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 255)
    private String razorpayPaymentId;

    @Column(name = "payment_status", length = 10)
    private String paymentStatus = "pending";

    @Column(name = "ticket_pdf_s3_key", length = 512)
    private String ticketPdfS3Key;

    @CreationTimestamp
    @Column(name = "booking_date", updatable = false)
    private LocalDateTime bookingDate;
}

package com.ticketapp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

public class PaymentDto {

    @Data
    public static class CreateOrderRequest {
        @NotNull(message = "event_id is required")
        private Long event_id;

        @NotNull(message = "tickets_booked is required")
        @Min(value = 1, message = "tickets_booked must be at least 1")
        private Integer tickets_booked;

        private List<String> selected_seats;
    }

    @Data
    public static class VerifyPaymentRequest {
        private String razorpay_order_id;
        private String razorpay_payment_id;
        private String razorpay_signature;
        private Long   event_id;
        private Integer tickets_booked;
        private List<String> selected_seats;
    }
}

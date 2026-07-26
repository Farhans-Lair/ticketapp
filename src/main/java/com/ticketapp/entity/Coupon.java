package com.ticketapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/* Coupon entity for marketing discounts. */
@Entity
@Table(name = "coupons")
@Data
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /* percent | flat */
    @Column(name = "discount_type", nullable = false, length = 10)
    @JsonProperty("discount_type")
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    @JsonProperty("discount_value")
    private Double discountValue;

    /* Minimum order amount before this coupon can be applied. */
    @Column(name = "min_amount")
    @JsonProperty("min_amount")
    private Double minAmount = 0.0;

    /* Maximum discount in rupees for percent-type coupons. */
    @Column(name = "max_discount")
    @JsonProperty("max_discount")
    private Double maxDiscount;

    @Column(name = "valid_from")
    @JsonProperty("valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    @JsonProperty("valid_to")
    private LocalDateTime validTo;

    /* 0 = unlimited total uses */
    @Column(name = "usage_limit")
    @JsonProperty("usage_limit")
    private Integer usageLimit = 0;

    /* 0 = unlimited per-user uses */
    @Column(name = "per_user_limit")
    @JsonProperty("per_user_limit")
    private Integer perUserLimit = 1;

    /* Atomically incremented on each redemption. */
    @Column(name = "usage_count")
    @JsonProperty("usage_count")
    private Integer usageCount = 0;

    /* active | inactive | expired */
    @Column(length = 20)
    private String status = "active";
}

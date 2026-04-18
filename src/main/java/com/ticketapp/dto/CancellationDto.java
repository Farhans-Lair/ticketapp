package com.ticketapp.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

public class CancellationDto {

    /** PUT /cancellations/policy/{eventId} request body */
    @Data
    public static class UpsertPolicyRequest {
        private List<Map<String, Object>> tiers;
        private Boolean is_cancellation_allowed = true;
    }

    /** Preview and cancel response */
    @Data
    public static class CancellationPreview {
        private boolean cancellationAllowed;
        private String  reason;
        private double  refundAmount;
        private double  refundPercent;
        private double  cancellationFee;
        private double  cancellationFeeGst;
        private double  totalPaid;
        private double  ticketAmount;
        private double  convenienceFee;
        private boolean isHighTier;
        private int     appliedTierHours;
        private int     hoursUntilEvent;
        private List<Map<String, Object>> policy;
    }
}

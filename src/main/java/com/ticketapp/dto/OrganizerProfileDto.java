package com.ticketapp.dto;

import lombok.Data;

@Data
public class OrganizerProfileDto {
    private String business_name;
    private String contact_phone;
    private String gst_number;
    private String address;

    // ── Feature 14: Payout details ────────────────────────────────────────────
    private String bank_account_number;
    private String bank_ifsc;
    private String upi_id;
    private String payout_method;   // 'bank' | 'upi'
}

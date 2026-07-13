package com.ticketapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizerProfileDto {

    @Size(max = 200, message = "Business name must be 200 characters or fewer")
    private String business_name;

    @Pattern(regexp = "^[+\\d\\s\\-()]{7,20}$|^$",
             message = "Contact phone must be 7–20 characters")
    private String contact_phone;

    @Pattern(regexp = "^[0-9A-Z]{15}$|^$",
             message = "GST number must be a valid 15-character GSTIN")
    private String gst_number;

    @Size(max = 300, message = "Address must be 300 characters or fewer")
    private String address;

    // ── Feature 14: Payout details ─────────────────────────────────────────
    @Pattern(regexp = "^[0-9]{9,18}$|^$",
             message = "Bank account number must be 9–18 digits")
    private String bank_account_number;

    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$|^$",
             message = "IFSC code must be in format AAAA0AAAAAA")
    private String bank_ifsc;

    @Size(max = 100, message = "UPI ID must be 100 characters or fewer")
    private String upi_id;

    @Pattern(regexp = "^(bank|upi)$|^$",
             message = "Payout method must be 'bank' or 'upi'")
    private String payout_method;
}

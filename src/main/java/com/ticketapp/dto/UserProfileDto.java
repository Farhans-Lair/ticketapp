package com.ticketapp.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileDto {

    @Size(max = 100, message = "Name must be 100 characters or fewer")
    private String name;

    @Pattern(regexp = "^[+\\d\\s\\-()]{7,20}$|^$",
             message = "Phone number must be 7–20 characters (digits, spaces, +, -, () only)")
    private String phone;

    /** ISO date string: "YYYY-MM-DD" */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$|^$",
             message = "Date of birth must be in YYYY-MM-DD format")
    private String date_of_birth;

    @Size(max = 1000, message = "Bio must be 1 000 characters or fewer")
    private String bio;

    @Size(max = 500, message = "Bank details must be 500 characters or fewer")
    private String bank_details;
}

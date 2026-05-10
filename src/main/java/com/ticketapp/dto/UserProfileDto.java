package com.ticketapp.dto;

import lombok.Data;

@Data
public class UserProfileDto {
    private String name;
    private String phone;
    private String date_of_birth;   // ISO date string: "YYYY-MM-DD"
}

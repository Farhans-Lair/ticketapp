package com.ticketapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.util.List;

@Data
public class EventDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;
    private String location;
    private String city;

    @NotBlank(message = "Event date is required")
    private String event_date;      // ISO string from frontend e.g. "2025-12-31T18:00"

    @NotNull(message = "Price is required")
    private Double price;

    @NotNull(message = "Total tickets is required")
    @Positive(message = "Total tickets must be greater than zero")
    private Integer total_tickets;

    private String category;
    private List<String> images;    // base64 or URLs; stored as JSON string
}

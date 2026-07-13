package com.ticketapp.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class EventDto {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    private String title;

    @Size(max = 5000, message = "Description must be 5 000 characters or fewer")
    private String description;

    @Size(max = 150, message = "Location must be 150 characters or fewer")
    private String location;

    @Size(max = 100, message = "City must be 100 characters or fewer")
    private String city;

    @NotBlank(message = "Event date is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?$",
             message = "Event date must be ISO format: YYYY-MM-DDTHH:MM")
    private String event_date;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be zero or greater")
    private Double price;

    @NotNull(message = "Total tickets is required")
    @Positive(message = "Total tickets must be greater than zero")
    @Max(value = 100_000, message = "Total tickets cannot exceed 100 000")
    private Integer total_tickets;

    @Size(max = 50, message = "Category must be 50 characters or fewer")
    private String category;

    /** Max 10 image URLs per event. */
    @Size(max = 10, message = "Maximum 10 images per event")
    private List<@Size(max = 512, message = "Image URL must be 512 characters or fewer") String> images;
}

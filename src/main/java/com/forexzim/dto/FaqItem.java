package com.forexzim.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FaqItem {
    @NotBlank(message = "FAQ question is required")
    private String question;

    @NotBlank(message = "FAQ answer is required")
    private String answer;
}

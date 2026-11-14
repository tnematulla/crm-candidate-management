package com.crm_demo.dto;

import com.crm_demo.entity.CandidateStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record CandidateRequest(
        @NotBlank(message = "First name is mandatory")
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is mandatory")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        @NotBlank(message = "Email is mandatory")
        @Email(message = "Invalid email format")
        @Size(max = 150, message = "Email cannot exceed 150 characters")
        String email,

        @Size(max = 30, message = "Phone cannot exceed 30 characters")
        String phone,

        @NotBlank(message = "Current title is mandatory")
        @Size(max = 150, message = "Current title cannot exceed 150 characters")
        String currentTitle,

        @NotNull(message = "Years of experience is mandatory")
        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 80, message = "Years of experience cannot exceed 80 years")
        Integer yearsOfExperience,

        @DecimalMin(value = "0.0", inclusive = true, message = "Expected salary cannot be negative")
        BigDecimal expectedSalary,

        @NotBlank(message = "Location is mandatory")
        @Size(max = 100, message = "Location cannot exceed 100 characters")
        String location,

        @NotNull(message = "Skills are mandatory")
        @Size(min = 3, message = "At least 3 skills are required")
        Set<@NotBlank(message = "Skill name cannot be blank") String> skills,

        CandidateStatus status,

        @Size(max = 2000, message = "Summary cannot exceed 2000 characters")
        String summary
) {
}

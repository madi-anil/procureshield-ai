package com.procureshield.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 100, message = "Email must not exceed 100 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "Password must be at least 12 characters and include uppercase, lowercase, digit, and special character"
        )
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 50, message = "Full name must not exceed 50 characters")
        String fullName,

        @NotNull(message = "Department ID is required")
        @Positive(message = "Department ID must be a positive number")
        Long departmentId
) {
}
package com.example.atm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String cardNumber,
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 4) String newPassword) {}

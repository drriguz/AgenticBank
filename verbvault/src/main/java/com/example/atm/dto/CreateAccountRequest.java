package com.example.atm.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String cardNumber,
        @NotBlank String name) {}

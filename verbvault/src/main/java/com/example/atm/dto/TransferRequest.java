package com.example.atm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank String fromCardNumber,
        @NotBlank String toCardNumber,
        @NotNull @Positive BigDecimal amount) {}

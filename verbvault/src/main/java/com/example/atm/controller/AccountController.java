package com.example.atm.controller;

import com.example.atm.dto.ApiResponse;
import com.example.atm.dto.ChangePasswordRequest;
import com.example.atm.dto.CreateAccountRequest;
import com.example.atm.entity.Transaction;
import com.example.atm.entity.Account;
import com.example.atm.service.AtmService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AtmService atmService;

    public AccountController(AtmService atmService) {
        this.atmService = atmService;
    }

    @PostMapping
    public ApiResponse<Account> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = atmService.createAccount(request.cardNumber(), request.name());
        return ApiResponse.ok(account);
    }

    @GetMapping("/{cardNumber}")
    public ApiResponse<Account> get(@PathVariable String cardNumber) {
        Account account = atmService.getAccountByCardNumber(cardNumber);
        return ApiResponse.ok(account);
    }

    @GetMapping("/{cardNumber}/transactions")
    public ApiResponse<List<Transaction>> getHistory(@PathVariable String cardNumber) {
        Account account = atmService.getAccountByCardNumber(cardNumber);
        List<Transaction> transactions = atmService.getHistory(account.getId());
        return ApiResponse.ok(transactions);
    }

    @PatchMapping("/{cardNumber}/password")
    public ApiResponse<Account> changePassword(@PathVariable String cardNumber, @Valid @RequestBody ChangePasswordRequest request) {
        Account account = atmService.getAccountByCardNumber(cardNumber);
        Account updated = atmService.changePassword(account.getId(), request.oldPassword(), request.newPassword());
        return ApiResponse.ok(updated);
    }
}

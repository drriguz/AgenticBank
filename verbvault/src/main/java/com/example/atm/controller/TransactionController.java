package com.example.atm.controller;

import com.example.atm.dto.*;
import com.example.atm.entity.Account;
import com.example.atm.service.AtmService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final AtmService atmService;

    public TransactionController(AtmService atmService) {
        this.atmService = atmService;
    }

    @PostMapping("/deposit")
    public ApiResponse<Account> deposit(@Valid @RequestBody DepositRequest request) {
        Account account = atmService.getAccountByCardNumber(request.cardNumber());
        Account updated = atmService.deposit(account.getId(), request.amount());
        return ApiResponse.ok(updated);
    }

    @PostMapping("/withdraw")
    public ApiResponse<Account> withdraw(@Valid @RequestBody DepositRequest request) {
        Account account = atmService.getAccountByCardNumber(request.cardNumber());
        Account updated = atmService.withdraw(account.getId(), request.amount());
        return ApiResponse.ok(updated);
    }

    @PostMapping("/transfer")
    public ApiResponse<List<Account>> transfer(@Valid @RequestBody TransferRequest request) {
        Account from = atmService.getAccountByCardNumber(request.fromCardNumber());
        Account to = atmService.getAccountByCardNumber(request.toCardNumber());
        List<Account> accounts = atmService.transfer(from.getId(), to.getId(), request.amount());
        return ApiResponse.ok(accounts);
    }
}

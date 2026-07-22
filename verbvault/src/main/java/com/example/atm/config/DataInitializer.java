package com.example.atm.config;

import com.example.atm.entity.Account;
import com.example.atm.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Map<String, String> TEST_CARDS = Map.ofEntries(
            Map.entry("4242424242424242", "John Smith"),
            Map.entry("4000056655565556", "Mary Johnson"),
            Map.entry("5555555555554444", "Robert Williams"),
            Map.entry("2223003122003222", "Patricia Brown"),
            Map.entry("378282246310005", "James Jones"),
            Map.entry("6011111111111117", "Linda Garcia"),
            Map.entry("3530111333000000", "Michael Miller"),
            Map.entry("6200000000000005", "Elizabeth Davis"),
            Map.entry("4000000000000002", "David Martinez"),
            Map.entry("4000000000000069", "Sarah Wilson"),
            Map.entry("5336410088888888", "Test User")
    );

    private final AccountRepository accountRepo;

    public DataInitializer(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    @Override
    public void run(String... args) {
        if (accountRepo.count() > 0) return;

        for (var entry : TEST_CARDS.entrySet()) {
            Account account = new Account(entry.getKey(), entry.getValue());
            account.deposit(new BigDecimal("5000.00"));
            accountRepo.save(account);
        }
    }
}

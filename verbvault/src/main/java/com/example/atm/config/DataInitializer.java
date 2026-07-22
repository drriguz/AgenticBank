package com.example.atm.config;

import com.example.atm.entity.Account;
import com.example.atm.entity.Transaction;
import com.example.atm.repository.AccountRepository;
import com.example.atm.repository.TransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final TransactionRepository txnRepo;

    public DataInitializer(AccountRepository accountRepo, TransactionRepository txnRepo) {
        this.accountRepo = accountRepo;
        this.txnRepo = txnRepo;
    }

    @Override
    public void run(String... args) {
        if (accountRepo.count() > 0) return;

        for (var entry : TEST_CARDS.entrySet()) {
            Account account = new Account(entry.getKey(), entry.getValue());
            account.deposit(new BigDecimal("5000.00"));
            accountRepo.save(account);
        }

        // Add sample transactions for John Smith (4242424242424242)
        Account john = accountRepo.findByCardNumber("4242424242424242").orElseThrow();
        Long johnId = john.getId();

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Transaction t1 = new Transaction(Transaction.TransactionType.DEPOSIT, null, johnId, new BigDecimal("1000.00"));
        t1.setTimestamp(now.minusDays(2).withHour(10).withMinute(30));
        txnRepo.save(t1);

        Transaction t2 = new Transaction(Transaction.TransactionType.WITHDRAW, johnId, null, new BigDecimal("200.00"));
        t2.setTimestamp(now.minusDays(1).withHour(14).withMinute(15));
        txnRepo.save(t2);

        Transaction t3 = new Transaction(Transaction.TransactionType.DEPOSIT, null, johnId, new BigDecimal("500.00"));
        t3.setTimestamp(now.withHour(9).withMinute(0));
        txnRepo.save(t3);
    }
}

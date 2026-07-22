package com.example.atm.service;

import com.example.atm.entity.Transaction;
import com.example.atm.entity.Account;
import com.example.atm.repository.TransactionRepository;
import com.example.atm.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AtmService {

    public static final BigDecimal ATM_CASH_LIMIT = new BigDecimal("10000.00");

    private final AccountRepository accountRepo;
    private final TransactionRepository txnRepo;

    public AtmService(AccountRepository accountRepo, TransactionRepository txnRepo) {
        this.accountRepo = accountRepo;
        this.txnRepo = txnRepo;
    }

    public Account createAccount(String cardNumber, String name) {
        return accountRepo.save(new Account(cardNumber, name));
    }

    public Account getAccount(Long id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
    }

    public Account getAccountByCardNumber(String cardNumber) {
        return accountRepo.findByCardNumber(cardNumber)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + cardNumber));
    }

    @Transactional
    public Account deposit(Long accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        account.deposit(amount);
        txnRepo.save(new Transaction(Transaction.TransactionType.DEPOSIT, null, accountId, amount));
        return account;
    }

    @Transactional
    public Account withdraw(Long accountId, BigDecimal amount) {
        if (amount.compareTo(ATM_CASH_LIMIT) > 0) {
            throw new IllegalArgumentException("ATM cash limit exceeded. Maximum withdrawal is " + ATM_CASH_LIMIT);
        }
        Account account = getAccount(accountId);
        account.withdraw(amount);
        txnRepo.save(new Transaction(Transaction.TransactionType.WITHDRAW, accountId, null, amount));
        return account;
    }

    @Transactional
    public List<Account> transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        Account fromAccount = getAccount(fromAccountId);
        Account toAccount = getAccount(toAccountId);

        fromAccount.withdraw(amount);
        toAccount.deposit(amount);

        txnRepo.save(new Transaction(Transaction.TransactionType.TRANSFER, fromAccountId, toAccountId, amount));

        return List.of(fromAccount, toAccount);
    }

    public Account changePassword(Long accountId, String oldPassword, String newPassword) {
        Account account = getAccount(accountId);
        if (!account.getPassword().equals(oldPassword)) {
            throw new IllegalArgumentException("Incorrect old password");
        }
        account.setPassword(newPassword);
        return accountRepo.save(account);
    }

    public List<Transaction> getHistory(Long accountId) {
        if (!accountRepo.existsById(accountId)) {
            throw new NoSuchElementException("Account not found: " + accountId);
        }
        return txnRepo.findByFromAccountIdOrToAccountIdOrderByTimestampDesc(accountId, accountId);
    }

    public List<Transaction> getHistory(Long accountId, LocalDateTime start, LocalDateTime end) {
        if (!accountRepo.existsById(accountId)) {
            throw new NoSuchElementException("Account not found: " + accountId);
        }
        return txnRepo.findAccountTransactionsBetween(accountId, start, end);
    }
}

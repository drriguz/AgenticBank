package com.example.atm.service;

import com.example.atm.entity.Transaction;
import com.example.atm.entity.Account;
import com.example.atm.repository.TransactionRepository;
import com.example.atm.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtmServiceTest {

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private TransactionRepository txnRepo;

    private AtmService service;

    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        service = new AtmService(accountRepo, txnRepo);
        alice = new Account("4242424242424242", "Alice");
        alice.setId(1L);
        alice.deposit(new BigDecimal("1000.00"));

        bob = new Account("4000056655565556", "Bob");
        bob.setId(2L);
        bob.deposit(new BigDecimal("500.00"));
    }

    @Test
    void createAccount_shouldSaveAndReturnAccount() {
        when(accountRepo.save(any(Account.class))).thenReturn(alice);

        Account result = service.createAccount("4242424242424242", "Alice");

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getCardNumber()).isEqualTo("4242424242424242");
    }

    @Test
    void getAccount_shouldReturnAccount() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));

        Account result = service.getAccount(1L);

        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void getAccount_shouldThrowWhenNotFound() {
        when(accountRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Account not found: 99");
    }

    @Test
    void getAccountByCardNumber_shouldReturnAccount() {
        when(accountRepo.findByCardNumber("4242424242424242")).thenReturn(Optional.of(alice));

        Account result = service.getAccountByCardNumber("4242424242424242");

        assertThat(result.getName()).isEqualTo("Alice");
    }

    @Test
    void getAccountByCardNumber_shouldThrowWhenNotFound() {
        when(accountRepo.findByCardNumber("0000000000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccountByCardNumber("0000000000000000"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void deposit_shouldIncreaseBalanceAndCreateTransaction() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(txnRepo.save(any(Transaction.class))).thenReturn(mock(Transaction.class));

        Account result = service.deposit(1L, new BigDecimal("200.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("1200.00");
    }

    @Test
    void withdraw_shouldDecreaseBalanceAndCreateTransaction() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(txnRepo.save(any(Transaction.class))).thenReturn(mock(Transaction.class));

        Account result = service.withdraw(1L, new BigDecimal("300.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void withdraw_shouldThrowWhenInsufficientBalance() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> service.withdraw(1L, new BigDecimal("2000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void withdraw_shouldThrowWhenExceedsAtmCashLimit() {
        assertThatThrownBy(() -> service.withdraw(1L, new BigDecimal("15000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATM cash limit exceeded");
    }

    @Test
    void transfer_shouldMoveMoneyBetweenAccounts() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(accountRepo.findById(2L)).thenReturn(Optional.of(bob));
        when(txnRepo.save(any(Transaction.class))).thenReturn(mock(Transaction.class));

        List<Account> result = service.transfer(1L, 2L, new BigDecimal("200.00"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBalance()).isEqualByComparingTo("800.00");
        assertThat(result.get(1).getBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void transfer_shouldThrowWhenInsufficientBalance() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(accountRepo.findById(2L)).thenReturn(Optional.of(bob));

        assertThatThrownBy(() -> service.transfer(1L, 2L, new BigDecimal("2000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void transfer_shouldThrowWhenSelfTransfer() {
        assertThatThrownBy(() -> service.transfer(1L, 1L, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot transfer to the same account");
    }

    @Test
    void getHistory_shouldReturnTransactions() {
        when(accountRepo.existsById(1L)).thenReturn(true);
        when(txnRepo.findByFromAccountIdOrToAccountIdOrderByTimestampDesc(1L, 1L))
                .thenReturn(List.of(mock(Transaction.class)));

        List<Transaction> result = service.getHistory(1L);

        assertThat(result).hasSize(1);
    }
}

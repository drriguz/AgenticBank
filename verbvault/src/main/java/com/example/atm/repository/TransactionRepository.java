package com.example.atm.repository;

import com.example.atm.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFromAccountIdOrToAccountIdOrderByTimestampDesc(Long fromAccountId, Long toAccountId);

    @Query("SELECT t FROM Transaction t WHERE (t.fromAccountId = ?1 OR t.toAccountId = ?1) AND t.timestamp BETWEEN ?2 AND ?3 ORDER BY t.timestamp DESC")
    List<Transaction> findAccountTransactionsBetween(Long accountId, LocalDateTime start, LocalDateTime end);
}

package com.example.atm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    public enum TransactionType {
        DEPOSIT, WITHDRAW, TRANSFER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column
    private Long fromAccountId;

    @Column
    private Long toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    protected Transaction() {}

    public Transaction(TransactionType type, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        this.type = type;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
    }

    @PrePersist
    void prePersist() {
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public TransactionType getType() { return type; }
    public Long getFromAccountId() { return fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
}

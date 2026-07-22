# AI ATM Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a REST API for banking operations (deposit, withdraw, transfer, balance, history) backed by SQLite.

**Architecture:** Standard layered Spring Boot app — Controllers handle HTTP, one AtmService holds all business logic with @Transactional, Spring Data JPA repositories talk to SQLite. Optimistic locking via @Version for concurrency safety. No authentication layer (accountId passed in request body per hackathon scope).

**Tech Stack:** JDK 21, Spring Boot 4.x LTS, Spring Data JPA, SQLite, Maven, Docker (eclipse-temurin:21-jre-alpine)

## Global Constraints

- JDK 21 LTS
- Spring Boot 4.x LTS (parent POM 4.0.0)
- SQLite via `org.xerial:sqlite-jdbc`
- Hibernate SQLite dialect via `org.hibernate.community-dialects:hibernate-community-dialects`
- All API responses use wrapper `{"success": bool, "data": T, "error": string|null}`
- Amounts use BigDecimal, precision 19 scale 2, validated @Positive
- accountId for auth in request body (no security framework)
- All write operations @Transactional
- Optimistic locking via @Version on Account entity
- Self-transfer (`fromAccountId == toAccountId`) prohibited
- Package: `com.example.atm`

---

### Task 1: Project Scaffolding

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/resources/application.properties`
- Create: `src/main/java/com/example/atm/AtmApplication.java`

**Interfaces:**
- Produces: `AtmApplication` main class, Maven build config with all deps, Spring config

- [ ] **Step 1: Write pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.0</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>atm</artifactId>
    <version>0.1.0</version>
    <name>AI ATM Simulator</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-community-dialects</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write .gitignore**

```
target/
*.db
*.db-journal
.idea/
*.iml
```

- [ ] **Step 3: Write application.properties**

```properties
spring.application.name=atm
spring.datasource.url=jdbc:sqlite:atm.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

- [ ] **Step 4: Write AtmApplication.java**

```java
package com.example.atm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AtmApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtmApplication.class, args);
    }
}
```

- [ ] **Step 5: Create directory structure**

Run: `mkdir -p src/main/java/com/example/atm/{controller,entity,repository,service,dto,exception} src/test/java/com/example/atm`

- [ ] **Step 6: Verify project compiles**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add pom.xml .gitignore src/main/resources/application.properties src/main/java/com/example/atm/AtmApplication.java
git commit -m "feat: scaffold Spring Boot project with SQLite dependencies"
```

---

### Task 2: Entity Classes

**Files:**
- Create: `src/main/java/com/example/atm/entity/Account.java`
- Create: `src/main/java/com/example/atm/entity/Transaction.java`

**Interfaces:**
- Consumes: JPA auto-DDL from `application.properties` (Task 1)
- Produces: `Account` entity (id: Long, name: String, balance: BigDecimal, version: Long), `Transaction` entity (id: Long, type: TransactionType, fromAccountId: Long|null, toAccountId: Long|null, amount: BigDecimal, timestamp: LocalDateTime), `TransactionType` enum (DEPOSIT, WITHDRAW, TRANSFER)

- [ ] **Step 1: Write Account.java**

```java
package com.example.atm.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private Long version;

    protected Account() {}

    public Account(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
```

- [ ] **Step 2: Write Transaction.java**

```java
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
    public void setId(Long id) { this.id = id; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public Long getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(Long fromAccountId) { this.fromAccountId = fromAccountId; }
    public Long getToAccountId() { return toAccountId; }
    public void setToAccountId(Long toAccountId) { this.toAccountId = toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/atm/entity/Account.java src/main/java/com/example/atm/entity/Transaction.java
git commit -m "feat: add Account and Transaction JPA entities with optimistic locking"
```

---

### Task 3: Repositories

**Files:**
- Create: `src/main/java/com/example/atm/repository/AccountRepository.java`
- Create: `src/main/java/com/example/atm/repository/TransactionRepository.java`

**Interfaces:**
- Consumes: `Account` entity (Task 2), `Transaction` entity (Task 2)
- Produces: `AccountRepository extends JpaRepository<Account, Long>`, `TransactionRepository extends JpaRepository<Transaction, Long>` with `findByFromAccountIdOrToAccountIdOrderByTimestampDesc(Long, Long): List<Transaction>`

- [ ] **Step 1: Write AccountRepository.java**

```java
package com.example.atm.repository;

import com.example.atm.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
```

- [ ] **Step 2: Write TransactionRepository.java**

```java
package com.example.atm.repository;

import com.example.atm.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByFromAccountIdOrToAccountIdOrderByTimestampDesc(Long fromAccountId, Long toAccountId);
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/atm/repository/AccountRepository.java src/main/java/com/example/atm/repository/TransactionRepository.java
git commit -m "feat: add Spring Data JPA repositories"
```

---

### Task 4: DTOs and Exception Handling

**Files:**
- Create: `src/main/java/com/example/atm/dto/ApiResponse.java`
- Create: `src/main/java/com/example/atm/dto/CreateAccountRequest.java`
- Create: `src/main/java/com/example/atm/dto/DepositRequest.java`
- Create: `src/main/java/com/example/atm/dto/WithdrawRequest.java`
- Create: `src/main/java/com/example/atm/dto/TransferRequest.java`
- Create: `src/main/java/com/example/atm/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Consumes: nothing from earlier tasks (DTOs are standalone)
- Produces: request DTO records with Jakarta validation, `ApiResponse<T>` generic wrapper, `GlobalExceptionHandler` with `@RestControllerAdvice`

- [ ] **Step 1: Write ApiResponse.java**

```java
package com.example.atm.dto;

public record ApiResponse<T>(boolean success, T data, String error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String error) {
        return new ApiResponse<>(false, null, error);
    }
}
```

- [ ] **Step 2: Write CreateAccountRequest.java**

```java
package com.example.atm.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(@NotBlank String name) {}
```

- [ ] **Step 3: Write DepositRequest.java**

```java
package com.example.atm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record DepositRequest(
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount) {}
```

- [ ] **Step 4: Write WithdrawRequest.java**

```java
package com.example.atm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record WithdrawRequest(
        @NotNull Long accountId,
        @NotNull @Positive BigDecimal amount) {}
```

- [ ] **Step 5: Write TransferRequest.java**

```java
package com.example.atm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @Positive BigDecimal amount) {}
```

- [ ] **Step 6: Write GlobalExceptionHandler.java**

```java
package com.example.atm.exception;

import com.example.atm.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ApiResponse.fail(message));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Account was modified by another request, please retry"));
    }
}
```

- [ ] **Step 7: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/atm/dto/ src/main/java/com/example/atm/exception/
git commit -m "feat: add DTOs and global exception handler with optimistic lock handling"
```

---

### Task 5: Business Logic

**Files:**
- Create: `src/main/java/com/example/atm/service/AtmService.java`

**Interfaces:**
- Consumes: `AccountRepository`, `TransactionRepository` (Task 3), `Account` entity (Task 2), `Transaction` entity (Task 2)
- Produces: `AtmService` with methods:
  - `createAccount(String name): Account`
  - `getAccount(Long id): Account` — throws `NoSuchElementException` if not found
  - `deposit(Long accountId, BigDecimal amount): Account`
  - `withdraw(Long accountId, BigDecimal amount): Account` — throws `IllegalArgumentException` if insufficient balance
  - `transfer(Long fromAccountId, Long toAccountId, BigDecimal amount): List<Account>` — throws `IllegalArgumentException` if insufficient balance or self-transfer
  - `getHistory(Long accountId): List<Transaction>`

- [ ] **Step 1: Write AtmService.java**

```java
package com.example.atm.service;

import com.example.atm.entity.Transaction;
import com.example.atm.entity.Account;
import com.example.atm.repository.TransactionRepository;
import com.example.atm.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AtmService {

    private final AccountRepository accountRepo;
    private final TransactionRepository txnRepo;

    public AtmService(AccountRepository accountRepo, TransactionRepository txnRepo) {
        this.accountRepo = accountRepo;
        this.txnRepo = txnRepo;
    }

    public Account createAccount(String name) {
        return accountRepo.save(new Account(name));
    }

    public Account getAccount(Long id) {
        return accountRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
    }

    @Transactional
    public Account deposit(Long accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        account.setBalance(account.getBalance().add(amount));
        accountRepo.save(account);

        Transaction txn = new Transaction(Transaction.TransactionType.DEPOSIT, null, accountId, amount);
        txnRepo.save(txn);

        return account;
    }

    @Transactional
    public Account withdraw(Long accountId, BigDecimal amount) {
        Account account = getAccount(accountId);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepo.save(account);

        Transaction txn = new Transaction(Transaction.TransactionType.WITHDRAW, accountId, null, amount);
        txnRepo.save(txn);

        return account;
    }

    @Transactional
    public List<Account> transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        Account fromAccount = getAccount(fromAccountId);
        Account toAccount = getAccount(toAccountId);

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepo.save(fromAccount);
        accountRepo.save(toAccount);

        Transaction txn = new Transaction(Transaction.TransactionType.TRANSFER, fromAccountId, toAccountId, amount);
        txnRepo.save(txn);

        return List.of(fromAccount, toAccount);
    }

    public List<Transaction> getHistory(Long accountId) {
        getAccount(accountId); // verify exists
        return txnRepo.findByFromAccountIdOrToAccountIdOrderByTimestampDesc(accountId, accountId);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/atm/service/AtmService.java
git commit -m "feat: add AtmService with transactional banking operations and self-transfer guard"
```

---

### Task 6: Controllers

**Files:**
- Create: `src/main/java/com/example/atm/controller/AccountController.java`
- Create: `src/main/java/com/example/atm/controller/TransactionController.java`

**Interfaces:**
- Consumes: `AtmService` (Task 5), DTOs (Task 4)
- Produces: REST endpoints per spec

- [ ] **Step 1: Write AccountController.java**

```java
package com.example.atm.controller;

import com.example.atm.dto.ApiResponse;
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
        Account account = atmService.createAccount(request.name());
        return ApiResponse.ok(account);
    }

    @GetMapping("/{id}")
    public ApiResponse<Account> get(@PathVariable Long id) {
        Account account = atmService.getAccount(id);
        return ApiResponse.ok(account);
    }

    @GetMapping("/{id}/transactions")
    public ApiResponse<List<Transaction>> getHistory(@PathVariable Long id) {
        List<Transaction> transactions = atmService.getHistory(id);
        return ApiResponse.ok(transactions);
    }
}
```

- [ ] **Step 2: Write TransactionController.java**

```java
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
        Account account = atmService.deposit(request.accountId(), request.amount());
        return ApiResponse.ok(account);
    }

    @PostMapping("/withdraw")
    public ApiResponse<Account> withdraw(@Valid @RequestBody WithdrawRequest request) {
        Account account = atmService.withdraw(request.accountId(), request.amount());
        return ApiResponse.ok(account);
    }

    @PostMapping("/transfer")
    public ApiResponse<List<Account>> transfer(@Valid @RequestBody TransferRequest request) {
        List<Account> accounts = atmService.transfer(request.fromAccountId(), request.toAccountId(), request.amount());
        return ApiResponse.ok(accounts);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/atm/controller/AccountController.java src/main/java/com/example/atm/controller/TransactionController.java
git commit -m "feat: add REST controllers for accounts and transactions"
```

---

### Task 7: Unit Tests for AtmService

**Files:**
- Create: `src/test/java/com/example/atm/service/AtmServiceTest.java`

**Interfaces:**
- Consumes: `AtmService` (Task 5), `AccountRepository`, `TransactionRepository` (mocked)
- Produces: 10 unit tests covering all operations + edge cases

- [ ] **Step 1: Write AtmServiceTest.java**

```java
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
        alice = new Account("Alice");
        alice.setId(1L);
        alice.setBalance(new BigDecimal("1000.00"));

        bob = new Account("Bob");
        bob.setId(2L);
        bob.setBalance(new BigDecimal("500.00"));
    }

    @Test
    void createAccount_shouldSaveAndReturnAccount() {
        when(accountRepo.save(any(Account.class))).thenReturn(alice);

        Account result = service.createAccount("Alice");

        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getId()).isEqualTo(1L);
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
    void deposit_shouldIncreaseBalanceAndCreateTransaction() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(accountRepo.save(any(Account.class))).thenReturn(alice);
        when(txnRepo.save(any(Transaction.class))).thenReturn(new Transaction());

        Account result = service.deposit(1L, new BigDecimal("200.00"));

        assertThat(result.getBalance()).isEqualByComparingTo("1200.00");
    }

    @Test
    void withdraw_shouldDecreaseBalanceAndCreateTransaction() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(accountRepo.save(any(Account.class))).thenReturn(alice);
        when(txnRepo.save(any(Transaction.class))).thenReturn(new Transaction());

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
    void transfer_shouldMoveMoneyBetweenAccounts() {
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(accountRepo.findById(2L)).thenReturn(Optional.of(bob));
        when(accountRepo.save(any(Account.class))).thenReturn(alice).thenReturn(bob);
        when(txnRepo.save(any(Transaction.class))).thenReturn(new Transaction());

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
        when(accountRepo.findById(1L)).thenReturn(Optional.of(alice));
        when(txnRepo.findByFromAccountIdOrToAccountIdOrderByTimestampDesc(1L, 1L))
                .thenReturn(List.of(new Transaction()));

        List<Transaction> result = service.getHistory(1L);

        assertThat(result).hasSize(1);
    }
}
```

- [ ] **Step 2: Run unit tests**

Run: `./mvnw test -Dtest=AtmServiceTest`
Expected: 10 tests pass, BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/example/atm/service/AtmServiceTest.java
git commit -m "test: add unit tests for AtmService"
```

---

### Task 8: Integration Test

**Files:**
- Create: `src/test/resources/application.properties`
- Create: `src/test/java/com/example/atm/AtmIntegrationTest.java`

**Interfaces:**
- Consumes: All source modules (Tasks 1-6)
- Produces: End-to-end integration test covering full flow

- [ ] **Step 1: Write test application.properties**

```properties
spring.datasource.url=jdbc:sqlite:atm-test.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=create-drop
```

- [ ] **Step 2: Write AtmIntegrationTest.java**

```java
package com.example.atm;

import com.example.atm.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AtmIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void fullFlow() {
        // 1. Create two accounts
        ApiResponse<?> aliceResp = rest.postForObject("/api/accounts",
                new CreateAccountRequest("Alice"), ApiResponse.class);
        ApiResponse<?> bobResp = rest.postForObject("/api/accounts",
                new CreateAccountRequest("Bob"), ApiResponse.class);

        assertThat(aliceResp.success()).isTrue();
        assertThat(bobResp.success()).isTrue();

        // 2. Deposit $1000 to Alice
        ResponseEntity<ApiResponse> depositResp = rest.postForEntity("/api/transactions/deposit",
                new DepositRequest(1L, new BigDecimal("1000.00")), ApiResponse.class);

        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositResp.getBody().success()).isTrue();

        // 3. Check Alice's balance via GET
        ApiResponse<?> balanceResp = rest.getForObject("/api/accounts/1", ApiResponse.class);
        assertThat(balanceResp.success()).isTrue();

        // 4. Withdraw $200 from Alice
        ResponseEntity<ApiResponse> withdrawResp = rest.postForEntity("/api/transactions/withdraw",
                new WithdrawRequest(1L, new BigDecimal("200.00")), ApiResponse.class);

        assertThat(withdrawResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawResp.getBody().success()).isTrue();

        // 5. Transfer $300 from Alice to Bob
        ResponseEntity<ApiResponse> transferResp = rest.postForEntity("/api/transactions/transfer",
                new TransferRequest(1L, 2L, new BigDecimal("300.00")), ApiResponse.class);

        assertThat(transferResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferResp.getBody().success()).isTrue();

        // 6. Check history — Alice should have 3 transactions
        ApiResponse<?> historyResp = rest.getForObject("/api/accounts/1/transactions", ApiResponse.class);
        assertThat(historyResp.success()).isTrue();

        // 7. Withdraw more than balance — should fail
        ResponseEntity<ApiResponse> failResp = rest.postForEntity("/api/transactions/withdraw",
                new WithdrawRequest(1L, new BigDecimal("10000.00")), ApiResponse.class);

        assertThat(failResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(failResp.getBody().success()).isFalse();
        assertThat(failResp.getBody().error()).contains("Insufficient balance");

        // 8. Transfer to self — should fail
        ResponseEntity<ApiResponse> selfResp = rest.postForEntity("/api/transactions/transfer",
                new TransferRequest(1L, 1L, new BigDecimal("50.00")), ApiResponse.class);

        assertThat(selfResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(selfResp.getBody().success()).isFalse();
        assertThat(selfResp.getBody().error()).contains("same account");

        // 9. Get non-existent account — should 404
        ResponseEntity<ApiResponse> notFoundResp = rest.getForEntity("/api/accounts/999", ApiResponse.class);

        assertThat(notFoundResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundResp.getBody().success()).isFalse();
        assertThat(notFoundResp.getBody().error()).contains("Account not found: 999");
    }

    @Test
    void validation_shouldRejectNegativeAmount() {
        ResponseEntity<ApiResponse> resp = rest.postForEntity("/api/transactions/deposit",
                new DepositRequest(1L, new BigDecimal("-50.00")), ApiResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 3: Run integration tests**

Run: `./mvnw test -Dtest=AtmIntegrationTest`
Expected: 2 tests pass, BUILD SUCCESS

- [ ] **Step 4: Run all tests together**

Run: `./mvnw test`
Expected: All 12 tests pass, BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/test/
git commit -m "test: add integration test for full ATM flow including self-transfer rejection"
```

---

### Task 9: Docker

**Files:**
- Create: `Dockerfile`

**Interfaces:**
- Consumes: Built jar from `./mvnw package`

- [ ] **Step 1: Write Dockerfile**

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/atm-*.jar app.jar
VOLUME /data
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

- [ ] **Step 2: Build jar**

Run: `./mvnw clean package -DskipTests`
Expected: BUILD SUCCESS, jar in `target/`

- [ ] **Step 3: Build Docker image**

Run: `docker build -t atm-simulator:latest .`
Expected: image built successfully

- [ ] **Step 4: Verify container starts**

Run: `docker run --rm -p 8080:8080 atm-simulator:latest` (stop after confirming startup logs)
Expected: Spring Boot starts on port 8080

- [ ] **Step 5: Commit**

```bash
git add Dockerfile
git commit -m "feat: add Dockerfile"
```

---

### Task 10: Final Verification

**Files:** none (verification only)

- [ ] **Step 1: Run full test suite**

Run: `./mvnw test`
Expected: All tests pass, BUILD SUCCESS

- [ ] **Step 2: Verify API manually (optional smoke test)**

Start the app: `java -jar target/atm-*.jar`

Then in another terminal:
```bash
# Create accounts
curl -s -X POST http://localhost:8080/api/accounts -H 'Content-Type: application/json' -d '{"name":"Alice"}' | jq
curl -s -X POST http://localhost:8080/api/accounts -H 'Content-Type: application/json' -d '{"name":"Bob"}' | jq

# Deposit
curl -s -X POST http://localhost:8080/api/transactions/deposit -H 'Content-Type: application/json' -d '{"accountId":1,"amount":500}' | jq

# Get balance
curl -s http://localhost:8080/api/accounts/1 | jq

# Withdraw
curl -s -X POST http://localhost:8080/api/transactions/withdraw -H 'Content-Type: application/json' -d '{"accountId":1,"amount":100}' | jq

# Transfer
curl -s -X POST http://localhost:8080/api/transactions/transfer -H 'Content-Type: application/json' -d '{"fromAccountId":1,"toAccountId":2,"amount":200}' | jq

# Check history
curl -s http://localhost:8080/api/accounts/1/transactions | jq

# Self-transfer (should fail)
curl -s -X POST http://localhost:8080/api/transactions/transfer -H 'Content-Type: application/json' -d '{"fromAccountId":1,"toAccountId":1,"amount":50}' | jq
```

- [ ] **Step 3: Run all tests one final time**

Run: `./mvnw test`
Expected: BUILD SUCCESS

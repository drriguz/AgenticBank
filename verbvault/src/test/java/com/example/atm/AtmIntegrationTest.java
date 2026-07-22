package com.example.atm;

import com.example.atm.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AtmIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate rest;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        rest.setErrorHandler(new NoOpResponseErrorHandler());
    }

    private String uri(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void fullFlow() {
        // 1. Create two accounts
        ApiResponse<?> aliceResp = rest.postForObject(uri("/api/accounts"),
                new CreateAccountRequest("1111111111111111", "Alice"), ApiResponse.class);
        ApiResponse<?> bobResp = rest.postForObject(uri("/api/accounts"),
                new CreateAccountRequest("2222222222222222", "Bob"), ApiResponse.class);

        assertThat(aliceResp.success()).isTrue();
        assertThat(bobResp.success()).isTrue();

        // 2. Deposit $1000 to Alice
        ResponseEntity<ApiResponse> depositResp = rest.postForEntity(uri("/api/transactions/deposit"),
                new DepositRequest("1111111111111111", new BigDecimal("1000.00")), ApiResponse.class);

        assertThat(depositResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositResp.getBody().success()).isTrue();

        // 3. Check Alice's balance via GET
        ApiResponse<?> balanceResp = rest.getForObject(uri("/api/accounts/1111111111111111"), ApiResponse.class);
        assertThat(balanceResp.success()).isTrue();

        // 4. Withdraw $200 from Alice
        ResponseEntity<ApiResponse> withdrawResp = rest.postForEntity(uri("/api/transactions/withdraw"),
                new DepositRequest("1111111111111111", new BigDecimal("200.00")), ApiResponse.class);

        assertThat(withdrawResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawResp.getBody().success()).isTrue();

        // 5. Transfer $300 from Alice to Bob
        ResponseEntity<ApiResponse> transferResp = rest.postForEntity(uri("/api/transactions/transfer"),
                new TransferRequest("1111111111111111", "2222222222222222", new BigDecimal("300.00")), ApiResponse.class);

        assertThat(transferResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferResp.getBody().success()).isTrue();

        // 6. Check history — Alice should have 3 transactions
        ApiResponse<?> historyResp = rest.getForObject(uri("/api/accounts/1111111111111111/transactions"), ApiResponse.class);
        assertThat(historyResp.success()).isTrue();

        // 7. Withdraw more than balance — should fail
        ResponseEntity<ApiResponse> failResp = rest.postForEntity(uri("/api/transactions/withdraw"),
                new DepositRequest("1111111111111111", new BigDecimal("10000.00")), ApiResponse.class);

        assertThat(failResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(failResp.getBody().success()).isFalse();
        assertThat(failResp.getBody().error()).contains("Insufficient balance");

        // 8. Transfer to self — should fail
        ResponseEntity<ApiResponse> selfResp = rest.postForEntity(uri("/api/transactions/transfer"),
                new TransferRequest("1111111111111111", "1111111111111111", new BigDecimal("50.00")), ApiResponse.class);

        assertThat(selfResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(selfResp.getBody().success()).isFalse();
        assertThat(selfResp.getBody().error()).contains("same account");

        // 9. Get non-existent account — should 404
        ResponseEntity<ApiResponse> notFoundResp = rest.getForEntity(uri("/api/accounts/0000000000000000"), ApiResponse.class);

        assertThat(notFoundResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(notFoundResp.getBody().success()).isFalse();
    }

    @Test
    void validation_shouldRejectNegativeAmount() {
        ResponseEntity<ApiResponse> resp = rest.postForEntity(uri("/api/transactions/deposit"),
                new DepositRequest("1111111111111111", new BigDecimal("-50.00")), ApiResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void withdraw_shouldRejectExceedingAtmCashLimit() {
        // Create and fund account
        rest.postForObject(uri("/api/accounts"),
                new CreateAccountRequest("9999999999999999", "Rich"), ApiResponse.class);
        rest.postForEntity(uri("/api/transactions/deposit"),
                new DepositRequest("9999999999999999", new BigDecimal("50000.00")), ApiResponse.class);

        // Try to withdraw more than ATM limit
        ResponseEntity<ApiResponse> resp = rest.postForEntity(uri("/api/transactions/withdraw"),
                new DepositRequest("9999999999999999", new BigDecimal("15000.00")), ApiResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().success()).isFalse();
        assertThat(resp.getBody().error()).contains("ATM cash limit exceeded");
    }
}

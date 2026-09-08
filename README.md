# Payment Wallet Processor

A Spring Boot 3.x application for processing payment wallet transactions with database-level concurrency control, ensuring idempotency and balance-safety under concurrent load.

## Technologies

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA with Pessimistic Locking
- H2 Database (in-memory with DB_CLOSE_DELAY=-1)
- HikariCP connection pool (20 connections)
- Maven

## Key Features

- **Idempotent Transaction Processing**: Duplicate transactionIds are safely handled via database unique constraints
- **Concurrent Balance Updates**: PESSIMISTIC_WRITE locks prevent race conditions on wallet balances
- **FAILED Transaction Persistence**: Failed transactions are committed (not rolled back) for audit trail
- **No In-JVM Locks**: All concurrency control is database-level (works across multiple instances)

## Project Structure

```
com.example.walletprocessor
├── controller       # REST controllers (Wallet, Transaction)
├── service          # Business logic (TransactionService, TransactionClaimService, WalletService)
├── repository       # Data access layer with pessimistic locking
├── entity           # JPA entities (Wallet, Transaction)
│   └── enums        # TransactionType (DEBIT/CREDIT), TransactionStatus (PENDING/COMPLETED/FAILED)
├── dto              # Data transfer objects
├── exception        # Custom exceptions with GlobalExceptionHandler
└── config           # Configuration classes
```

## Getting Started

### Prerequisites
- Java 17
- Maven 3.6+
- No external database required (uses in-memory H2)

### Import and Run Tests

**Option 1: IntelliJ IDEA (Recommended)**
1. Import project as Maven project: `File` → `Open` → Select `pom.xml`
2. Wait for Maven to download dependencies
3. Navigate to `src/test/java/com/example/walletprocessor/TransactionProcessingIntegrationTest.java`
4. Right-click → `Run 'TransactionProcessingIntegrationTest'`

**Option 2: Command Line**
```bash
# Run all tests
mvn test

# Run only integration tests
mvn test -Dtest=TransactionProcessingIntegrationTest
```

**No Postman or external tools needed** — the integration tests start an embedded Tomcat server and test all endpoints via `TestRestTemplate`.

## API Endpoints

### Create Wallet
```http
POST /api/v1/wallets
Content-Type: application/json

{
  "userId": "user-uuid-string",
  "initialBalance": 1000.00
}
```

### Process Transaction
```http
POST /api/v1/transactions/process
Content-Type: application/json

{
  "transactionId": "txn-uuid-string",
  "userId": "user-uuid-string",
  "amount": 100.00,
  "type": "DEBIT"  // or "CREDIT"
}
```

**Response Codes:**
- `200 OK`: Transaction processed successfully (or idempotent replay of COMPLETED transaction)
- `400 Bad Request`: Insufficient funds
- `404 Not Found`: Wallet not found
- `409 Conflict`: Duplicate transaction in progress (PENDING or FAILED status)

## Running the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## H2 Console (Development)

Access the H2 console at: `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: _(leave empty)_

## Integration Tests

Three comprehensive tests verify concurrent transaction processing:

1. **Single Valid Debit**: Basic transaction flow (wallet ₹1000 → debit ₹250 → balance ₹750)
2. **Idempotency Test**: 3 identical transactionIds fired simultaneously via CyclicBarrier
   - Verifies exactly 1 succeeds (HTTP 200) and 2 return conflict (HTTP 409)
   - Verifies balance deducted exactly once (₹900 final balance)
3. **Race Condition Test**: 10 concurrent ₹100 debits against ₹500 wallet
   - Verifies exactly 5 succeed and 5 fail with insufficient funds
   - Verifies final balance is exactly ₹0

**Stress tested**: All tests run 10 times consecutively with 100% consistency (no flakiness detected).

## Building the Project

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package as JAR
mvn clean package
```

## Design Decisions

See [DECISIONS.md](DECISIONS.md) for detailed explanation of:
- How database-level concurrency control is implemented
- Why `REQUIRES_NEW` + `saveAndFlush()` is used for idempotency claims
- Why `PESSIMISTIC_WRITE` locks are used for balance updates
- Why `noRollbackFor` ensures FAILED transactions persist
- Issues encountered during development and how they were resolved

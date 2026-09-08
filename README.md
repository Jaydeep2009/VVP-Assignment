# Payment Wallet Processor

A Spring Boot 3.x application for processing payment wallet transactions.

## Technologies

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database (in-memory)
- Maven

## Project Structure

```
com.example.walletprocessor
├── controller       # REST controllers
├── service          # Business logic
├── repository       # Data access layer
├── entity           # JPA entities
│   └── enums        # Entity enums
├── dto              # Data transfer objects
├── exception        # Custom exceptions
└── config           # Configuration classes
```

## Running the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## H2 Console

Access the H2 console at: `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: _(leave empty)_

## Building the Project

```bash
mvn clean install
```

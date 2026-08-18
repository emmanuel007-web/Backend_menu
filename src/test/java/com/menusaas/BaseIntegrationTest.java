package com.menusaas;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de integración: PostgreSQL real con Testcontainers + Flyway.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret",
                () -> "dGVzdC1zZWNyZXQtbWVudS1zYWFzLWp3dC1zZWNyZXQtbG9uZy1lbm91Z2gtMjAyNi0wOA==");
        registry.add("app.app-base-url", () -> "http://localhost:4200");
        registry.add("app.upload-dir", () -> "target/test-uploads");
    }
}
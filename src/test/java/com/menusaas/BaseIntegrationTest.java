package com.menusaas;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para tests de integración: un único PostgreSQL real con Testcontainers
 * compartido por todas las clases IT de la JVM. El contenedor se arranca una
 * sola vez en el arranque estático y NO se detiene por el ciclo de vida de JUnit
 * (se elimina con Ryuk al terminar la JVM), evitando que contextos cacheados de
 * Spring apunten a un contenedor ya detenido.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret",
                () -> "dGVzdC1zZWNyZXQtbWVudS1zYWFzLWp3dC1zZWNyZXQtbG9uZy1lbm91Z2gtMjAyNi0wOA==");
        registry.add("app.app-base-url", () -> "http://localhost:4200");
        registry.add("app.api-base-url", () -> "http://localhost:8080");
        registry.add("app.upload-dir", () -> "target/test-uploads");
        registry.add("app.security.cookies-secure", () -> "false");
    }
}
package com.menusaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MenuSaaSApplication {

    public static void main(String[] args) {
        normalizeDatabaseUrl();
        SpringApplication.run(MenuSaaSApplication.class, args);
    }

    static void normalizeDatabaseUrl() {
        String url = System.getenv("DB_URL");
        if (url != null && url.startsWith("postgresql://")) {
            String remainder = url.substring("postgresql://".length());
            int at = remainder.lastIndexOf('@');
            if (at >= 0) {
                remainder = remainder.substring(at + 1);
            }
            System.setProperty("spring.datasource.url", "jdbc:postgresql://" + remainder);
        }
    }
}
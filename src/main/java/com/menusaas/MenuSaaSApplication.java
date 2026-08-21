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
        if (url == null) {
            return;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            throw new IllegalStateException(
                    "DB_URL contiene la URL HTTP de la API de Supabase (" + url + "). Debes usar la cadena "
                            + "Postgres del panel: Connect -> Session pooler, ejemplo: "
                            + "postgresql://usuario:password@aws-0-xxx.pooler.supabase.com:5432/postgres");
        }
        String[] schemes = {"postgresql://", "postgres://"};
        for (String scheme : schemes) {
            if (url.startsWith(scheme)) {
                String remainder = url.substring(scheme.length());
                int at = remainder.lastIndexOf('@');
                if (at >= 0) {
                    remainder = remainder.substring(at + 1);
                }
                System.setProperty("spring.datasource.url", "jdbc:postgresql://" + remainder);
                return;
            }
        }
    }
}
package com.menusaas.config;

import com.menusaas.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF con token en cookie XSRF-TOKEN (lectible por Angular, que lo
                // reenvía en X-XSRF-TOKEN automáticamente). SameSite=Strict lo refuerza.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Handler clásico: el header X-XSRF-TOKEN debe ser el MÍSMO valor
                        // de la cookie XSRF-TOKEN (patrón Angular). El handler XOR de
                        // Spring Security 6.5 exige un token enmascarado que Angular no
                        // puede generar, por lo que no usamos XOR aquí.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // La estrategia por defecto de Spring Security 6.5 rota/borra la
                        // cookie XSRF en CADA petición autenticada (pensada para sesiones
                        // de servidor). Con JWT stateless eso rompe el flujo del SPA:
                        // el token CSRF debe permanecer estable entre peticiones.
                        .sessionAuthenticationStrategy((authentication, request, response) -> {
                        })
                        // Endpoints que se autentican con credenciales propias, pedidos públicos o firma
                        // criptográfica (webhook de ePayco).
                        .ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/public/orders/**", "/api/webhooks/**"))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Errores del contenedor (p. ej. rechazo CSRF) y preflight CORS
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // API pública: menús + archivos con URL firmada (expiración + HMAC)
                        .requestMatchers("/api/public/**").permitAll()
                        // Auth: login/register/refresh/logout se autentican con credenciales
                        // o con el propio token de la cookie (refresh/logout deben funcionar
                        // incluso con el access token expirado); csrf es el bootstrap del SPA.
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/csrf",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // Webhooks de pasarela de pagos (firma verificada por el proveedor)
                        .requestMatchers("/api/webhooks/**").permitAll()
                        // Swagger / Actuator
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // El resto requiere autenticación
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

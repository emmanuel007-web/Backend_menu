package com.menusaas.auth.security;

import com.menusaas.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * Emisión y limpieza de cookies HttpOnly para access/refresh tokens.
 * - HttpOnly: JavaScript NO puede leer los tokens (mitiga robo por XSS).
 * - SameSite=Strict: la cookie no viaja en peticiones cross-site (mitiga CSRF).
 * - Secure: solo se envía por HTTPS (obligatorio en el perfil prod).
 */
@Component
public class CookieService {

    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH_AUTH = "/api/auth";

    private final AppProperties appProperties;

    public CookieService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void setAccessToken(HttpServletResponse response, String token) {
        response.addCookie(cookie(ACCESS_COOKIE, token,
                appProperties.jwt().accessTokenTtlMinutes() * 60, "/"));
    }

    public void setRefreshToken(HttpServletResponse response, String token) {
        response.addCookie(cookie(REFRESH_COOKIE, token,
                appProperties.jwt().refreshTokenTtlDays() * 86400, COOKIE_PATH_AUTH));
    }

    /**
     * Elimina las cookies del navegador (logout).
     */
    public void clearTokens(HttpServletRequest request, HttpServletResponse response) {
        Cookie access = new Cookie(ACCESS_COOKIE, "");
        access.setMaxAge(0);
        access.setPath("/");
        applyCommonAttributes(access);

        Cookie refresh = new Cookie(REFRESH_COOKIE, "");
        refresh.setMaxAge(0);
        refresh.setPath(COOKIE_PATH_AUTH);
        applyCommonAttributes(refresh);

        response.addCookie(access);
        response.addCookie(refresh);
    }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, ACCESS_COOKIE);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Cookie cookie(String name, String value, int maxAge, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        cookie.setPath(path);
        applyCommonAttributes(cookie);
        return cookie;
    }

    private void applyCommonAttributes(Cookie cookie) {
        cookie.setHttpOnly(true);
        cookie.setSecure(appProperties.security().cookiesSecure());
        cookie.setAttribute("SameSite", "Strict");
    }
}

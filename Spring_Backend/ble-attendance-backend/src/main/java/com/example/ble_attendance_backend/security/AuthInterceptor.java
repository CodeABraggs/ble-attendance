package com.example.ble_attendance_backend.security;

import com.example.ble_attendance_backend.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String AUTH_USER_ATTRIBUTE = "authenticatedUser";
    public static final String AUTH_TOKEN_ATTRIBUTE = "authToken";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService tokenService;

    public AuthInterceptor(AuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() == BEARER_PREFIX.length()) {
            throw new UnauthorizedException("Login required");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        AuthenticatedUser user = tokenService.authenticate(token)
                .orElseThrow(() -> new UnauthorizedException("Session expired. Please log in again"));
        request.setAttribute(AUTH_USER_ATTRIBUTE, user);
        request.setAttribute(AUTH_TOKEN_ATTRIBUTE, token);
        return true;
    }
}

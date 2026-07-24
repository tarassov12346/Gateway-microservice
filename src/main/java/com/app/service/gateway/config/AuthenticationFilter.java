package com.app.service.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey signingKey;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("🔒 Криптографический ключ JWT успешно сгенерирован и закэширован в памяти шлюза");
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                token = request.getQueryParams().getFirst("token");
            }

            if (token == null) {
                log.warn("⚠️ Токен авторизации не найден. Маршрут: {}", request.getPath());
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = String.valueOf(claims.get("userId"));
                String username = claims.getSubject();

                // 🛰️ ЛОГИРОВАНИЕ НА ГЕЙТВЕЕ (Показывает прохождение данных через шлюз)
                log.info("🛰️ ГЕЙТВЕЙ ТРАССИРОВКА: Пользователь '{}' (ID: {}) успешно прошел аутентификацию. Маршрутизация на целевой путь: [{}] {}",
                        username, userId, request.getMethod(), request.getPath());

                // Мутируем запрос, добавляя заголовки безопасности X-Headers
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(request.mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Name", username)
                                .build())
                        .build();

                return chain.filter(mutatedExchange);

            } catch (Exception e) {
                log.warn("⚠️ Сбой валидации JWT токена для IP {}: {}", request.getRemoteAddress(), e.getMessage());
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().add("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        return response.setComplete();
    }

    public static class Config { }
}

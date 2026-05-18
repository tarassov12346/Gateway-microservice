package com.app.service.gateway.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.nio.charset.StandardCharsets;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    @Value("${jwt.secret}")
    private String secret;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Внутри метода apply:
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                // Если заголовка нет, берем из параметра "token"
                token = request.getQueryParams().getFirst("token");
            }

            if (token == null) {
                log.warn("❌ No token found in headers or query params");
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
// дальше твой код проверки JWT...


            try {
                // 2. Валидация токена
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = String.valueOf(claims.get("userId"));
                String username = claims.getSubject();

                log.debug("✅ Token validated for user: {} (ID: {})", username, userId);

                // 3. Мутация запроса (прокидываем заголовки в микросервисы)
                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(request.mutate()
                                .header("X-User-Id", userId)
                                .header("X-User-Name", username)
                                // Можно удалить Authorization, чтобы микросервисы его не парсили повторно
                                .build())
                        .build();

                return chain.filter(mutatedExchange);

            } catch (Exception e) {
                log.error("💥 JWT Validation failed: {}", e.getMessage());
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        // Можно добавить заголовок с причиной ошибки для фронтенда
        response.getHeaders().add("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        return response.setComplete();
    }

    public static class Config { }
}

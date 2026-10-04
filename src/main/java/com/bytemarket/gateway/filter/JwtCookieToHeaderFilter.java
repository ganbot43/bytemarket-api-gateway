package com.bytemarket.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtCookieToHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // El proxy de Nuxt ya manda el token de la sesión en Authorization.
        // Si lo pisáramos con la cookie, una cookie caducada invalidaría una
        // sesión buena: la cookie es solo el respaldo de quien llama directo.
        if (request.getHeaders().getFirst("Authorization") != null) {
            return chain.filter(exchange);
        }

        // Extraer la cookie jwt_token
        HttpCookie jwtCookie = request.getCookies().getFirst("jwt_token");

        if (jwtCookie != null && jwtCookie.getValue() != null && !jwtCookie.getValue().isEmpty()) {
            // Clonar la petición y agregar el Header Authorization: Bearer
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("Authorization", "Bearer " + jwtCookie.getValue())
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return chain.filter(mutatedExchange);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Ejecutar temprano, antes que el enrutamiento a los microservicios
    }
}

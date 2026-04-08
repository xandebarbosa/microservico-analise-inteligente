package com.coruja.client;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

@Provider
public class TokenPropagationFilter implements ClientRequestFilter {

    @Context
    HttpHeaders httpHeaders;

    @Override
    public void filter(ClientRequestContext requestContext) {
        // Captura o Token JWT da requisição original que chegou no Quarkus
        String authHeader = httpHeaders.getHeaderString(HttpHeaders.AUTHORIZATION);

        // Se houver um Token, injeta-o no cabeçalho da requisição de saída para o BFF
        if (authHeader != null && !requestContext.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            requestContext.getHeaders().add(HttpHeaders.AUTHORIZATION, authHeader);
        }
    }
}

package com.artagram.canvas.realtime;

import com.artagram.canvas.auth.JwtService;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Valida el JWT ANTES de subir a WebSocket (mismo token que REST). El
 * navegador no puede mandar headers propios en el handshake nativo de
 * WebSocket, asi que el token viaja como query param: /ws?token=...
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USUARIO_ID = "usuarioId";
    static final String ATTR_NOMBRE_ARTISTA = "nombreArtista";

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                    @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String token = extraerToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return jwtService.validar(token).map(principal -> {
            attributes.put(ATTR_USUARIO_ID, principal.usuarioId().toString());
            attributes.put(ATTR_NOMBRE_ARTISTA, principal.nombreArtista());
            return true;
        }).orElseGet(() -> {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        });
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                @NonNull WebSocketHandler wsHandler, Exception exception) {
        // nada que hacer
    }

    private String extraerToken(ServerHttpRequest request) {
        URI uri = request.getURI();
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String token = params.getFirst("token");
        if (token != null) {
            return token;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String header = servletRequest.getServletRequest().getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                return header.substring(7);
            }
        }
        return null;
    }
}

package com.artagram.canvas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.artagram.canvas.common.error.ApiException;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtServiceTest {

    private static final KeyPair PAR = generar();

    private static KeyPair generar() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem() {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(PAR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private JwtService conPem(String pem) {
        JwtProperties p = new JwtProperties();
        p.setPublicKeyPem(pem);
        p.setPublicKeyPath("");
        return new JwtService(p);
    }

    @Test
    void validaTokensConLosNombresDeClaimNuevosYViejos() {
        JwtService jwt = conPem(pem());
        UUID id = UUID.randomUUID();
        String nuevo = Jwts.builder().subject(id.toString()).claim("nombreArtista", "ana").claim("rolGlobal", "ADMIN")
                .claim("premium", true).signWith(PAR.getPrivate()).compact();
        var p = jwt.validar(nuevo).orElseThrow();
        assertThat(p.nombreArtista()).isEqualTo("ana");
        assertThat(p.rolGlobal()).isEqualTo("ADMIN");
        assertThat(p.premium()).isTrue();

        String viejo = Jwts.builder().subject(id.toString()).claim("nombre", "sam").claim("rol", "MOD")
                .signWith(PAR.getPrivate()).compact();
        assertThat(jwt.validar(viejo).orElseThrow().nombreArtista()).isEqualTo("sam");
        assertThat(jwt.validar(viejo).orElseThrow().rolGlobal()).isEqualTo("MOD");
        String vacio = Jwts.builder().subject(id.toString()).claim("nombreArtista", " ").claim("rolGlobal", "")
                .signWith(PAR.getPrivate()).compact();
        assertThat(jwt.validar(vacio).orElseThrow().rolGlobal()).isEqualTo("USUARIO");
        assertThat(jwt.validar("basura")).isEmpty();
    }

    @Test
    void cargaLaLlaveDesdeArchivoOQuedaSinLlave(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("pub.pem");
        Files.writeString(f, pem());
        JwtProperties p = new JwtProperties();
        p.setPublicKeyPath(f.toString());
        assertThat(p.getPublicKeyPem()).isEmpty();
        assertThat(new JwtService(p).validar("x")).isEmpty();

        p.setPublicKeyPath(dir.resolve("no-existe.pem").toString());
        assertThat(new JwtService(p).validar("x")).isEmpty();
        p.setPublicKeyPath(null);
        assertThat(new JwtService(p).validar("x")).isEmpty();
        assertThat(conPem("no-es-base64!!").validar("x")).isEmpty();
    }

    @Test
    void elResolverLeeElEncabezadoBearer() {
        JwtService jwt = mock(JwtService.class);
        var principal = new JwtService.TokenPrincipal(UUID.randomUUID(), "ana", "USUARIO", false);
        when(jwt.validar(anyString())).thenReturn(Optional.of(principal));
        JwtUserResolver r = new JwtUserResolver(jwt);
        MockHttpServletRequest sin = new MockHttpServletRequest();
        assertThat(r.resolverOpcional(sin)).isEmpty();
        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic x");
        assertThat(r.resolverOpcional(basic)).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> r.resolverRequerido(sin)).isInstanceOf(ApiException.class);
        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader("Authorization", "Bearer t");
        assertThat(r.resolverRequerido(ok)).isEqualTo(principal);
    }
}

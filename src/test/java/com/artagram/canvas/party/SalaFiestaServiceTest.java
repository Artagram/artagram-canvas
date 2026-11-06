package com.artagram.canvas.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.auth.JwtService.TokenPrincipal;
import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.party.dto.CrearSalaRequest;
import com.artagram.canvas.realtime.RealtimeBroadcaster;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SalaFiestaServiceTest {

    private final UUID usuarioId = UUID.randomUUID();
    private final CrearSalaRequest solicitud = new CrearSalaRequest(60, 8, 3);

    private SalaFiestaRepository salas;
    private ParticipanteSalaRepository participantes;
    private UsuarioProyeccionResolver usuarios;
    private SalaFiestaService service;

    @BeforeEach
    void setUp() {
        salas = mock(SalaFiestaRepository.class);
        participantes = mock(ParticipanteSalaRepository.class);
        usuarios = mock(UsuarioProyeccionResolver.class);
        service = new SalaFiestaService(salas, participantes, mock(PartyRondaEstadoRepository.class),
                mock(PartyDrawService.class), usuarios, mock(PartyRoundClock.class),
                mock(SeleccionadorPincelRonda.class), mock(RealtimeBroadcaster.class), new PartyProperties());
    }

    private TokenPrincipal principal(boolean premium) {
        return new TokenPrincipal(usuarioId, "lucia", "USUARIO", premium);
    }

    private void prepararCreacion() {
        when(usuarios.resolver(any(TokenPrincipal.class))).thenReturn(new UsuarioProyeccion(usuarioId, "lucia", false));
        when(salas.save(any(SalaFiesta.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void elPlanGratisNoPuedeAbrirUnaSegundaSala() {
        when(participantes.contarSalasActivasComoAnfitrion(usuarioId)).thenReturn(1L);

        assertThatThrownBy(() -> service.crear(principal(false), solicitud))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getCode()).isEqualTo("PLAN_LIMITE");
                });
        verify(salas, never()).save(any());
    }

    @Test
    void elPlanGratisPuedeAbrirSuPrimeraSala() {
        prepararCreacion();
        when(participantes.contarSalasActivasComoAnfitrion(usuarioId)).thenReturn(0L);

        assertThat(service.crear(principal(false), solicitud).estado()).isEqualTo("ESPERANDO");
    }

    @Test
    void elPlanProNoTieneTope() {
        prepararCreacion();

        assertThat(service.crear(principal(true), solicitud).estado()).isEqualTo("ESPERANDO");
        verify(participantes, never()).contarSalasActivasComoAnfitrion(any());
    }
}

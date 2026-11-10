package com.artagram.canvas.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.dto.CrearComunidadRequest;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionRepository;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComunidadServiceTest {

    @Test
    void elTopeDelPlanGratisResponde403ConCodigoPlanLimite() {
        UUID usuarioId = UUID.randomUUID();
        ComunidadRepository comunidades = mock(ComunidadRepository.class);
        when(comunidades.countByCreador_UsuarioId(usuarioId)).thenReturn(3L);
        ComunidadService service = new ComunidadService(comunidades, mock(MembresiaComunidadRepository.class),
                mock(UsuarioProyeccionRepository.class), mock(UsuarioProyeccionResolver.class), new ComunidadProperties());

        assertThatThrownBy(() -> service.crear(usuarioId, "lucia", false, new CrearComunidadRequest("Acuarelistas", "", true)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("PLAN_LIMITE"));
        verify(comunidades, never()).save(any());
    }

    @Test
    void lasRecomendadasCompletanConLasDeMasMiembrosSinRepetir() {
        UUID usuarioId = UUID.randomUUID();
        Comunidad cercana = comunidad("Cercana");
        Comunidad popular = comunidad("Popular");
        ComunidadRepository comunidades = mock(ComunidadRepository.class);
        UUID cercanaId = cercana.getId();
        UUID popularId = popular.getId();
        when(comunidades.buscarPorMiembrosCompartidos(eq(usuarioId), any())).thenReturn(List.of(cercanaId));
        when(comunidades.buscarConMasMiembros(eq(usuarioId), any())).thenReturn(List.of(cercanaId, popularId));
        when(comunidades.findById(cercanaId)).thenReturn(Optional.of(cercana));
        when(comunidades.findById(popularId)).thenReturn(Optional.of(popular));
        ComunidadService service = new ComunidadService(comunidades, mock(MembresiaComunidadRepository.class),
                mock(UsuarioProyeccionRepository.class), mock(UsuarioProyeccionResolver.class), new ComunidadProperties());

        assertThat(service.recomendadas(usuarioId, 5)).extracting("nombre").containsExactly("Cercana", "Popular");
    }

    private Comunidad comunidad(String nombre) {
        Comunidad c = mock(Comunidad.class);
        UsuarioProyeccion creador = mock(UsuarioProyeccion.class);
        UUID id = UUID.randomUUID();
        when(c.getId()).thenReturn(id);
        when(c.getNombre()).thenReturn(nombre);
        when(c.getCreador()).thenReturn(creador);
        return c;
    }
}

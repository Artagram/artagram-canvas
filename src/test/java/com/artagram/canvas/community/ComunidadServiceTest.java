package com.artagram.canvas.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.dto.CrearComunidadRequest;
import com.artagram.canvas.usuario.UsuarioProyeccionRepository;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
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
}

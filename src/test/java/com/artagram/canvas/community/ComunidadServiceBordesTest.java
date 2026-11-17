package com.artagram.canvas.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.dto.CrearComunidadRequest;
import com.artagram.canvas.realtime.StompPrincipal;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionRepository;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class ComunidadServiceBordesTest {

    private final UUID creadorId = UUID.randomUUID();
    private final UUID otroId = UUID.randomUUID();
    private final UUID comunidadId = UUID.randomUUID();
    private final UsuarioProyeccion creador = new UsuarioProyeccion(creadorId, "ana", false);
    private final UsuarioProyeccion otro = new UsuarioProyeccion(otroId, "sam", false);

    private ComunidadRepository comunidades;
    private MembresiaComunidadRepository membresias;
    private UsuarioProyeccionRepository usuariosRepo;
    private UsuarioProyeccionResolver resolver;
    private ComunidadService service;
    private Comunidad publica;

    @BeforeEach
    void setUp() {
        comunidades = mock(ComunidadRepository.class);
        membresias = mock(MembresiaComunidadRepository.class);
        usuariosRepo = mock(UsuarioProyeccionRepository.class);
        resolver = mock(UsuarioProyeccionResolver.class);
        service = new ComunidadService(comunidades, membresias, usuariosRepo, resolver, new ComunidadProperties());
        publica = new Comunidad("Acuarela", "desc", true, creador);
        ReflectionTestUtils.setField(publica, "id", comunidadId);
        when(comunidades.findById(comunidadId)).thenReturn(Optional.of(publica));
        when(membresias.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void rol(UUID usuario, RolComunidad rol) {
        when(membresias.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, usuario))
                .thenReturn(Optional.of(new MembresiaComunidad(publica, usuario.equals(creadorId) ? creador : otro, rol)));
    }

    @Test
    void crearValidaNombreYGuardaAlCreadorComoAdmin() {
        when(comunidades.existsByNombreIgnoreCase("Tomada")).thenReturn(true);
        assertThatThrownBy(() -> service.crear(creadorId, "ana", true, new CrearComunidadRequest("Tomada", null, true)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOMBRE_COMUNIDAD_TOMADO");

        when(resolver.resolver(creadorId, "ana", true)).thenReturn(creador);
        when(comunidades.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var r = service.crear(creadorId, "ana", true, new CrearComunidadRequest("Nueva", null, false));
        assertThat(r.miRol()).isEqualTo("ADMIN");
        assertThat(r.descripcion()).isEmpty();

        when(comunidades.save(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.crear(creadorId, "ana", true, new CrearComunidadRequest("Carrera", "x", true)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NOMBRE_COMUNIDAD_TOMADO");
    }

    @Test
    void unirseAPublicaCreaOReactivaYRechazaPrivadas() {
        when(resolver.resolver(otroId, "sam")).thenReturn(otro);
        when(membresias.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, otroId)).thenReturn(Optional.empty());
        assertThat(service.unirse(otroId, "sam", comunidadId).miRol()).isEqualTo("MIEMBRO");

        MembresiaComunidad previa = new MembresiaComunidad(publica, otro, RolComunidad.MODERADOR);
        previa.expulsar();
        when(membresias.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, otroId)).thenReturn(Optional.of(previa));
        assertThat(service.unirse(otroId, "sam", comunidadId).miRol()).isEqualTo("MODERADOR");
        assertThat(previa.isActivo()).isTrue();

        UUID privadaId = UUID.randomUUID();
        when(comunidades.findById(privadaId)).thenReturn(Optional.of(new Comunidad("P", "", false, creador)));
        assertThatThrownBy(() -> service.unirse(otroId, "sam", privadaId)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("COMUNIDAD_PRIVADA");
        assertThatThrownBy(() -> service.unirse(otroId, "sam", UUID.randomUUID())).isInstanceOf(ApiException.class);
    }

    @Test
    void cambiarRolSoloAdmin() {
        assertThatThrownBy(() -> service.cambiarRol(otroId, "USUARIO", comunidadId, creadorId, RolComunidad.MIEMBRO))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("NO_ERES_MIEMBRO");
        rol(otroId, RolComunidad.MIEMBRO);
        assertThatThrownBy(() -> service.cambiarRol(otroId, "USUARIO", comunidadId, creadorId, RolComunidad.MIEMBRO))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SOLO_ADMIN");
        rol(creadorId, RolComunidad.ADMIN);
        service.cambiarRol(creadorId, "USUARIO", comunidadId, otroId, RolComunidad.MODERADOR);
        service.cambiarRol(otroId, "ADMIN", comunidadId, otroId, RolComunidad.MODERADOR);
        UUID nadie = UUID.randomUUID();
        assertThatThrownBy(() -> service.cambiarRol(creadorId, "USUARIO", comunidadId, nadie, RolComunidad.MIEMBRO))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("MIEMBRO_NO_ENCONTRADO");
    }

    @Test
    void listarEInvitarMiembros() {
        assertThatThrownBy(() -> service.listarMiembros(otroId, "USUARIO", comunidadId)).isInstanceOf(ApiException.class);
        when(membresias.findByComunidad_IdAndActivoTrue(comunidadId))
                .thenReturn(List.of(new MembresiaComunidad(publica, creador, RolComunidad.ADMIN)));
        assertThat(service.listarMiembros(otroId, "ADMIN", comunidadId)).singleElement()
                .extracting("nombreArtista").isEqualTo("ana");
        when(membresias.existsByComunidad_IdAndUsuario_UsuarioIdAndActivoTrue(comunidadId, creadorId)).thenReturn(true);
        assertThat(service.listarMiembros(creadorId, "USUARIO", comunidadId)).hasSize(1);

        assertThatThrownBy(() -> service.invitarMiembro(otroId, "USUARIO", comunidadId, "x")).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("SIN_PERMISO_MODERACION");
        rol(otroId, RolComunidad.MIEMBRO);
        assertThatThrownBy(() -> service.invitarMiembro(otroId, "USUARIO", comunidadId, "x")).isInstanceOf(ApiException.class);

        rol(creadorId, RolComunidad.ADMIN);
        when(usuariosRepo.findByNombreArtistaIgnoreCase("sam")).thenReturn(Optional.of(otro));
        when(membresias.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, otroId)).thenReturn(Optional.empty());
        assertThat(service.invitarMiembro(creadorId, "USUARIO", comunidadId, " sam ").rol()).isEqualTo("MIEMBRO");
        MembresiaComunidad previa = new MembresiaComunidad(publica, otro, RolComunidad.MIEMBRO);
        previa.expulsar();
        when(membresias.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, otroId)).thenReturn(Optional.of(previa));
        service.invitarMiembro(creadorId, "USUARIO", comunidadId, "sam");
        assertThat(previa.isActivo()).isTrue();

        assertThatThrownBy(() -> service.invitarMiembro(creadorId, "USUARIO", comunidadId, "nadie"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("USUARIO_NO_ENCONTRADO");
        assertThatThrownBy(() -> service.invitarMiembro(creadorId, "ADMIN", UUID.randomUUID(), "sam"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("COMUNIDAD_NO_ENCONTRADA");
    }

    @Test
    void expulsarYEliminar() {
        assertThatThrownBy(() -> service.expulsar(otroId, "USUARIO", comunidadId, creadorId)).isInstanceOf(ApiException.class);
        rol(otroId, RolComunidad.MIEMBRO);
        assertThatThrownBy(() -> service.expulsar(otroId, "USUARIO", comunidadId, creadorId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.expulsar(otroId, "ADMIN", comunidadId, creadorId)).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("NO_EXPULSAR_CREADOR");
        assertThatThrownBy(() -> service.expulsar(otroId, "ADMIN", UUID.randomUUID(), creadorId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.expulsar(otroId, "ADMIN", comunidadId, UUID.randomUUID())).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("MIEMBRO_NO_ENCONTRADO");
        rol(creadorId, RolComunidad.ADMIN);
        MembresiaComunidad objetivo = new MembresiaComunidad(publica, otro, RolComunidad.MIEMBRO);
        when(membresias.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, otroId)).thenReturn(Optional.of(objetivo));
        service.expulsar(creadorId, "USUARIO", comunidadId, otroId);
        assertThat(objetivo.isActivo()).isFalse();
        assertThat(service.obtenerRol(otroId, comunidadId)).isEmpty();

        assertThatThrownBy(() -> service.eliminar(otroId, comunidadId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.eliminar(creadorId, UUID.randomUUID())).isInstanceOf(ApiException.class);
        service.eliminar(creadorId, comunidadId);
        verify(comunidades).delete(publica);
    }

    @Test
    void consultasYAutorizadorDeTopics() {
        when(membresias.findByUsuario_UsuarioIdAndActivoTrue(creadorId))
                .thenReturn(List.of(new MembresiaComunidad(publica, creador, RolComunidad.ADMIN)));
        assertThat(service.misComunidades(creadorId)).singleElement().extracting("miRol").isEqualTo("ADMIN");
        when(comunidades.buscarPorMiembrosCompartidos(any(), any())).thenReturn(List.of(comunidadId));
        assertThat(service.recomendadas(creadorId, 1)).hasSize(1);
        assertThat(service.ver(comunidadId, null).miRol()).isNull();
        rol(creadorId, RolComunidad.ADMIN);
        assertThat(service.ver(comunidadId, creadorId).miRol()).isEqualTo("ADMIN");
        assertThatThrownBy(() -> service.ver(UUID.randomUUID(), null)).isInstanceOf(ApiException.class);

        ComunidadTopicAuthorizer auth = new ComunidadTopicAuthorizer(service);
        String destino = "/topic/comunidad/" + comunidadId + "/chat";
        assertThat(auth.soporta(destino)).isTrue();
        assertThat(auth.soporta("/topic/otra")).isFalse();
        assertThat(auth.autorizado(new StompPrincipal(creadorId, "ana"), "/topic/otra")).isFalse();
        assertThat(auth.autorizado(new StompPrincipal(creadorId, "ana"), destino)).isFalse();
        when(membresias.existsByComunidad_IdAndUsuario_UsuarioIdAndActivoTrue(comunidadId, creadorId)).thenReturn(true);
        assertThat(auth.autorizado(new StompPrincipal(creadorId, "ana"), "/app/lienzo/" + comunidadId)).isTrue();

        InternalCommunityController interno = new InternalCommunityController(comunidades, service);
        assertThat(interno.verificarMembresia(UUID.randomUUID(), creadorId).getStatusCode().value()).isEqualTo(404);
        assertThat(interno.verificarMembresia(comunidadId, creadorId).getBody()).containsEntry("rol", "ADMIN");
        assertThat(interno.verificarMembresia(comunidadId, otroId).getBody()).containsEntry("rol", "NINGUNO");

        ComunidadProperties p = new ComunidadProperties();
        p.setFreeMaxCommunities(7);
        assertThat(p.getFreeMaxCommunities()).isEqualTo(7);
        assertThat(publica.getDescripcion()).isEqualTo("desc");
    }
}

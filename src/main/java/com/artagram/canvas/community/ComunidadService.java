package com.artagram.canvas.community;

import com.artagram.canvas.common.error.ApiException;
import com.artagram.canvas.community.dto.ComunidadResponse;
import com.artagram.canvas.community.dto.CrearComunidadRequest;
import com.artagram.canvas.community.dto.MiembroComunidadDto;
import com.artagram.canvas.usuario.UsuarioProyeccion;
import com.artagram.canvas.usuario.UsuarioProyeccionRepository;
import com.artagram.canvas.usuario.UsuarioProyeccionResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A diferencia del monolito, la cuota de "comunidades gratis" no se valida
 * contra un {@code QuotaService} de artagram-users: la tabla {@code uso_cuenta}
 * que ya existe ahí tiene un propósito distinto (almacenamiento, no conteo de
 * comunidades/salas). En vez de tocar ese esquema ajeno, el límite se calcula
 * localmente contando las comunidades que ya creó el usuario — ver
 * {@code ComunidadRepository.countByCreador_UsuarioId}.
 */
@Service
public class ComunidadService {

    private static final Logger log = LoggerFactory.getLogger(ComunidadService.class);

    private final ComunidadRepository comunidadRepository;
    private final MembresiaComunidadRepository membresiaRepository;
    private final UsuarioProyeccionRepository usuarioRepository;
    private final UsuarioProyeccionResolver usuarioResolver;
    private final ComunidadProperties properties;

    public ComunidadService(ComunidadRepository comunidadRepository,
                             MembresiaComunidadRepository membresiaRepository,
                             UsuarioProyeccionRepository usuarioRepository,
                             UsuarioProyeccionResolver usuarioResolver,
                             ComunidadProperties properties) {
        this.comunidadRepository = comunidadRepository;
        this.membresiaRepository = membresiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioResolver = usuarioResolver;
        this.properties = properties;
    }

    @Transactional
    public ComunidadResponse crear(UUID usuarioId, String nombreUsuario, boolean premium, CrearComunidadRequest request) {
        if (comunidadRepository.existsByNombreIgnoreCase(request.nombre())) {
            throw ApiException.conflict("NOMBRE_COMUNIDAD_TOMADO", "Ese nombre de comunidad ya esta tomado");
        }
        if (!premium && comunidadRepository.countByCreador_UsuarioId(usuarioId) >= properties.getFreeMaxCommunities()) {
            throw ApiException.forbidden("TOPE_COMUNIDADES",
                    "Llegaste al limite de " + properties.getFreeMaxCommunities()
                            + " comunidades del plan gratis. Pasate a premium para crear comunidades ilimitadas");
        }

        UsuarioProyeccion creador = usuarioResolver.resolver(usuarioId, nombreUsuario, premium);
        Comunidad comunidad;
        try {
            comunidad = comunidadRepository.save(new Comunidad(
                    request.nombre(), request.descripcion() == null ? "" : request.descripcion(),
                    request.publica(), creador));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("NOMBRE_COMUNIDAD_TOMADO", "Ese nombre de comunidad ya esta tomado");
        }
        membresiaRepository.save(new MembresiaComunidad(comunidad, creador, RolComunidad.ADMIN));
        log.info("Comunidad creada: id={} nombre='{}' creador={}", comunidad.getId(), comunidad.getNombre(), usuarioId);

        return aRespuesta(comunidad, RolComunidad.ADMIN);
    }

    @Transactional
    public ComunidadResponse unirse(UUID usuarioId, String nombreUsuario, UUID comunidadId) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> ApiException.notFound("COMUNIDAD_NO_ENCONTRADA", "Esa comunidad no existe"));
        if (!comunidad.isPublica()) {
            throw ApiException.forbidden("COMUNIDAD_PRIVADA", "Esta comunidad es privada, necesitas invitacion");
        }
        MembresiaComunidad membresia = membresiaRepository.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, usuarioId)
                .map(existing -> {
                    existing.reactivar();
                    return existing;
                })
                .orElseGet(() -> {
                    UsuarioProyeccion usuario = usuarioResolver.resolver(usuarioId, nombreUsuario);
                    return membresiaRepository.save(new MembresiaComunidad(comunidad, usuario, RolComunidad.MIEMBRO));
                });
        log.info("Usuario {} se unió a la comunidad {} con rol {}", usuarioId, comunidadId, membresia.getRol());

        return aRespuesta(comunidad, membresia.getRol());
    }

    @Transactional
    public void cambiarRol(UUID solicitanteId, String rolGlobalSolicitante, UUID comunidadId, UUID usuarioObjetivoId, RolComunidad nuevoRol) {
        RolComunidad rolSolicitante = obtenerRol(solicitanteId, comunidadId)
                .orElseThrow(() -> ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad"));
        if (rolSolicitante != RolComunidad.ADMIN && !"ADMIN".equals(rolGlobalSolicitante)) {
            throw ApiException.forbidden("SOLO_ADMIN", "Solo un administrador puede cambiar roles");
        }
        MembresiaComunidad membresia = membresiaRepository.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, usuarioObjetivoId)
                .orElseThrow(() -> ApiException.notFound("MIEMBRO_NO_ENCONTRADO", "Ese usuario no es miembro de la comunidad"));
        membresia.setRol(nuevoRol);
        log.info("Rol cambiado en comunidad {}: usuario={} nuevoRol={} (por {})", comunidadId, usuarioObjetivoId, nuevoRol, solicitanteId);
    }

    @Transactional(readOnly = true)
    public List<MiembroComunidadDto> listarMiembros(UUID solicitanteId, String rolGlobalSolicitante, UUID comunidadId) {
        if (!esMiembroActivo(solicitanteId, comunidadId) && !"ADMIN".equals(rolGlobalSolicitante)) {
            throw ApiException.forbidden("NO_ERES_MIEMBRO", "No perteneces a esta comunidad");
        }
        return membresiaRepository.findByComunidad_IdAndActivoTrue(comunidadId).stream()
                .map(m -> new MiembroComunidadDto(
                        m.getUsuario().getUsuarioId(),
                        m.getUsuario().getNombreArtista(),
                        m.getRol().name(),
                        m.getCreadoEn()
                ))
                .toList();
    }

    @Transactional
    public MiembroComunidadDto invitarMiembro(UUID solicitanteId, String rolGlobalSolicitante, UUID comunidadId, String nombreArtista) {
        RolComunidad rolSolicitante = obtenerRol(solicitanteId, comunidadId).orElse(null);
        if (!"ADMIN".equals(rolGlobalSolicitante) && (rolSolicitante == null || !rolSolicitante.puedeModerar())) {
            throw ApiException.forbidden("SIN_PERMISO_MODERACION", "Solo un moderador o administrador puede invitar miembros");
        }
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> ApiException.notFound("COMUNIDAD_NO_ENCONTRADA", "Esa comunidad no existe"));
        UsuarioProyeccion invitado = usuarioRepository.findByNombreArtistaIgnoreCase(nombreArtista.trim())
                .orElseThrow(() -> ApiException.notFound("USUARIO_NO_ENCONTRADO", "No existe un usuario con nombre de artista @" + nombreArtista));

        MembresiaComunidad membresia = membresiaRepository.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, invitado.getUsuarioId())
                .map(m -> {
                    m.reactivar();
                    return m;
                })
                .orElseGet(() -> membresiaRepository.save(new MembresiaComunidad(comunidad, invitado, RolComunidad.MIEMBRO)));

        log.info("Usuario {} invitado/añadido a comunidad {} por {}", invitado.getUsuarioId(), comunidadId, solicitanteId);
        return new MiembroComunidadDto(
                invitado.getUsuarioId(),
                invitado.getNombreArtista(),
                membresia.getRol().name(),
                membresia.getCreadoEn()
        );
    }

    @Transactional
    public void expulsar(UUID solicitanteId, String rolGlobalSolicitante, UUID comunidadId, UUID usuarioObjetivoId) {
        RolComunidad rolSolicitante = obtenerRol(solicitanteId, comunidadId).orElse(null);
        if (!"ADMIN".equals(rolGlobalSolicitante) && (rolSolicitante == null || !rolSolicitante.puedeModerar())) {
            throw ApiException.forbidden("SIN_PERMISO_MODERACION", "No tienes permisos de moderacion en esta comunidad");
        }
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> ApiException.notFound("COMUNIDAD_NO_ENCONTRADA", "Esa comunidad no existe"));
        if (comunidad.getCreador().getUsuarioId().equals(usuarioObjetivoId)) {
            throw ApiException.forbidden("NO_EXPULSAR_CREADOR", "No se puede expulsar al creador de la comunidad");
        }
        MembresiaComunidad membresia = membresiaRepository.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, usuarioObjetivoId)
                .orElseThrow(() -> ApiException.notFound("MIEMBRO_NO_ENCONTRADO", "Ese usuario no es miembro de la comunidad"));
        membresia.expulsar();
        log.info("Usuario {} expulsado de la comunidad {} (por {})", usuarioObjetivoId, comunidadId, solicitanteId);
    }

    @Transactional
    public void eliminar(UUID usuarioId, UUID comunidadId) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> ApiException.notFound("COMUNIDAD_NO_ENCONTRADA", "Esa comunidad no existe"));
        if (!comunidad.getCreador().getUsuarioId().equals(usuarioId)) {
            throw ApiException.forbidden("SOLO_CREADOR", "Solo quien creo la comunidad puede eliminarla");
        }
        comunidadRepository.delete(comunidad);
        log.info("Comunidad eliminada: id={} nombre='{}' por={}", comunidadId, comunidad.getNombre(), usuarioId);
    }

    @Transactional(readOnly = true)
    public Optional<RolComunidad> obtenerRol(UUID usuarioId, UUID comunidadId) {
        return membresiaRepository.findByComunidad_IdAndUsuario_UsuarioId(comunidadId, usuarioId)
                .filter(MembresiaComunidad::isActivo)
                .map(MembresiaComunidad::getRol);
    }

    @Transactional(readOnly = true)
    public boolean esMiembroActivo(UUID usuarioId, UUID comunidadId) {
        return membresiaRepository.existsByComunidad_IdAndUsuario_UsuarioIdAndActivoTrue(comunidadId, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<ComunidadResponse> misComunidades(UUID usuarioId) {
        return membresiaRepository.findByUsuario_UsuarioIdAndActivoTrue(usuarioId).stream()
                .map(m -> aRespuesta(m.getComunidad(), m.getRol()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ComunidadResponse ver(UUID comunidadId, UUID usuarioId) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> ApiException.notFound("COMUNIDAD_NO_ENCONTRADA", "Esa comunidad no existe"));
        RolComunidad rol = usuarioId == null ? null : obtenerRol(usuarioId, comunidadId).orElse(null);
        return aRespuesta(comunidad, rol);
    }

    private ComunidadResponse aRespuesta(Comunidad comunidad, RolComunidad miRol) {
        long totalMiembros = membresiaRepository.countByComunidad_IdAndActivoTrue(comunidad.getId());
        return new ComunidadResponse(
                comunidad.getId(),
                comunidad.getNombre(),
                comunidad.getDescripcion(),
                comunidad.isPublica(),
                comunidad.getCreador().getNombreArtista(),
                totalMiembros,
                miRol == null ? null : miRol.name()
        );
    }
}

/**
 * Modo fiesta: salas con rondas sincronizadas, dibujo colaborativo en tiempo
 * real y chat de sala vía WebSocket, scoped a los participantes activos de
 * cada {@code salaId}.
 * <p>
 * Migrado desde {@code ARTAGRAM-backv1/src/main/java/com/artagram/platform/party}
 * y probado end-to-end contra la infra real (login → crear/unirse a sala →
 * chat por STOMP, incluyendo el rechazo de un usuario no participante). Única
 * diferencia funcional respecto al original: no se reserva/libera cupo de
 * "salas de fiesta activas" contra artagram-users — ver {@code SalaFiestaService}
 * para el detalle de por qué.
 * <p>
 * Decisión de arquitectura (chat de fiesta vs. chat normal):
 * <ul>
 *   <li>El chat personal/comunidad NO vive aquí — sigue siendo responsabilidad
 *       de {@code artagram-chat} vía REST + SSE, sin WebSocket propio (así quedó
 *       decidido para esos dos: no necesitan latencia de tiempo real).</li>
 *   <li>El chat DENTRO de una sala de fiesta sí es síncrono, porque la sala ya
 *       es una sesión en vivo (rondas con timer, dibujo colaborativo). Va en este
 *       microservicio, reutilizando el mismo canal STOMP que el dibujo.</li>
 *   <li>Implementación: un {@code PartyChatStompController} con
 *       {@code @MessageMapping("/fiesta/{salaId}/chat/enviar")} que reenvía con
 *       {@code RealtimeBroadcaster.enviar} a {@code /topic/fiesta/{salaId}/chat}.
 *       {@code PartyTopicAuthorizer} ya autoriza cualquier destino bajo
 *       {@code /app|/topic/fiesta/{salaId}/...} verificando participación activa
 *       en la sala — no hace falta tocar nada de autorización.</li>
 *   <li>Persistencia: por defecto NO se persiste (las salas son efímeras y se
 *       cierran/exportan al final). Si se quiere historial, el mismo patrón que
 *       usa el chat de comunidad hoy (publicar un evento a RabbitMQ para que
 *       artagram-chat lo guarde) se puede reutilizar sin cambios de diseño.</li>
 * </ul>
 */
package com.artagram.canvas.party;

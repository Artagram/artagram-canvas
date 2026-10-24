/**
 * Puente en vivo (STOMP) para el chat de comunidad y el chat personal,
 * migrado de ARTAGRAM-backv1.
 * <p>
 * Decision de diseño: artagram-chat ya es dueño exclusivo de la persistencia
 * de ambos (su propio MongoDB, su propio REST en {@code /api/comunidades/{id}/chat}
 * y {@code /api/mensajes/**}). Este paquete NO guarda ninguna copia local de
 * mensajes — solo transmite en vivo por {@code /ws} (que artagram-canvas ya
 * posee) y publica un evento en {@code artagram.events} para que
 * artagram-chat persista de forma asincrona:
 * <ul>
 *   <li>Chat de comunidad: routing key {@code room.message} — contrato que
 *       YA existia (lo publicaba {@code LienzoEventBridge} en el monolito;
 *       artagram-chat ya lo consume desde antes de esta migracion, cero
 *       cambios necesarios ahi).</li>
 *   <li>Chat personal: routing key {@code personal.message.sent} — nuevo,
 *       no existia ningun camino de chat personal por STOMP antes de hoy
 *       (solo REST). Requiere el listener nuevo agregado en artagram-chat
 *       ({@code ChatEventListener.onPersonalMessage} +
 *       {@code ChatPersonalService.guardarDesdeStomp}).</li>
 * </ul>
 * {@link com.artagram.canvas.community.ComunidadTopicAuthorizer} ya protege
 * la suscripcion a {@code /topic/comunidad/{id}/chat} (se extendio su patron
 * para cubrir {@code comunidad/} ademas de {@code lienzo/}); el chat
 * personal no necesita autorizador propio porque viaja por destinos
 * {@code /user/**} (colas por-usuario), que {@code StompAuthChannelInterceptor}
 * ya exime del chequeo de {@code DestinationAuthorizer} por diseño.
 */
package com.artagram.canvas.chat;

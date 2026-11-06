package com.artagram.canvas.kpi;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.artagram.canvas.realtime.StompPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

class KpiSessionListenerTest {

    private final KpiEventPublisher publisher = mock(KpiEventPublisher.class);
    private final KpiSessionListener listener = new KpiSessionListener(publisher);
    private final StompPrincipal usuario = new StompPrincipal(UUID.randomUUID(), "lucia");
    private final UUID comunidadId = UUID.randomUUID();

    private Message<byte[]> mensaje(StompCommand comando, String suscripcion, String destino) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(comando);
        headers.setSessionId("s1");
        headers.setSubscriptionId(suscripcion);
        headers.setDestination(destino);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private void suscribir(String suscripcion, String destino) {
        listener.alSuscribirse(new SessionSubscribeEvent(this, mensaje(StompCommand.SUBSCRIBE, suscripcion, destino), usuario));
    }

    @Test
    void entrarAlLienzoInformaLaComunidad() {
        suscribir("sub-1", "/topic/lienzo/" + comunidadId);

        verify(publisher).publicarSesionIniciada(usuario.usuarioId(), "s1:sub-1", "LIENZO", comunidadId);
    }

    @Test
    void lasSuscripcionesSecundariasNoCuentan() {
        suscribir("sub-1", "/topic/lienzo/" + comunidadId + "/bloqueos");

        verifyNoInteractions(publisher);
    }

    @Test
    void salirDelLienzoSinCerrarElSocketTerminaLaActividad() {
        suscribir("sub-1", "/topic/lienzo/" + comunidadId);

        listener.alDesuscribirse(new SessionUnsubscribeEvent(this, mensaje(StompCommand.UNSUBSCRIBE, "sub-1", null), usuario));

        verify(publisher).publicarSesionTerminada(eq(usuario.usuarioId()), eq("s1:sub-1"), anyLong());
    }

    @Test
    void desconectarseTerminaTodoYNoDuplica() {
        suscribir("sub-1", "/topic/lienzo/" + comunidadId);
        suscribir("sub-2", "/topic/fiesta/" + UUID.randomUUID());
        listener.alDesuscribirse(new SessionUnsubscribeEvent(this, mensaje(StompCommand.UNSUBSCRIBE, "sub-1", null), usuario));

        var desconexion = new SessionDisconnectEvent(this, mensaje(StompCommand.DISCONNECT, null, null), "s1", CloseStatus.NORMAL, usuario);
        listener.alDesconectar(desconexion);
        listener.alDesconectar(desconexion);

        verify(publisher, times(1)).publicarSesionTerminada(eq(usuario.usuarioId()), eq("s1:sub-2"), anyLong());
        verify(publisher, times(1)).publicarSesionTerminada(eq(usuario.usuarioId()), eq("s1:sub-1"), anyLong());
    }

    @Test
    void cancelarOtraSuscripcionNoTerminaNada() {
        suscribir("sub-1", "/topic/lienzo/" + comunidadId);

        listener.alDesuscribirse(new SessionUnsubscribeEvent(this, mensaje(StompCommand.UNSUBSCRIBE, "otra", null), usuario));

        verify(publisher, never()).publicarSesionTerminada(eq(usuario.usuarioId()), eq("s1:sub-1"), anyLong());
    }
}

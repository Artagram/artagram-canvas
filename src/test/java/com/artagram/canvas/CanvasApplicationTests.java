package com.artagram.canvas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CanvasApplicationTests {

    @Test
    void laClasePrincipalEsUnaAplicacionSpringBoot() {
        // El @SpringBootTest completo necesita Postgres, Redis y RabbitMQ; aquí
        // solo se valida que la clase de arranque esté bien declarada.
        assertTrue(CanvasApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }
}

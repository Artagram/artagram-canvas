package com.artagram.canvas;

import com.artagram.canvas.auth.JwtProperties;
import com.artagram.canvas.canvas.CanvasProperties;
import com.artagram.canvas.common.config.CorsProperties;
import com.artagram.canvas.community.ComunidadProperties;
import com.artagram.canvas.party.PartyProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, PartyProperties.class,
        CanvasProperties.class, ComunidadProperties.class})
@EnableScheduling
public class CanvasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CanvasApplication.class, args);
    }
}

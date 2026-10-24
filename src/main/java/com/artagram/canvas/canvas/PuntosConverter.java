package com.artagram.canvas.canvas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

@Converter
public class PuntosConverter implements AttributeConverter<List<Punto>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Punto>> TIPO = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Punto> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron serializar los puntos del trazo", e);
        }
    }

    @Override
    public List<Punto> convertToEntityAttribute(String dbData) {
        try {
            return MAPPER.readValue(dbData, TIPO);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron leer los puntos del trazo", e);
        }
    }
}

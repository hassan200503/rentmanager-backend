package com.rentmanager.shared.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Every {@code BigDecimal} field on this API — money, and the handful of
 * percentage/rate fields that are also BigDecimal — is serialized as a JSON
 * string, never a raw number. A raw JSON number becomes an IEEE-754 double
 * in every JS/TS consumer, which is exactly the kind of precision loss a
 * monetary amount can't tolerate (see the frontend's own CLAUDE.md defect
 * list, which was already written expecting this backend change).
 *
 * Global, not per-field: annotating each response DTO individually
 * (@JsonFormat(shape = STRING) on every BigDecimal field) is exactly the
 * kind of thing that's trivial to forget on a newly-added field — a single
 * serializer registered for the whole type can't be missed. Uses
 * toPlainString(), not toString(), so a value that would otherwise render
 * in scientific notation (very large or very small BigDecimals) still comes
 * out as an ordinary decimal string.
 *
 * Deserialization is untouched: Jackson's default BigDecimal deserializer
 * already accepts both a JSON string and a JSON number on the way in, so
 * request DTOs need no corresponding change — only response serialization
 * was ever the problem.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsStringCustomizer() {
        return builder -> builder.serializerByType(BigDecimal.class, new PlainStringBigDecimalSerializer());
    }

    private static final class PlainStringBigDecimalSerializer extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.toPlainString());
            }
        }
    }
}

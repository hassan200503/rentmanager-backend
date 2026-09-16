package com.rentmanager.shared.config;

import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Makes the published OpenAPI contract tell the truth about money.
 *
 * {@link JacksonConfig} serialises every {@code BigDecimal} as a JSON string,
 * but springdoc describes {@code BigDecimal} as {@code type: number} by
 * default. Any client generated from {@code /v3/api-docs} — the mobile app
 * generates its types this way — was therefore told a string was a number,
 * which is exactly the lie that let {@code totalPaid + totalDue} type-check
 * and concatenate on the web client (TD-102).
 *
 * Requests are unaffected at runtime: Jackson's BigDecimal deserialiser
 * accepts both a string and a number, so describing request fields as a
 * decimal string is accurate as well.
 */
@Configuration
public class OpenApiMoneySchemaConfig {

    static {
        SpringDocUtils.getConfig().replaceWithSchema(
                BigDecimal.class,
                new StringSchema().format("decimal").example("15000.00"));
    }
}

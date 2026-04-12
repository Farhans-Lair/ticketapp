package com.ticketapp.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Jackson for Spring MVC's HTTP message converter.
 *
 * Uses Jackson2ObjectMapperBuilderCustomizer so the configuration applies to
 * the ObjectMapper that Spring MVC uses for HTTP response serialization.
 *
 * SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS is intentionally NOT enabled:
 * It calls getId() on every Hibernate proxy, which can chain into security objects
 * (via Hibernate6Module's proxy detection) and trigger StackOverflowError through
 * AbstractAuthenticationToken.getName() -> getPrincipal().toString() -> getName()...
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            Hibernate6Module hibernateModule = new Hibernate6Module();
            hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
            // DO NOT enable SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS

            builder.modulesToInstall(hibernateModule, new JavaTimeModule());
            builder.featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                SerializationFeature.FAIL_ON_EMPTY_BEANS
            );
        };
    }
}

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
 * Uses Jackson2ObjectMapperBuilderCustomizer (NOT @Primary ObjectMapper)
 * so the configuration applies to the same ObjectMapper that Spring MVC uses
 * for serializing HTTP responses.
 *
 * Key decisions:
 *  - Hibernate6Module with FORCE_LAZY_LOADING disabled: skip uninitialized
 *    lazy proxies rather than trying to load them (avoids LazyInitializationException)
 *  - SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS intentionally NOT enabled:
 *    this feature aggressively calls getId() on all proxies and can trigger
 *    unexpected serialization of security objects leading to StackOverflowError
 *  - JavaTimeModule: serialize LocalDateTime as ISO-8601 strings, not timestamps
 *  - FAIL_ON_EMPTY_BEANS disabled: don't crash on empty/proxy objects
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // Hibernate 6 proxy handling - skip unloaded lazy associations
            Hibernate6Module hibernateModule = new Hibernate6Module();
            hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
            // DO NOT enable SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS
            // It calls getId() on every proxy which can chain into security objects
            // and trigger StackOverflowError via AbstractAuthenticationToken.getName()

            builder.modulesToInstall(hibernateModule, new JavaTimeModule());
            builder.featuresToDisable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                SerializationFeature.FAIL_ON_EMPTY_BEANS
            );
        };
    }
}

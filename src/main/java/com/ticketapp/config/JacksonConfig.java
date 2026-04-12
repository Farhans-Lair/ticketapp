package com.ticketapp.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Hibernate6Module with Spring MVC's Jackson HTTP message converter.
 *
 * IMPORTANT — WHY Jackson2ObjectMapperBuilderCustomizer INSTEAD OF @Primary ObjectMapper:
 *
 * Spring Boot auto-configures Jackson via JacksonAutoConfiguration, which uses
 * Jackson2ObjectMapperBuilder to create the ObjectMapper that Spring MVC uses for
 * HTTP response serialization (inside MappingJackson2HttpMessageConverter).
 *
 * If we define our own @Primary ObjectMapper bean (as done previously), Spring MVC
 * does NOT automatically use it for HTTP responses — it still uses the one created
 * by its own Jackson2ObjectMapperBuilder. Our @Primary bean ends up being used only
 * for non-MVC injection points (e.g., ObjectMapper injected directly into controllers).
 * This is why the Hibernate6Module had no effect on HTTP responses.
 *
 * Jackson2ObjectMapperBuilderCustomizer is the correct hook: it customizes the SAME
 * builder that Spring MVC uses, so modules registered here ARE applied to the HTTP
 * message converter's ObjectMapper.
 *
 * The Hibernate6Module tells Jackson how to handle Hibernate 6 proxy objects:
 *   - Skip uninitialized lazy proxies (don't trigger loading)
 *   - Serialize them as null instead of throwing LazyInitializationException
 * Without this, any @ManyToOne(fetch=LAZY) field causes Jackson to crash mid-stream
 * when the Hibernate session is closed (spring.jpa.open-in-view=false), producing
 * ERR_INCOMPLETE_CHUNKED_ENCODING with a 200 status in the browser.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateModuleCustomizer() {
        return builder -> {
            Hibernate6Module hibernateModule = new Hibernate6Module();
            // Do NOT force lazy loading — skip proxies instead of triggering them
            hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
            // Return the entity ID for unloaded lazy associations instead of null
            hibernateModule.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
            builder.modulesToInstall(hibernateModule);
        };
    }
}

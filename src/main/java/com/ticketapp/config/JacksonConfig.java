package com.ticketapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Registers the Hibernate6Module with Jackson.
 *
 * WHY THIS IS NECESSARY:
 * Spring Boot 3.x uses Hibernate 6. When Jackson serialises an @Entity that has
 * a @ManyToOne(fetch = LAZY) field, it encounters a Hibernate proxy object.
 * Without this module, Jackson tries to serialise the proxy directly, which:
 *   1. Triggers LazyInitializationException if the Hibernate session is closed
 *      (spring.jpa.open-in-view=false), OR
 *   2. Serialises Hibernate internal proxy metadata instead of the real data.
 * Either way, the exception is thrown AFTER the HTTP 200 header and partial
 * response body are already written to the socket, so Spring cannot send a
 * proper error response — the stream is simply cut off, causing the browser to
 * report ERR_INCOMPLETE_CHUNKED_ENCODING.
 *
 * Hibernate6Module teaches Jackson to recognise Hibernate proxies and either:
 *   - Skip uninitialized proxies (FORCE_LAZY_LOADING = false, the default), or
 *   - Serialize them as null.
 * Combined with @JsonIgnore on all lazy fields, this makes serialisation safe.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        Hibernate6Module hibernateModule = new Hibernate6Module();
        // Do NOT force lazy loading — skip uninitialized proxies instead of triggering them.
        hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        // Serialize uninitialized proxies as null rather than throwing.
        hibernateModule.enable(Hibernate6Module.Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(hibernateModule);
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
}

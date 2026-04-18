package com.ticketapp.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * When USE_HTTPS=true (local dev with mkcert):
 *   - Spring Boot's Tomcat serves HTTPS on server.port (default 8443)
 *   - This bean spins up a secondary HTTP connector on HTTP_PORT (default 8080)
 *     that issues a 301 redirect to the HTTPS port.
 *
 * When USE_HTTPS=false (AWS behind ALB):
 *   - This bean is skipped entirely (ConditionalOnProperty).
 *   - Tomcat serves plain HTTP; the ALB handles TLS termination.
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class HttpsConfig implements WebMvcConfigurer {

    /** The HTTPS port (set in application.properties as server.port) */
    @Value("${server.port:8443}")
    private int httpsPort;

    /** The HTTP port that will redirect to HTTPS */
    @Value("${server.http.port:8080}")
    private int httpPort;

    /**
     * Registers a secondary HTTP Connector.
     * All plain-HTTP requests received on httpPort are redirected to
     * https://host:httpsPort by Tomcat's built-in redirect mechanism.
     */
    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(org.apache.catalina.Context context) {
                // Force Tomcat to redirect HTTP → HTTPS
                org.apache.tomcat.util.descriptor.web.SecurityConstraint constraint =
                        new org.apache.tomcat.util.descriptor.web.SecurityConstraint();
                constraint.setUserConstraint("CONFIDENTIAL");
                org.apache.tomcat.util.descriptor.web.SecurityCollection collection =
                        new org.apache.tomcat.util.descriptor.web.SecurityCollection();
                collection.addPattern("/*");
                constraint.addCollection(collection);
                context.addConstraint(constraint);
            }
        };
        factory.addAdditionalTomcatConnectors(httpConnector());
        return factory;
    }

    private Connector httpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        return connector;
    }
}

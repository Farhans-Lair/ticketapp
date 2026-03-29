package com.ticketapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the frontend HTML files at the same URL paths Express did.
 * Static assets (JS/CSS) are served from /static/js and /static/css
 * which map to src/main/resources/static/js and /css.
 *
 * Drop all frontend HTML files into: src/main/resources/static/
 * Drop JS files into:  src/main/resources/static/js/
 * Drop CSS files into: src/main/resources/static/css/
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // ── Public pages ───────────────────────────────────────────────────
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/events-page").setViewName("forward:/events.html");

        // ── User pages ─────────────────────────────────────────────────────
        registry.addViewController("/my-bookings").setViewName("forward:/my-bookings.html");
        registry.addViewController("/payment").setViewName("forward:/payment.html");
        registry.addViewController("/seat-selection").setViewName("forward:/seat-selection.html");

        // ── Organizer pages ────────────────────────────────────────────────
        registry.addViewController("/organizer-register").setViewName("forward:/organizer-register.html");
        registry.addViewController("/organizer-dashboard").setViewName("forward:/organizer-dashboard.html");
        registry.addViewController("/organizer-events").setViewName("forward:/organizer-events.html");
        registry.addViewController("/organizer-revenue").setViewName("forward:/organizer-revenue.html");

        // ── Admin pages ────────────────────────────────────────────────────
        registry.addViewController("/admin").setViewName("forward:/admin-dashboard.html");
        registry.addViewController("/admin/revenue").setViewName("forward:/admin-revenue.html");
        registry.addViewController("/admin/organizers").setViewName("forward:/admin-organizers.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // /js/** → src/main/resources/static/js/
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");

        // /css/** → src/main/resources/static/css/
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
    }
}

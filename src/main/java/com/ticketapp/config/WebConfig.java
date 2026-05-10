package com.ticketapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/events-page").setViewName("forward:/events.html");
        registry.addViewController("/my-bookings").setViewName("forward:/my-bookings.html");
        registry.addViewController("/my-profile").setViewName("forward:/my-profile.html");   // Feature 10
        registry.addViewController("/payment").setViewName("forward:/payment.html");
        registry.addViewController("/seat-selection").setViewName("forward:/seat-selection.html");
        registry.addViewController("/organizer-register").setViewName("forward:/organizer-register.html");
        registry.addViewController("/organizer-dashboard").setViewName("forward:/organizer-dashboard.html");
        registry.addViewController("/organizer-events").setViewName("forward:/organizer-events.html");
        registry.addViewController("/organizer-revenue").setViewName("forward:/organizer-revenue.html");
        registry.addViewController("/admin").setViewName("forward:/admin-dashboard.html");
        registry.addViewController("/admin/revenue").setViewName("forward:/admin-revenue.html");
        registry.addViewController("/admin/organizers").setViewName("forward:/admin-organizers.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}

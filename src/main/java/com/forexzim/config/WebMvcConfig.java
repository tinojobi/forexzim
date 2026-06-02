package com.forexzim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${zimrate.uploads.dir:./uploads/}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + (uploadsDir.endsWith("/") ? uploadsDir : uploadsDir + "/");
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}

package com.pavti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Pavti restaurant billing API.
 * Layers (same as Health 360): controller → bal → dal → MySQL stored procedures.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PavtiApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PavtiApiApplication.class, args);
    }
}

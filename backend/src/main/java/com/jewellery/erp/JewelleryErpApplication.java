package com.jewellery.erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Jewellery Shop ERP backend.
 *
 * <p>The application is a modular monolith: each business capability lives in
 * its own package under {@code com.jewellery.erp} and communicates with other
 * modules only through their service interfaces and DTOs.
 */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@ConfigurationPropertiesScan
public class JewelleryErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(JewelleryErpApplication.class, args);
    }
}

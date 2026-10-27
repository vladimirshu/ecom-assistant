package com.ecomassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EcomAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(EcomAssistantApplication.class, args);
    }

}

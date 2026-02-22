package com.aiplatform.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IncidentProducerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IncidentProducerApplication.class, args);
    }
}

package com.liteworkflow.infra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InfraServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InfraServiceApplication.class, args);
    }
}

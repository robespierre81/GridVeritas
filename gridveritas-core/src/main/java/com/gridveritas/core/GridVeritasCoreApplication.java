package com.gridveritas.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GridVeritasCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GridVeritasCoreApplication.class, args);
    }
}

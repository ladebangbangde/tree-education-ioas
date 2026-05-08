package com.treeeducation.ioas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tree Education IOAS backend application. */
@SpringBootApplication
@EnableScheduling
public class IoasApplication {
    public static void main(String[] args) {
        SpringApplication.run(IoasApplication.class, args);
    }
}

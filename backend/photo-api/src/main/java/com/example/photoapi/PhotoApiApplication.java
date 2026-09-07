package com.example.photoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PhotoApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoApiApplication.class, args);
    }
}


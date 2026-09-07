package com.example.photoconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PhotoConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoConsumerApplication.class, args);
    }
}


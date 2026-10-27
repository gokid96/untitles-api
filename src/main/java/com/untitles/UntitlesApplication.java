package com.untitles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
public class UntitlesApplication {
    public static void main(String[] args) {
        SpringApplication.run(UntitlesApplication.class, args);
    }

}

package uk.ac.dundee.ga.mms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Mentor Management System API (V1: Spring Boot, Heroku, JawsDB MySQL). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MmsApplication.class, args);
    }
}

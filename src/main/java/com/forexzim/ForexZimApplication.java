package com.forexzim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class ForexZimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForexZimApplication.class, args);
    }

}
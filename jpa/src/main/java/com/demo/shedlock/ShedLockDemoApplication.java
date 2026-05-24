package com.demo.shedlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShedLockDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShedLockDemoApplication.class, args);
    }
}

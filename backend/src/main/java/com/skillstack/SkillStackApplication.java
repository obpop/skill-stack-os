package com.skillstack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillStackApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillStackApplication.class, args);
    }
}

package com.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockAgentHarnessApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockAgentHarnessApplication.class, args);
    }
}
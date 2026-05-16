package com.hub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@MapperScan("com.hub.mapper")
@SpringBootApplication
public class CampusLostHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusLostHubApplication.class, args);
    }
}

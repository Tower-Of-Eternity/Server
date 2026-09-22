package com.towerofeternity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
        log.info("====================================================");
        log.info("  TOWER OF ETERNITY - GAME SERVER STARTED ON PORT 8080");
        log.info("====================================================");
    }
}

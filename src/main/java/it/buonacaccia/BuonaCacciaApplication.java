package it.buonacaccia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BuonaCacciaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuonaCacciaApplication.class, args);
    }
}

package com.munte.KickOffBet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;



@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan
public class KickOffBetApplication {

	public static void main(String[] args) {
		SpringApplication.run(KickOffBetApplication.class, args);
	}

}

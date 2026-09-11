package com.shoplocker.fssai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.shoplocker.fssai"})
@EnableScheduling
public class DukaanLockerApplication {

	public static void main(String[] args) {
		SpringApplication.run(DukaanLockerApplication.class, args);
	}




}




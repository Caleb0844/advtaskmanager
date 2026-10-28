package com.caleb.advtaskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdvtaskmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AdvtaskmanagerApplication.class, args);
	}

}

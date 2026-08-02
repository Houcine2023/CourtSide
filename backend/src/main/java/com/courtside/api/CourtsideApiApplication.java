package com.courtside.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates @Scheduled methods (RefreshTokenCleanupJob).
// Without it the annotation is inert — the job would never run and nothing would warn you.
@EnableScheduling
@SpringBootApplication
public class CourtsideApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CourtsideApiApplication.class, args);
	}

}

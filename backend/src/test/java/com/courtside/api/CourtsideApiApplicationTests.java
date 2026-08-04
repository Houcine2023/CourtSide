package com.courtside.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The smoke test: does the whole application context start?
 *
 * It catches an entire family of mistakes no unit test can see — a missing bean, a
 * circular dependency, a broken @Value placeholder, an invalid JPA mapping, a Flyway
 * migration that will not apply. When this fails, nothing else matters.
 *
 * It runs on the `integration` profile so it uses the throwaway test database rather
 * than the development one.
 */
@SpringBootTest
@ActiveProfiles("integration")
@DisplayName("Application context")
class CourtsideApiApplicationTests {

	@Test
	@DisplayName("starts with every bean wired and both migrations applied")
	void contextLoads() {
	}

}

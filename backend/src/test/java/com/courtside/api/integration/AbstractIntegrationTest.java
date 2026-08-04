package com.courtside.api.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for tests that need a REAL PostgreSQL: exclusion constraints, tstzrange,
 * window functions and generate_series exist in no in-memory database, so H2 would
 * mean testing a different application than the one we ship.
 *
 * WHERE THE DATABASE COMES FROM
 * The environment provides it, and the tests only need a URL:
 *   - locally  : `docker compose up -d`, then the `courtside_test` database on the
 *                same server (a SEPARATE database, so a test that truncates tables
 *                can never wipe your development data);
 *   - in CI    : GitHub Actions `services:` containers, reachable on localhost.
 *
 * We deliberately do NOT use Testcontainers here: its bundled docker-java client
 * negotiates a Docker API version that Engine 29 rejects (`/v1.32/info` -> 400), so on
 * this machine it cannot start a container at all. Switching back is a change to this
 * one file — no test below knows how the database is provisioned.
 *
 * Connection details live in `src/test/resources/application-integration.yml` and can
 * be overridden with the usual Spring environment variables in CI.
 */
@SpringBootTest
@ActiveProfiles("integration")
public abstract class AbstractIntegrationTest {
}

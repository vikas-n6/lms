package com.lms.testkit.containers;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base for integration tests needing PostgreSQL. Uses a single reused container (started on first
 * load) so a test run shares one database engine. Requires Docker; executed under Failsafe in CI
 * and in a local Docker session (deferred where Docker is absent; docs/backlog.md).
 */
public abstract class PostgresIT {

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("lms")
                    .withUsername("lms")
                    .withPassword("lms");

    static {
        POSTGRES.start();
    }

    protected static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    protected static String username() {
        return POSTGRES.getUsername();
    }

    protected static String password() {
        return POSTGRES.getPassword();
    }
}

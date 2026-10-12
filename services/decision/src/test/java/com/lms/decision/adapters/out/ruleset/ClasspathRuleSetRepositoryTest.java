package com.lms.decision.adapters.out.ruleset;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ClasspathRuleSetRepositoryTest {

    private final ClasspathRuleSetRepository repo =
            new ClasspathRuleSetRepository(new ObjectMapper(), "v1");

    @Test
    void loadsTheSeededV1RuleSetFromTheClasspath() {
        assertThat(repo.findByVersion("v1")).isPresent();
        assertThat(repo.current().version()).isEqualTo("v1");
        assertThat(repo.current().rules()).hasSize(8);
    }

    @Test
    void unknownVersionIsEmpty() {
        assertThat(repo.findByVersion("v999")).isEmpty();
    }
}

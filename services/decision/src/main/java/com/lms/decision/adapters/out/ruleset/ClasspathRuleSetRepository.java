package com.lms.decision.adapters.out.ruleset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.decision.application.port.out.RuleSetRepository;
import com.lms.decision.domain.RuleSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Serves seeded rule sets from {@code classpath:rule-sets/{version}.json}. The JDBC + Redis-cached
 * repository arrives in the Docker session; this one keeps dev and tests self-contained and is the
 * source the seed migration will load into {@code rule_sets}.
 */
@Component
public class ClasspathRuleSetRepository implements RuleSetRepository {

    private final RuleSetJsonLoader loader;
    private final String currentVersion;

    public ClasspathRuleSetRepository(
            ObjectMapper mapper,
            @Value("${lms.decision.current-rule-set:v1}") String currentVersion) {
        this.loader = new RuleSetJsonLoader(mapper);
        this.currentVersion = currentVersion;
    }

    @Override
    public Optional<RuleSet> findByVersion(String version) {
        String resource = "rule-sets/" + version + ".json";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(loader.load(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read rule set " + version, e);
        }
    }

    @Override
    public String currentVersion() {
        return currentVersion;
    }
}

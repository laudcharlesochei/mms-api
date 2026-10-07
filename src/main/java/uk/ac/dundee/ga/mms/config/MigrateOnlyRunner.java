package uk.ac.dundee.ga.mms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Heroku release phase: Flyway has already migrated during start-up, so exit cleanly. */
@Slf4j
@Component
@Order(Integer.MIN_VALUE)
@ConditionalOnProperty(name = "mms.migrate-only", havingValue = "true")
public class MigrateOnlyRunner implements ApplicationRunner {

    private final ApplicationContext context;

    public MigrateOnlyRunner(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Database migrations applied - exiting (release phase).");
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}

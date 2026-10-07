package uk.ac.dundee.ga.mms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uk.ac.dundee.ga.mms.storage.UserStore;

import java.util.Arrays;

/**
 * Prints one summary line after start-up so `heroku logs` shows at a glance whether the API is
 * configured correctly (profile, database host, CORS origin, number of users). Never logs secrets.
 */
@Slf4j
@Component
@Order(100)
public class StartupReport implements ApplicationRunner {

    private final Environment env;
    private final MmsProperties props;
    private final UserStore users;

    public StartupReport(Environment env, MmsProperties props, UserStore users) {
        this.env = env;
        this.props = props;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isMigrateOnly()) {
            return;
        }
        String url = env.getProperty("spring.datasource.url", "(default)");
        String db = url.replaceAll("//[^@/]*@", "//").replaceAll("\\?.*$", "");
        long userCount;
        try {
            userCount = users.count();
        } catch (RuntimeException e) {
            userCount = -1;
        }
        log.info("MMS API ready | profiles={} | auth={} | database={} | CORS origin={} | users={} | encryption key {}",
                Arrays.toString(env.getActiveProfiles()), props.getAuth().getMode(), db, props.getCorsAllowedOrigin(),
                userCount, props.getNotesEncryptionKey() == null || props.getNotesEncryptionKey().isBlank() ? "NOT SET (dev key)" : "set");
        if (userCount == 0) {
            log.warn("No users in the database: set ADMIN_EMAIL and ADMIN_PASSWORD and restart (heroku restart).");
        }
        if (props.getCorsAllowedOrigin() != null && props.getCorsAllowedOrigin().contains("localhost")
                && Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            log.warn("CORS_ALLOWED_ORIGIN still points at localhost - set it to the mms-web URL or the browser will be blocked.");
        }
    }
}

package uk.ac.dundee.ga.mms.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code mms.*} configuration (all values come from environment variables). */
@ConfigurationProperties(prefix = "mms")
@Getter @Setter
public class MmsProperties {

    private Auth auth = new Auth();
    private String corsAllowedOrigin = "http://localhost:5173";
    private String frontendUrl = "http://localhost:5173";
    private String notesEncryptionKey;
    private int editWindowDays = 14;
    private String ipHashSalt = "mms";
    private Mail mail = new Mail();
    private Retention retention = new Retention();
    private Reminders reminders = new Reminders();
    private Features features = new Features();
    private Bootstrap bootstrap = new Bootstrap();
    private boolean seedDemoData;
    private boolean migrateOnly;
    private RateLimit rateLimit = new RateLimit();

    public boolean isEntra() {
        return "entra".equalsIgnoreCase(auth.getMode());
    }

    @Getter @Setter
    public static class Auth {
        private String mode = "local";
        private String jwtSecret;
        private String issuer = "mms-api";
        private int accessTokenMinutes = 15;
        private int refreshTokenMinutes = 30;
        private int maxFailedLogins = 5;
        private int lockoutMinutes = 15;
        private boolean requireMfa;
        private Entra entra = new Entra();
    }

    @Getter @Setter
    public static class Entra {
        private String tenantId;
        private String clientId;
        private String audience;
        private String apiScope;
        private boolean autoProvision;
    }

    @Getter @Setter
    public static class Mail {
        private boolean enabled;
        private String from = "no-reply@mms.local";
        private boolean sendToAos = true;
        private boolean sendToStudent;
        private boolean attachPdf = true;
    }

    @Getter @Setter
    public static class Retention {
        private boolean enabled;
        private int years = 6;
        private String cron = "0 30 2 1 * *";
    }

    @Getter @Setter
    public static class Reminders {
        private boolean enabled;
        private String cron = "0 0 9 * * MON";
        private String zone = "Europe/London";
        private int dueWindowDays = 21;
    }

    @Getter @Setter
    public static class Features {
        private boolean messaging = true;
    }

    @Getter @Setter
    public static class Bootstrap {
        private String adminEmail;
        private String adminPassword;
        private String adminName = "MMS Administrator";
        /** ADMIN_RESET=true: on start-up, reset ADMIN_EMAIL's password to ADMIN_PASSWORD, unlock and reactivate it. */
        private boolean adminReset;
    }

    @Getter @Setter
    public static class RateLimit {
        private int loginPerMinute = 10;
        private int writesPerMinute = 120;
    }
}

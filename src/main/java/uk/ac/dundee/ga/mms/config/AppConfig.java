package uk.ac.dundee.ga.mms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;
import uk.ac.dundee.ga.mms.util.NotesCipher;

import java.time.Clock;

@Configuration
@EnableAsync
@EnableScheduling
public class AppConfig {

    private final MmsProperties props;

    public AppConfig(MmsProperties props) {
        this.props = props;
    }

    @PostConstruct
    void initCipher() {
        NotesCipher.init(props.getNotesEncryptionKey());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OpenAPI mmsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Mentor Management System API").version("v1")
                        .description("GA mentor meeting records, dashboard and administration (V1: JawsDB MySQL)."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}

package uk.ac.dundee.ga.mms.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns the {@code JAWSDB_URL} config var set by the JawsDB add-on
 * ({@code mysql://user:pass@host:3306/db}) into Spring datasource properties, with TLS required.
 */
public class JawsDbEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String url = env.getProperty("JAWSDB_URL");
        if (url == null || url.isBlank()) {
            url = env.getProperty("JAWSDB_MARIA_URL");
        }
        if (url == null || url.isBlank()) {
            return;
        }
        Map<String, Object> props = parse(url, env.getProperty("JAWSDB_SSL_MODE", "REQUIRED"));
        env.getPropertySources().addFirst(new MapPropertySource("jawsdb", props));
    }

    static Map<String, Object> parse(String jawsUrl, String sslMode) {
        URI uri = URI.create(jawsUrl.trim());
        String userInfo = uri.getRawUserInfo();
        String user = "";
        String pass = "";
        if (userInfo != null) {
            int i = userInfo.indexOf(':');
            user = URLDecoder.decode(i >= 0 ? userInfo.substring(0, i) : userInfo, StandardCharsets.UTF_8);
            pass = i >= 0 ? URLDecoder.decode(userInfo.substring(i + 1), StandardCharsets.UTF_8) : "";
        }
        int port = uri.getPort() > 0 ? uri.getPort() : 3306;
        String jdbc = "jdbc:mysql://" + uri.getHost() + ":" + port + uri.getPath()
                + "?sslMode=" + sslMode + "&serverTimezone=UTC&characterEncoding=UTF-8";
        Map<String, Object> p = new HashMap<>();
        p.put("spring.datasource.url", jdbc);
        p.put("spring.datasource.username", user);
        p.put("spring.datasource.password", pass);
        p.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        return p;
    }
}

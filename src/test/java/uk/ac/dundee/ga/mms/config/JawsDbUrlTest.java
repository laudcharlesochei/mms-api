package uk.ac.dundee.ga.mms.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JawsDbUrlTest {

    @Test
    void parsesJawsDbUrl() {
        Map<String, Object> p = JawsDbEnvironmentPostProcessor.parse("mysql://user1:p%40ss@host.example.com:3306/dbname", "REQUIRED");
        assertThat(p.get("spring.datasource.url")).isEqualTo(
                "jdbc:mysql://host.example.com:3306/dbname?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8");
        assertThat(p.get("spring.datasource.username")).isEqualTo("user1");
        assertThat(p.get("spring.datasource.password")).isEqualTo("p@ss");
    }
}

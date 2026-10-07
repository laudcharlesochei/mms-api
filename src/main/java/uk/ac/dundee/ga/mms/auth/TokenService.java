package uk.ac.dundee.ga.mms.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.stereotype.Service;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/** Issues and verifies the API's own HS256 tokens for local accounts (AUTH_MODE=local). */
@Slf4j
@Service
public class TokenService {

    private final MmsProperties props;
    private final SecretKey key;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder refreshDecoder;

    public TokenService(MmsProperties props) {
        this.props = props;
        this.key = buildKey(props.getAuth().getJwtSecret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.refreshDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.refreshDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getAuth().getIssuer()));
    }

    private static SecretKey buildKey(String secret) {
        try {
            byte[] material;
            if (secret == null || secret.isBlank()) {
                log.warn("JWT_SECRET is not set - generating a random key; sessions will not survive a restart.");
                material = new byte[32];
                new SecureRandom().nextBytes(material);
            } else {
                material = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            }
            return new SecretKeySpec(material, "HmacSHA256");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public SecretKey key() {
        return key;
    }

    public TokenPair issue(AppUser user) {
        Instant now = Instant.now();
        Instant accessExp = now.plus(props.getAuth().getAccessTokenMinutes(), ChronoUnit.MINUTES);
        Instant refreshExp = now.plus(props.getAuth().getRefreshTokenMinutes(), ChronoUnit.MINUTES);
        String access = encode(JwtClaimsSet.builder()
                .issuer(props.getAuth().getIssuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now).expiresAt(accessExp)
                .id(UUID.randomUUID().toString())
                .claim("typ", "access")
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .build());
        String refresh = encode(JwtClaimsSet.builder()
                .issuer(props.getAuth().getIssuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now).expiresAt(refreshExp)
                .id(UUID.randomUUID().toString())
                .claim("typ", "refresh")
                .claim("pwd", passwordFingerprint(user))
                .build());
        return new TokenPair(access, refresh, accessExp, refreshExp);
    }

    /** A short MFA-pending token used between password and TOTP steps. */
    public String issueMfaChallenge(AppUser user) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .issuer(props.getAuth().getIssuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now).expiresAt(now.plus(5, ChronoUnit.MINUTES))
                .claim("typ", "mfa")
                .build());
    }

    public Jwt decode(String token, String expectedType) {
        try {
            Jwt jwt = refreshDecoder.decode(token);
            if (!expectedType.equals(jwt.getClaimAsString("typ"))) {
                throw new JwtException("Wrong token type");
            }
            return jwt;
        } catch (JwtException e) {
            throw e;
        }
    }

    /** Changes when the password changes, so refresh tokens die on password reset. */
    public static String passwordFingerprint(AppUser user) {
        String h = user.getPasswordHash() == null ? "none" : user.getPasswordHash();
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(h.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public JwtDecoder accessDecoder() {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.getAuth().getIssuer()));
        return d;
    }

    public record TokenPair(String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
    }
}

package uk.ac.dundee.ga.mms.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.storage.UserStore;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns a validated JWT (locally issued or Entra ID) into an authentication whose name is the
 * app_user id and whose authorities are ROLE_* from the database (plus Entra app roles, if any).
 * Deactivated users are rejected on every request (FR-03).
 */
@Component
public class MmsJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserStore users;
    private final MmsProperties props;

    public MmsJwtAuthenticationConverter(UserStore users, MmsProperties props) {
        this.users = users;
        this.props = props;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = props.isEntra() ? resolveEntraUser(jwt) : resolveLocalUser(jwt);
        if (user == null || !user.isActive()) {
            throw new InvalidBearerTokenException("User is not registered in MMS or has been deactivated");
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        roles.addAll(user.getRoles());
        if (props.isEntra()) {
            roles.addAll(EntraRoles.fromClaim(jwt.getClaimAsStringList("roles")));
        }
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        return new JwtAuthenticationToken(jwt, authorities, String.valueOf(user.getId()));
    }

    private AppUser resolveLocalUser(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("typ"))) {
            return null;
        }
        try {
            return users.findById(Long.valueOf(jwt.getSubject())).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private AppUser resolveEntraUser(Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        Optional<AppUser> byOid = users.findByEntraOid(oid);
        if (byOid.isPresent()) {
            return byOid.get();
        }
        String email = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getClaimAsString("email"),
                jwt.getClaimAsString("upn"));
        Optional<AppUser> byEmail = users.findByEmail(email);
        if (byEmail.isPresent()) {
            AppUser u = byEmail.get();
            if (u.getEntraOid() == null && oid != null) {
                u.setEntraOid(oid); // link the Entra identity on first sign-in
                return users.save(u);
            }
            return u;
        }
        if (props.getAuth().getEntra().isAutoProvision() && email != null) {
            Set<Role> claimRoles = EntraRoles.fromClaim(jwt.getClaimAsStringList("roles"));
            if (!claimRoles.isEmpty()) {
                AppUser u = new AppUser();
                u.setEmail(email.toLowerCase());
                u.setDisplayName(firstNonBlank(jwt.getClaimAsString("name"), email));
                u.setEntraOid(oid);
                u.setRoles(EnumSet.copyOf(claimRoles));
                u.setCreatedAt(Instant.now());
                return users.save(u);
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

package uk.ac.dundee.ga.mms.auth;

import uk.ac.dundee.ga.mms.domain.Role;

import java.util.Arrays;
import java.util.Set;

/** The signed-in user as seen by the service layer. */
public record CurrentUser(Long id, String email, String displayName, Set<Role> roles) {

    public boolean has(Role r) {
        return roles.contains(r);
    }

    public boolean hasAny(Role... rs) {
        return Arrays.stream(rs).anyMatch(roles::contains);
    }

    /** ADMIN and DIRECTOR see everything (Section 3.3). */
    public boolean seesAll() {
        return hasAny(Role.ADMIN, Role.DIRECTOR);
    }

    /** Roles allowed to write meeting records for their own advisees. */
    public boolean canAdvise() {
        return hasAny(Role.AOS, Role.DIRECTOR, Role.PROG_LEAD);
    }
}

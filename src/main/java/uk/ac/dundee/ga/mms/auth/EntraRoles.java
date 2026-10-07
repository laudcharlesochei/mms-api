package uk.ac.dundee.ga.mms.auth;

import uk.ac.dundee.ga.mms.domain.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Maps Entra ID app-role values (e.g. "AOS" or "MMS.AOS") to MMS roles. */
public final class EntraRoles {

    private EntraRoles() {
    }

    public static Set<Role> fromClaim(List<String> values) {
        Set<Role> out = EnumSet.noneOf(Role.class);
        if (values == null) {
            return out;
        }
        for (String v : values) {
            if (v == null) {
                continue;
            }
            String code = v.trim().toUpperCase();
            if (code.startsWith("MMS.")) {
                code = code.substring(4);
            }
            code = code.replace('-', '_').replace(' ', '_');
            if ("PROGRAMME_LEAD".equals(code) || "PROGLEAD".equals(code)) {
                code = "PROG_LEAD";
            }
            try {
                out.add(Role.valueOf(code));
            } catch (IllegalArgumentException ignored) {
                // not an MMS role
            }
        }
        return out;
    }
}

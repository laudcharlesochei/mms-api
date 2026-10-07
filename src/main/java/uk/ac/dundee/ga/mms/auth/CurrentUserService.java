package uk.ac.dundee.ga.mms.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Maps the authenticated token to the application user and roles. */
@Service
public class CurrentUserService {

    private final UserStore users;

    public CurrentUserService(UserStore users) {
        this.users = users;
    }

    public CurrentUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Not signed in");
        }
        Long id;
        try {
            id = Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Not signed in");
        }
        AppUser u = users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthenticated", "Unknown user"));
        Set<Role> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Role.valueOf(a.substring(5)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Role.class)));
        return new CurrentUser(u.getId(), u.getEmail(), u.getDisplayName(), roles);
    }

    public AppUser entity() {
        return users.findById(get().id()).orElseThrow();
    }
}

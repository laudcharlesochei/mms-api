package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.service.UserAdminService;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.CreateUserRequest;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.RolesRequest;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.UpdateUserRequest;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.UserDto;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration (FR-03)")
public class UserController {

    private final UserAdminService users;
    private final CurrentUserService current;

    public UserController(UserAdminService users, CurrentUserService current) {
        this.users = users;
        this.current = current;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> list(@RequestParam(required = false) Role role, @RequestParam(required = false) String q) {
        return users.list(role, q);
    }

    /** Active staff holding a role - for pickers such as "reassign AoS" or "programme lead". */
    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public List<UserDto> staff(@RequestParam(required = false) Role role) {
        return users.staff(role);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@Valid @RequestBody CreateUserRequest r) {
        return users.create(current.get(), r);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest r) {
        return users.update(current.get(), id, r);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto roles(@PathVariable Long id, @Valid @RequestBody RolesRequest r) {
        return users.setRoles(current.get(), id, r.roles());
    }

    @PostMapping("/{id}/reset-mfa")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto resetMfa(@PathVariable Long id) {
        return users.resetMfa(id);
    }

    @PostMapping("/{id}/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> invite(@PathVariable Long id) {
        return users.sendInvite(current.get(), id);
    }
}

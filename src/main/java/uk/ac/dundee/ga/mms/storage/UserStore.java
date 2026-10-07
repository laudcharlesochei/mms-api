package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserStore extends CrudStore<AppUser> {
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEntraOid(String oid);

    List<AppUser> findByRole(Role role);

    List<AppUser> findByIds(Collection<Long> ids);

    long count();
}

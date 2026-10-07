package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.storage.UserStore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaUserStore extends JpaCrudStore<AppUser> implements UserStore {
    private final UserRepository r;

    public JpaUserStore(UserRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return email == null ? Optional.empty() : r.findFirstByEmailIgnoreCase(email.trim());
    }

    @Override
    public Optional<AppUser> findByEntraOid(String oid) {
        return oid == null ? Optional.empty() : r.findFirstByEntraOid(oid);
    }

    @Override
    public List<AppUser> findByRole(Role role) {
        return r.findByRole(role);
    }

    @Override
    public List<AppUser> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : r.findAllById(ids);
    }

    @Override
    public long count() {
        return r.count();
    }
}

package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.UserInvite;
import uk.ac.dundee.ga.mms.storage.InviteStore;

import java.util.Optional;

@Repository
public class JpaInviteStore implements InviteStore {
    private final InviteRepository r;

    public JpaInviteStore(InviteRepository r) {
        this.r = r;
    }

    @Override
    public UserInvite save(UserInvite invite) {
        return r.save(invite);
    }

    @Override
    public Optional<UserInvite> findByTokenHash(String tokenHash) {
        return r.findFirstByTokenHash(tokenHash);
    }
}

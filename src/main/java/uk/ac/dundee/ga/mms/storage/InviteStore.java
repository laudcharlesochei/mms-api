package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.UserInvite;

import java.util.Optional;

public interface InviteStore {
    UserInvite save(UserInvite invite);

    Optional<UserInvite> findByTokenHash(String tokenHash);
}

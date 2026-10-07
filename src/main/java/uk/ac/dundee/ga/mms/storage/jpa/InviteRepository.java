package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.UserInvite;

import java.util.Optional;

public interface InviteRepository extends JpaRepository<UserInvite, Long> {
    Optional<UserInvite> findFirstByTokenHash(String tokenHash);
}

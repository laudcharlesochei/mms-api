package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findFirstByEmailIgnoreCase(String email);

    Optional<AppUser> findFirstByEntraOid(String entraOid);

    @Query("select distinct u from AppUser u join u.roles r where r = :role")
    List<AppUser> findByRole(@Param("role") Role role);
}

package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.Employer;

import java.util.Optional;

public interface EmployerRepository extends JpaRepository<Employer, Long> {
    Optional<Employer> findFirstByNameIgnoreCase(String name);
}

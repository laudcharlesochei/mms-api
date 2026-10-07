package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.Programme;

import java.util.List;
import java.util.Optional;

public interface ProgrammeRepository extends JpaRepository<Programme, Long> {
    Optional<Programme> findFirstByCodeIgnoreCase(String code);

    List<Programme> findByLeadUserId(Long leadUserId);
}

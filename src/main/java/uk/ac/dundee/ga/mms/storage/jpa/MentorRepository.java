package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;

import java.util.List;
import java.util.Optional;

public interface MentorRepository extends JpaRepository<WorkplaceMentor, Long> {
    List<WorkplaceMentor> findByEmployerId(Long employerId);

    Optional<WorkplaceMentor> findFirstByEmployerIdAndFullNameIgnoreCase(Long employerId, String fullName);
}

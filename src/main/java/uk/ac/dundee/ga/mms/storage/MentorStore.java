package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;

import java.util.List;
import java.util.Optional;

public interface MentorStore extends CrudStore<WorkplaceMentor> {
    List<WorkplaceMentor> findByEmployerId(Long employerId);

    Optional<WorkplaceMentor> findByEmployerAndName(Long employerId, String fullName);
}

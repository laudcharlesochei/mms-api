package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.Programme;

import java.util.List;
import java.util.Optional;

public interface ProgrammeStore extends CrudStore<Programme> {
    Optional<Programme> findByCode(String code);

    List<Programme> findByLeadUserId(Long userId);
}

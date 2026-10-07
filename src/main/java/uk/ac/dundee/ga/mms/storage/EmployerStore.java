package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.Employer;

import java.util.Optional;

public interface EmployerStore extends CrudStore<Employer> {
    Optional<Employer> findByName(String name);
}

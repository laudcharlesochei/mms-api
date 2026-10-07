package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.Student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StudentStore extends CrudStore<Student> {
    Optional<Student> findByMatricNo(String matricNo);

    Optional<Student> findByUniEmail(String email);

    List<Student> findByIds(Collection<Long> ids);
}

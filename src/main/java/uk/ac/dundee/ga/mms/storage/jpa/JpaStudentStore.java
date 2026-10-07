package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.storage.StudentStore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaStudentStore extends JpaCrudStore<Student> implements StudentStore {
    private final StudentRepository r;

    public JpaStudentStore(StudentRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public Optional<Student> findByMatricNo(String matricNo) {
        return r.findFirstByMatricNoIgnoreCase(matricNo);
    }

    @Override
    public Optional<Student> findByUniEmail(String email) {
        return r.findFirstByUniEmailIgnoreCase(email);
    }

    @Override
    public List<Student> findByIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : r.findAllById(ids);
    }
}

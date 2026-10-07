package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.Student;

import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findFirstByMatricNoIgnoreCase(String matricNo);

    Optional<Student> findFirstByUniEmailIgnoreCase(String email);
}

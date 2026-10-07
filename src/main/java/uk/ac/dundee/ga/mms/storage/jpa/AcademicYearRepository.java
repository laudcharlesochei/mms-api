package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.ac.dundee.ga.mms.domain.AcademicYear;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    Optional<AcademicYear> findFirstByCurrentTrue();

    Optional<AcademicYear> findFirstByLabel(String label);

    @Query("select y from AcademicYear y where y.startDate <= :d and y.endDate >= :d order by y.startDate desc")
    List<AcademicYear> findContaining(@Param("d") LocalDate d);
}

package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;

import java.util.List;

public interface MeetingPeriodRepository extends JpaRepository<MeetingPeriod, Long> {
    List<MeetingPeriod> findByAcademicYearIdOrderBySeqAsc(Long academicYearId);

    void deleteByAcademicYearId(Long academicYearId);
}

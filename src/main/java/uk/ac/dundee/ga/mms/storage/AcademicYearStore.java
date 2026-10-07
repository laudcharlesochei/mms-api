package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AcademicYearStore extends CrudStore<AcademicYear> {
    Optional<AcademicYear> findCurrent();

    Optional<AcademicYear> findByLabel(String label);

    Optional<AcademicYear> findContaining(LocalDate date);

    List<MeetingPeriod> findPeriods(Long academicYearId);

    Optional<MeetingPeriod> findPeriod(Long periodId);

    MeetingPeriod savePeriod(MeetingPeriod period);

    void deletePeriods(Long academicYearId);
}

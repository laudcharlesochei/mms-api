package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaAcademicYearStore extends JpaCrudStore<AcademicYear> implements AcademicYearStore {

    private final AcademicYearRepository years;
    private final MeetingPeriodRepository periods;

    public JpaAcademicYearStore(AcademicYearRepository years, MeetingPeriodRepository periods) {
        super(years);
        this.years = years;
        this.periods = periods;
    }

    @Override
    public Optional<AcademicYear> findCurrent() {
        return years.findFirstByCurrentTrue();
    }

    @Override
    public Optional<AcademicYear> findByLabel(String label) {
        return years.findFirstByLabel(label);
    }

    @Override
    public Optional<AcademicYear> findContaining(LocalDate date) {
        return years.findContaining(date).stream().findFirst();
    }

    @Override
    public List<MeetingPeriod> findPeriods(Long academicYearId) {
        return periods.findByAcademicYearIdOrderBySeqAsc(academicYearId);
    }

    @Override
    public Optional<MeetingPeriod> findPeriod(Long periodId) {
        return periodId == null ? Optional.empty() : periods.findById(periodId);
    }

    @Override
    public MeetingPeriod savePeriod(MeetingPeriod period) {
        return periods.save(period);
    }

    @Override
    @Transactional
    public void deletePeriods(Long academicYearId) {
        periods.deleteByAcademicYearId(academicYearId);
        periods.flush();
    }
}

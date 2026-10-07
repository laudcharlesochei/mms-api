package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;

import java.util.List;
import java.util.Optional;

public interface AllocationStore extends CrudStore<AdvisorAllocation> {
    List<AdvisorAllocation> findByAcademicYear(Long academicYearId);

    List<AdvisorAllocation> findByStudent(Long studentId);

    List<AdvisorAllocation> findByAos(Long aosUserId);

    Optional<AdvisorAllocation> findOpen(Long studentId, Long academicYearId);
}

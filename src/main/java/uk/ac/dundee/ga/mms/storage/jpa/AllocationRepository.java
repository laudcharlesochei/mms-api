package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;

import java.util.List;
import java.util.Optional;

public interface AllocationRepository extends JpaRepository<AdvisorAllocation, Long> {
    List<AdvisorAllocation> findByAcademicYearId(Long academicYearId);

    List<AdvisorAllocation> findByStudentIdOrderByValidFromDesc(Long studentId);

    List<AdvisorAllocation> findByAosUserId(Long aosUserId);

    Optional<AdvisorAllocation> findFirstByStudentIdAndAcademicYearIdAndValidToIsNull(Long studentId, Long academicYearId);
}

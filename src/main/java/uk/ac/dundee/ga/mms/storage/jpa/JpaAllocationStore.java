package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.storage.AllocationStore;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaAllocationStore extends JpaCrudStore<AdvisorAllocation> implements AllocationStore {
    private final AllocationRepository r;

    public JpaAllocationStore(AllocationRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public List<AdvisorAllocation> findByAcademicYear(Long academicYearId) {
        return r.findByAcademicYearId(academicYearId);
    }

    @Override
    public List<AdvisorAllocation> findByStudent(Long studentId) {
        return r.findByStudentIdOrderByValidFromDesc(studentId);
    }

    @Override
    public List<AdvisorAllocation> findByAos(Long aosUserId) {
        return r.findByAosUserId(aosUserId);
    }

    @Override
    public Optional<AdvisorAllocation> findOpen(Long studentId, Long academicYearId) {
        return r.findFirstByStudentIdAndAcademicYearIdAndValidToIsNull(studentId, academicYearId);
    }
}

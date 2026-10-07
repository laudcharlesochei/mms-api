package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;
import uk.ac.dundee.ga.mms.storage.MentorStore;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaMentorStore extends JpaCrudStore<WorkplaceMentor> implements MentorStore {
    private final MentorRepository r;

    public JpaMentorStore(MentorRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public List<WorkplaceMentor> findByEmployerId(Long employerId) {
        return r.findByEmployerId(employerId);
    }

    @Override
    public Optional<WorkplaceMentor> findByEmployerAndName(Long employerId, String fullName) {
        return r.findFirstByEmployerIdAndFullNameIgnoreCase(employerId, fullName);
    }
}

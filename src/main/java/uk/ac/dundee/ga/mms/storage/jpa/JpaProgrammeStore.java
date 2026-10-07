package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.Programme;
import uk.ac.dundee.ga.mms.storage.ProgrammeStore;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaProgrammeStore extends JpaCrudStore<Programme> implements ProgrammeStore {
    private final ProgrammeRepository r;

    public JpaProgrammeStore(ProgrammeRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public Optional<Programme> findByCode(String code) {
        return r.findFirstByCodeIgnoreCase(code);
    }

    @Override
    public List<Programme> findByLeadUserId(Long userId) {
        return r.findByLeadUserId(userId);
    }
}

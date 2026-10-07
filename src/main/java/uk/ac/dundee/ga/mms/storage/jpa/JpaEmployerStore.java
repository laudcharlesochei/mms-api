package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.Employer;
import uk.ac.dundee.ga.mms.storage.EmployerStore;

import java.util.Optional;

@Repository
public class JpaEmployerStore extends JpaCrudStore<Employer> implements EmployerStore {
    private final EmployerRepository r;

    public JpaEmployerStore(EmployerRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public Optional<Employer> findByName(String name) {
        return r.findFirstByNameIgnoreCase(name);
    }
}

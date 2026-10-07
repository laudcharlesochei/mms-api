package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.storage.CrudStore;

import java.util.List;
import java.util.Optional;

/** Base class for the JPA implementations of the storage interfaces. */
public abstract class JpaCrudStore<T> implements CrudStore<T> {

    protected final JpaRepository<T, Long> repo;

    protected JpaCrudStore(JpaRepository<T, Long> repo) {
        this.repo = repo;
    }

    @Override
    public List<T> findAll() {
        return repo.findAll();
    }

    @Override
    public Optional<T> findById(Long id) {
        return id == null ? Optional.empty() : repo.findById(id);
    }

    @Override
    public T save(T entity) {
        return repo.save(entity);
    }

    @Override
    public void deleteById(Long id) {
        repo.deleteById(id);
    }
}

package uk.ac.dundee.ga.mms.storage;

import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction (NFR-14). Services depend only on these interfaces, so the storage
 * layer can be swapped (V1: JPA/MySQL, V2: Microsoft Graph / SharePoint lists).
 */
public interface CrudStore<T> {
    List<T> findAll();

    Optional<T> findById(Long id);

    T save(T entity);

    void deleteById(Long id);
}

package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.ImportRowError;

import java.util.List;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {
    List<ImportRowError> findByBatchIdOrderByRowNoAsc(Long batchId);
}

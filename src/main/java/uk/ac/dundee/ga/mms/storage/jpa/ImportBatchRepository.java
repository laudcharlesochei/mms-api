package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.ImportBatch;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findByOrderByUploadedAtDesc(Pageable pageable);
}

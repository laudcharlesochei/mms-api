package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.ImportBatch;
import uk.ac.dundee.ga.mms.domain.ImportRowError;
import uk.ac.dundee.ga.mms.storage.ImportStore;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaImportStore implements ImportStore {
    private final ImportBatchRepository batches;
    private final ImportRowErrorRepository errors;

    public JpaImportStore(ImportBatchRepository batches, ImportRowErrorRepository errors) {
        this.batches = batches;
        this.errors = errors;
    }

    @Override
    public ImportBatch saveBatch(ImportBatch batch) {
        return batches.save(batch);
    }

    @Override
    public Optional<ImportBatch> findBatch(Long id) {
        return batches.findById(id);
    }

    @Override
    public List<ImportBatch> recentBatches(int limit) {
        return batches.findByOrderByUploadedAtDesc(PageRequest.of(0, limit));
    }

    @Override
    public void saveErrors(List<ImportRowError> list) {
        errors.saveAll(list);
    }

    @Override
    public List<ImportRowError> findErrors(Long batchId) {
        return errors.findByBatchIdOrderByRowNoAsc(batchId);
    }
}

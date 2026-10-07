package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.ImportBatch;
import uk.ac.dundee.ga.mms.domain.ImportRowError;

import java.util.List;
import java.util.Optional;

public interface ImportStore {
    ImportBatch saveBatch(ImportBatch batch);

    Optional<ImportBatch> findBatch(Long id);

    List<ImportBatch> recentBatches(int limit);

    void saveErrors(List<ImportRowError> errors);

    List<ImportRowError> findErrors(Long batchId);
}

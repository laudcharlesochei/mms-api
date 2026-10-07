package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.service.ImportService;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ImportBatchSummary;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ImportPreview;

import java.util.List;

@RestController
@RequestMapping("/api/v1/imports")
@PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
@Tag(name = "Imports", description = "AoS allocation import wizard (FR-07, FR-08)")
public class ImportController {

    private final ImportService imports;
    private final CurrentUserService current;

    public ImportController(ImportService imports, CurrentUserService current) {
        this.imports = imports;
        this.current = current;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return CsvResponses.csv("mms-allocation-template.csv", imports.templateCsv());
    }

    @GetMapping
    public List<ImportBatchSummary> recent() {
        return imports.recent();
    }

    @PostMapping(value = "/allocations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview upload(@RequestParam("file") MultipartFile file) {
        return imports.upload(current.get(), file);
    }

    @GetMapping("/{id}")
    public ImportPreview get(@PathVariable Long id) {
        return imports.get(id);
    }

    @PostMapping("/{id}/commit")
    public ImportPreview commit(@PathVariable Long id) {
        return imports.commit(current.get(), id);
    }

    @PostMapping("/{id}/cancel")
    public ImportPreview cancel(@PathVariable Long id) {
        return imports.cancel(id);
    }

    @GetMapping("/{id}/errors.csv")
    public ResponseEntity<byte[]> errors(@PathVariable Long id) {
        return CsvResponses.csv("mms-import-" + id + "-errors.csv", imports.errorReportCsv(id));
    }
}

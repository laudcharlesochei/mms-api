package uk.ac.dundee.ga.mms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AllocationSource;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Employer;
import uk.ac.dundee.ga.mms.domain.ImportBatch;
import uk.ac.dundee.ga.mms.domain.ImportRowError;
import uk.ac.dundee.ga.mms.domain.ImportStatus;
import uk.ac.dundee.ga.mms.domain.Programme;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.domain.StudentStatus;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.storage.EmployerStore;
import uk.ac.dundee.ga.mms.storage.ImportStore;
import uk.ac.dundee.ga.mms.storage.MentorStore;
import uk.ac.dundee.ga.mms.storage.ProgrammeStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.util.Csv;
import uk.ac.dundee.ga.mms.util.TextSanitizer;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ImportBatchSummary;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ImportPreview;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.ImportRowDto;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * September allocation import (FR-07, FR-08, workflow 7.2): upload -> validate every row ->
 * preview (add / update / errors) -> commit valid rows. Re-import is idempotent (match on
 * matric number + academic year); nothing is deleted.
 */
@Service
public class ImportService {

    public static final List<String> COLUMNS = List.of("matric_no", "first_name", "last_name", "student_email",
            "programme_code", "aos_email", "employer_name", "mentor_name", "mentor_email", "academic_year");
    private static final Set<String> REQUIRED = Set.of("matric_no", "first_name", "last_name", "programme_code",
            "aos_email", "academic_year");
    private static final int MAX_ROWS = 2000;
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Pattern MATRIC = Pattern.compile("[A-Za-z0-9]{5,12}");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ImportStore imports;
    private final StudentStore students;
    private final ProgrammeStore programmes;
    private final EmployerStore employers;
    private final MentorStore mentors;
    private final UserStore users;
    private final AcademicYearStore years;
    private final AllocationService allocationService;
    private final AuditService audit;
    private final Lookups lookups;
    private final ObjectMapper json;

    public ImportService(ImportStore imports, StudentStore students, ProgrammeStore programmes, EmployerStore employers,
                         MentorStore mentors, UserStore users, AcademicYearStore years,
                         AllocationService allocationService, AuditService audit, Lookups lookups, ObjectMapper json) {
        this.imports = imports;
        this.students = students;
        this.programmes = programmes;
        this.employers = employers;
        this.mentors = mentors;
        this.users = users;
        this.years = years;
        this.allocationService = allocationService;
        this.audit = audit;
        this.lookups = lookups;
        this.json = json;
    }

    public String templateCsv() {
        return Csv.row(COLUMNS) + Csv.row(List.of("2512345", "Alex", "Example", "2512345@dundee.ac.uk", "GA-SE",
                "aos1@mms.local", "Example Ltd", "Jordan Mentor", "jordan.mentor@example.com", "2026/27"));
    }

    // ------------------------------------------------------------------ upload & preview

    @Transactional
    public ImportPreview upload(CurrentUser u, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a CSV or XLSX file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("The file is larger than 2 MB.");
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String lower = name.toLowerCase(Locale.ROOT);
        List<Map<String, String>> raw;
        try (InputStream in = file.getInputStream()) {
            if (lower.endsWith(".csv")) {
                raw = parseCsv(in);
            } else if (lower.endsWith(".xlsx")) {
                raw = parseXlsx(in);
            } else {
                throw ApiException.badRequest("Only .csv and .xlsx files are accepted.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("The file could not be read: " + e.getMessage());
        }
        if (raw.size() > MAX_ROWS) {
            throw ApiException.badRequest("The file has " + raw.size() + " rows; the maximum is " + MAX_ROWS + ".");
        }
        List<ImportRowDto> rows = validate(raw);
        ImportBatch b = new ImportBatch();
        b.setFileName(TextSanitizer.clean(name));
        b.setUploadedBy(u.id());
        b.setUploadedAt(Instant.now());
        b.setStatus(ImportStatus.PREVIEW);
        b.setRowsTotal(rows.size());
        b.setRowsError((int) rows.stream().filter(r -> !r.errors().isEmpty()).count());
        b.setRowsOk(b.getRowsTotal() - b.getRowsError());
        b.setRowsAdd((int) rows.stream().filter(r -> r.errors().isEmpty() && "ADD".equals(r.action())).count());
        b.setRowsUpdate((int) rows.stream().filter(r -> r.errors().isEmpty() && "UPDATE".equals(r.action())).count());
        try {
            b.setPayloadJson(json.writeValueAsString(rows));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        ImportBatch saved = imports.saveBatch(b);
        List<ImportRowError> errs = new ArrayList<>();
        for (ImportRowDto r : rows) {
            for (String msg : r.errors()) {
                ImportRowError e = new ImportRowError();
                e.setBatchId(saved.getId());
                e.setRowNo(r.rowNo());
                e.setMessage(msg.length() > 500 ? msg.substring(0, 500) : msg);
                errs.add(e);
            }
        }
        if (!errs.isEmpty()) {
            imports.saveErrors(errs);
        }
        audit.record("IMPORT_UPLOAD", "ImportBatch", saved.getId(), Map.of("rows", rows.size(), "errors", saved.getRowsError()));
        return toPreview(saved, rows);
    }

    public ImportPreview get(Long batchId) {
        ImportBatch b = imports.findBatch(batchId).orElseThrow(() -> ApiException.notFound("Import"));
        return toPreview(b, rowsOf(b));
    }

    public List<ImportBatchSummary> recent() {
        Lookups.Snapshot s = lookups.snapshot();
        return imports.recentBatches(20).stream().map(b -> new ImportBatchSummary(b.getId(), b.getFileName(), b.getStatus(),
                b.getRowsTotal(), b.getRowsOk(), b.getRowsError(), b.getUploadedAt(), s.userName(b.getUploadedBy()))).toList();
    }

    public String errorReportCsv(Long batchId) {
        StringBuilder sb = new StringBuilder(Csv.row(List.of("row_no", "message")));
        for (ImportRowError e : imports.findErrors(batchId)) {
            sb.append(Csv.row(Arrays.asList(e.getRowNo(), e.getMessage())));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ commit

    @Transactional
    public ImportPreview commit(CurrentUser u, Long batchId) {
        ImportBatch b = imports.findBatch(batchId).orElseThrow(() -> ApiException.notFound("Import"));
        if (b.getStatus() != ImportStatus.PREVIEW) {
            throw ApiException.conflict("already-committed", "This import has already been " + b.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        List<ImportRowDto> rows = rowsOf(b);
        LocalDate today = LocalDate.now(DashboardService.UK);
        int added = 0;
        int updated = 0;
        for (ImportRowDto r : rows) {
            if (!r.errors().isEmpty()) {
                continue;
            }
            AcademicYear year = years.findByLabel(r.academicYear()).orElseThrow();
            Programme prog = programmes.findByCode(r.programmeCode()).orElseThrow();
            AppUser aos = users.findByEmail(r.aosEmail()).orElseThrow();
            Long employerId = null;
            Long mentorId = null;
            if (TextSanitizer.hasText(r.employerName())) {
                Employer e = employers.findByName(r.employerName()).orElseGet(() -> {
                    Employer ne = new Employer();
                    ne.setName(r.employerName());
                    return employers.save(ne);
                });
                employerId = e.getId();
                if (TextSanitizer.hasText(r.mentorName())) {
                    final Long eid = employerId;
                    WorkplaceMentor m = mentors.findByEmployerAndName(eid, r.mentorName()).orElseGet(() -> {
                        WorkplaceMentor nm = new WorkplaceMentor();
                        nm.setEmployerId(eid);
                        nm.setFullName(r.mentorName());
                        return nm;
                    });
                    if (TextSanitizer.hasText(r.mentorEmail())) {
                        m.setEmail(r.mentorEmail().toLowerCase(Locale.ROOT));
                    }
                    mentorId = mentors.save(m).getId();
                }
            }
            Student st = students.findByMatricNo(r.matricNo()).orElse(null);
            if (st == null) {
                st = new Student();
                st.setMatricNo(r.matricNo().toUpperCase(Locale.ROOT));
                st.setStatus(StudentStatus.ACTIVE);
                added++;
            } else {
                updated++;
            }
            st.setFirstName(r.firstName());
            st.setLastName(r.lastName());
            if (TextSanitizer.hasText(r.studentEmail())) {
                st.setUniEmail(r.studentEmail().toLowerCase(Locale.ROOT));
            }
            st.setProgrammeId(prog.getId());
            if (employerId != null) {
                st.setEmployerId(employerId);
            }
            if (mentorId != null) {
                st.setMentorId(mentorId);
            }
            st = students.save(st);
            allocationService.closePreviousYears(st.getId(), year);
            LocalDate from = today.isBefore(year.getStartDate()) || today.isAfter(year.getEndDate()) ? year.getStartDate() : today;
            allocationService.allocate(st.getId(), aos.getId(), year, from, AllocationSource.IMPORT);
        }
        b.setStatus(ImportStatus.COMMITTED);
        b.setCommittedAt(Instant.now());
        imports.saveBatch(b);
        audit.record("IMPORT_COMMIT", "ImportBatch", batchId, Map.of("added", added, "updated", updated));
        return toPreview(b, rows);
    }

    @Transactional
    public ImportPreview cancel(Long batchId) {
        ImportBatch b = imports.findBatch(batchId).orElseThrow(() -> ApiException.notFound("Import"));
        if (b.getStatus() == ImportStatus.PREVIEW) {
            b.setStatus(ImportStatus.CANCELLED);
            imports.saveBatch(b);
            audit.record("IMPORT_CANCEL", "ImportBatch", batchId, Map.of());
        }
        return toPreview(b, rowsOf(b));
    }

    // ------------------------------------------------------------------ parsing & validation

    List<Map<String, String>> parseCsv(InputStream in) throws Exception {
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        CSVFormat fmt = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true)
                .setTrim(true).setIgnoreHeaderCase(true).build();
        List<Map<String, String>> out = new ArrayList<>();
        try (CSVParser p = CSVParser.parse(reader, fmt)) {
            checkHeaders(p.getHeaderNames());
            for (CSVRecord rec : p) {
                Map<String, String> row = new HashMap<>();
                for (String h : p.getHeaderNames()) {
                    String key = normaliseHeader(h);
                    row.put(key, rec.isMapped(h) && rec.isSet(h) ? rec.get(h) : "");
                }
                if (row.values().stream().allMatch(String::isBlank)) {
                    continue;
                }
                out.add(row);
            }
        }
        return out;
    }

    List<Map<String, String>> parseXlsx(InputStream in) throws Exception {
        List<Map<String, String>> out = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw ApiException.badRequest("The spreadsheet is empty.");
            }
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c);
                headers.add(cell == null ? "" : fmt.formatCellValue(cell));
            }
            checkHeaders(headers);
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row r = sheet.getRow(i);
                if (r == null) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = r.getCell(c);
                    row.put(normaliseHeader(headers.get(c)), cell == null ? "" : fmt.formatCellValue(cell).trim());
                }
                if (row.values().stream().allMatch(String::isBlank)) {
                    continue;
                }
                out.add(row);
            }
        }
        return out;
    }

    private static String normaliseHeader(String h) {
        return h == null ? "" : h.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace("﻿", "");
    }

    private void checkHeaders(List<String> headers) {
        Set<String> got = new HashSet<>();
        headers.forEach(h -> got.add(normaliseHeader(h)));
        List<String> missing = REQUIRED.stream().filter(r -> !got.contains(r)).sorted().toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("Missing column(s): " + String.join(", ", missing)
                    + ". Download the template for the expected header row.");
        }
    }

    List<ImportRowDto> validate(List<Map<String, String>> raw) {
        Map<String, Programme> progByCode = new HashMap<>();
        programmes.findAll().forEach(p -> progByCode.put(p.getCode().toUpperCase(Locale.ROOT), p));
        Map<String, AcademicYear> yearByLabel = new HashMap<>();
        years.findAll().forEach(y -> yearByLabel.put(y.getLabel(), y));
        Map<String, AppUser> userByEmail = new HashMap<>();
        users.findAll().forEach(x -> userByEmail.put(x.getEmail().toLowerCase(Locale.ROOT), x));
        Set<String> existingMatrics = new HashSet<>();
        students.findAll().forEach(st -> existingMatrics.add(st.getMatricNo().toUpperCase(Locale.ROOT)));
        Set<String> seen = new HashSet<>();
        List<ImportRowDto> out = new ArrayList<>();
        int rowNo = 1; // header is row 1
        for (Map<String, String> r : raw) {
            rowNo++;
            List<String> errors = new ArrayList<>();
            String matric = val(r, "matric_no").toUpperCase(Locale.ROOT);
            String first = TextSanitizer.clean(val(r, "first_name"));
            String last = TextSanitizer.clean(val(r, "last_name"));
            String sEmail = val(r, "student_email");
            String prog = val(r, "programme_code").toUpperCase(Locale.ROOT);
            String aosEmail = val(r, "aos_email").toLowerCase(Locale.ROOT);
            String employer = TextSanitizer.clean(val(r, "employer_name"));
            String mentor = TextSanitizer.clean(val(r, "mentor_name"));
            String mEmail = val(r, "mentor_email");
            String year = val(r, "academic_year");

            if (!MATRIC.matcher(matric).matches()) {
                errors.add("matric_no must be 5-12 letters or digits");
            }
            if (first == null || first.length() > 80) {
                errors.add("first_name is required (max 80 characters)");
            }
            if (last == null || last.length() > 80) {
                errors.add("last_name is required (max 80 characters)");
            }
            if (!sEmail.isBlank() && !EMAIL.matcher(sEmail).matches()) {
                errors.add("student_email is not a valid email address");
            }
            if (!progByCode.containsKey(prog)) {
                errors.add("programme_code '" + prog + "' is not a known programme");
            }
            AppUser aos = userByEmail.get(aosEmail);
            if (aos == null) {
                errors.add("aos_email '" + aosEmail + "' has no MMS account");
            } else if (!aos.isActive()) {
                errors.add("aos_email '" + aosEmail + "' belongs to a deactivated account");
            } else if (!(aos.hasRole(Role.AOS) || aos.hasRole(Role.DIRECTOR) || aos.hasRole(Role.PROG_LEAD))) {
                errors.add("aos_email '" + aosEmail + "' is not an Advisor of Studies");
            }
            if (employer != null && employer.length() > 160) {
                errors.add("employer_name is longer than 160 characters");
            }
            if (mentor != null && employer == null) {
                errors.add("mentor_name needs an employer_name");
            }
            if (!mEmail.isBlank() && !EMAIL.matcher(mEmail).matches()) {
                errors.add("mentor_email is not a valid email address");
            }
            if (!yearByLabel.containsKey(year)) {
                errors.add("academic_year '" + year + "' does not exist - create it first");
            }
            String key = matric + "|" + year;
            if (!seen.add(key)) {
                errors.add("duplicate row for " + matric + " in " + year);
            }
            String action = existingMatrics.contains(matric) ? "UPDATE" : "ADD";
            out.add(new ImportRowDto(rowNo, matric, first, last, sEmail.isBlank() ? null : sEmail, prog, aosEmail,
                    employer, mentor, mEmail.isBlank() ? null : mEmail, year, action, errors));
        }
        return out;
    }

    private static String val(Map<String, String> r, String key) {
        String v = r.get(key);
        return v == null ? "" : v.trim();
    }

    private List<ImportRowDto> rowsOf(ImportBatch b) {
        if (b.getPayloadJson() == null) {
            return List.of();
        }
        try {
            return json.readValue(b.getPayloadJson(), new TypeReference<List<ImportRowDto>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ImportPreview toPreview(ImportBatch b, List<ImportRowDto> rows) {
        Map<Integer, ImportRowDto> ordered = new LinkedHashMap<>();
        rows.forEach(r -> ordered.put(r.rowNo(), r));
        return new ImportPreview(b.getId(), b.getFileName(), b.getStatus(), b.getRowsTotal(), b.getRowsOk(),
                b.getRowsError(), b.getRowsAdd(), b.getRowsUpdate(), b.getUploadedAt(), b.getCommittedAt(),
                new ArrayList<>(ordered.values()));
    }
}

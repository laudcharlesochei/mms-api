package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.service.AcademicYearService;
import uk.ac.dundee.ga.mms.service.AuditService;
import uk.ac.dundee.ga.mms.service.DashboardService;
import uk.ac.dundee.ga.mms.service.DashboardService.Filter;
import uk.ac.dundee.ga.mms.service.MeetingService;
import uk.ac.dundee.ga.mms.web.dto.DashboardDtos.AdviseesResponse;
import uk.ac.dundee.ga.mms.web.dto.DashboardDtos.AdvisorRow;
import uk.ac.dundee.ga.mms.web.dto.DashboardDtos.Summary;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgressRow;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dashboard", description = "Progress tracking (FR-21 to FR-24) and the AoS advisee list (S2)")
public class DashboardController {

    private final DashboardService dashboard;
    private final AcademicYearService years;
    private final MeetingService meetings;
    private final CurrentUserService current;
    private final AuditService audit;

    public DashboardController(DashboardService dashboard, AcademicYearService years, MeetingService meetings,
                               CurrentUserService current, AuditService audit) {
        this.dashboard = dashboard;
        this.years = years;
        this.meetings = meetings;
        this.current = current;
        this.audit = audit;
    }

    @GetMapping("/advisees")
    public AdviseesResponse advisees(@RequestParam(required = false) Long year) {
        CurrentUser u = current.get();
        AcademicYear y = years.resolve(year);
        List<ProgressRow> rows = dashboard.advisees(u, y);
        return new AdviseesResponse(dashboard.summary(y, rows), rows, meetings.drafts(u));
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD','AOS')")
    public Summary summary(@RequestParam(required = false) Long year, @RequestParam(required = false) Long programme,
                           @RequestParam(required = false) Long aos) {
        AcademicYear y = years.resolve(year);
        return dashboard.summary(y, dashboard.rows(current.get(), y, new Filter(programme, aos, null, null)));
    }

    @GetMapping("/dashboard/students")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD','AOS')")
    public List<ProgressRow> students(@RequestParam(required = false) Long year,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) Long programme,
                                      @RequestParam(required = false) Long aos,
                                      @RequestParam(required = false) String q) {
        AcademicYear y = years.resolve(year);
        return dashboard.rows(current.get(), y, new Filter(programme, aos, status, q));
    }

    @GetMapping("/dashboard/advisors")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD','AOS')")
    public List<AdvisorRow> advisors(@RequestParam(required = false) Long year,
                                     @RequestParam(required = false) Long programme) {
        AcademicYear y = years.resolve(year);
        return dashboard.advisors(dashboard.rows(current.get(), y, new Filter(programme, null, null, null)));
    }

    @GetMapping("/dashboard/students/export")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long year,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long programme,
                                         @RequestParam(required = false) Long aos,
                                         @RequestParam(required = false) String q) {
        AcademicYear y = years.resolve(year);
        List<ProgressRow> rows = dashboard.rows(current.get(), y, new Filter(programme, aos, status, q));
        audit.record("EXPORT_PROGRESS", "Dashboard", y.getId(), Map.of("rows", rows.size()));
        return CsvResponses.csv("mms-progress-" + y.getLabel().replace('/', '-') + ".csv", dashboard.exportCsv(rows));
    }
}

package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.service.AcademicYearService;
import uk.ac.dundee.ga.mms.service.Lookups;
import uk.ac.dundee.ga.mms.service.ReferenceDataService;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AcademicYearDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AcademicYearRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.EmployerDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.EmployerRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.MentorDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.MentorRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.PeriodDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgrammeDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgrammeRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference data", description = "Programmes, employers, workplace mentors, academic years (FR-05, FR-06)")
public class ReferenceDataController {

    private final ReferenceDataService reference;
    private final AcademicYearService years;
    private final Lookups lookups;

    public ReferenceDataController(ReferenceDataService reference, AcademicYearService years, Lookups lookups) {
        this.reference = reference;
        this.years = years;
        this.lookups = lookups;
    }

    @GetMapping("/programmes")
    public List<ProgrammeDto> programmes() {
        return reference.programmes();
    }

    @PostMapping("/programmes")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    public ProgrammeDto createProgramme(@Valid @RequestBody ProgrammeRequest r) {
        Long id = reference.saveProgramme(null, r).getId();
        return reference.programmes().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @PutMapping("/programmes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    public ProgrammeDto updateProgramme(@PathVariable Long id, @Valid @RequestBody ProgrammeRequest r) {
        reference.saveProgramme(id, r);
        return reference.programmes().stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @GetMapping("/employers")
    public List<EmployerDto> employers() {
        return reference.employers();
    }

    @PostMapping("/employers")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public EmployerDto createEmployer(@Valid @RequestBody EmployerRequest r) {
        return reference.toDto(reference.saveEmployer(null, r));
    }

    @PutMapping("/employers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public EmployerDto updateEmployer(@PathVariable Long id, @Valid @RequestBody EmployerRequest r) {
        return reference.toDto(reference.saveEmployer(id, r));
    }

    @GetMapping("/mentors")
    public List<MentorDto> mentors(@RequestParam(required = false) Long employer) {
        return reference.mentors(employer);
    }

    @PostMapping("/mentors")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public MentorDto createMentor(@Valid @RequestBody MentorRequest r) {
        return reference.toDto(reference.saveMentor(null, r), lookups.snapshot());
    }

    @PutMapping("/mentors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public MentorDto updateMentor(@PathVariable Long id, @Valid @RequestBody MentorRequest r) {
        return reference.toDto(reference.saveMentor(id, r), lookups.snapshot());
    }

    @GetMapping("/academic-years")
    public List<AcademicYearDto> academicYears() {
        return years.list();
    }

    @GetMapping("/academic-years/{id}")
    public AcademicYearDto academicYear(@PathVariable Long id) {
        return years.get(id);
    }

    @PostMapping("/academic-years")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    public AcademicYearDto createYear(@Valid @RequestBody AcademicYearRequest r) {
        return years.create(r);
    }

    @PutMapping("/academic-years/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    public AcademicYearDto updateYear(@PathVariable Long id, @Valid @RequestBody AcademicYearRequest r) {
        return years.update(id, r);
    }

    @PutMapping("/academic-years/{id}/periods")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    public AcademicYearDto updatePeriods(@PathVariable Long id, @RequestBody List<PeriodDto> periods) {
        return years.updatePeriods(id, periods);
    }
}

package uk.ac.dundee.ga.mms.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uk.ac.dundee.ga.mms.domain.AllocationSource;
import uk.ac.dundee.ga.mms.domain.ProgressStatus;
import uk.ac.dundee.ga.mms.domain.StudentStatus;

import java.time.LocalDate;
import java.util.List;

/** Request/response bodies for reference data, students and allocations (FR-05, FR-06, FR-09). */
public final class ReferenceDtos {

    private ReferenceDtos() {
    }

    public record PeriodDto(Long id, int seq, LocalDate startDate, LocalDate endDate) {
    }

    public record AcademicYearDto(Long id, String label, LocalDate startDate, LocalDate endDate,
                                  int requiredMeetings, boolean current, List<PeriodDto> periods) {
    }

    public record AcademicYearRequest(
            @NotBlank @Pattern(regexp = "\\d{4}/\\d{2}", message = "Use the form 2026/27") String label,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Min(1) @Max(12) int requiredMeetings,
            boolean current) {
    }

    public record ProgrammeDto(Long id, String code, String name, Long leadUserId, String leadName, boolean active,
                               long studentCount) {
    }

    public record ProgrammeRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 120) String name,
            Long leadUserId,
            Boolean active) {
    }

    public record EmployerDto(Long id, String name, String address, boolean active) {
    }

    public record EmployerRequest(@NotBlank @Size(max = 160) String name, @Size(max = 255) String address, Boolean active) {
    }

    public record MentorDto(Long id, Long employerId, String employerName, String fullName, String email,
                            String phone, boolean active) {
    }

    public record MentorRequest(
            @NotNull Long employerId,
            @NotBlank @Size(max = 120) String fullName,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            Boolean active) {
    }

    public record StudentRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{5,12}", message = "5-12 letters or digits") String matricNo,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Email @Size(max = 254) String uniEmail,
            @NotNull Long programmeId,
            Long employerId,
            Long mentorId,
            @Min(1) @Max(6) Integer yearOfStudy,
            StudentStatus status) {
    }

    public record StudentSummary(Long id, String matricNo, String firstName, String lastName, String fullName,
                                 String uniEmail, Long programmeId, String programmeName, Long employerId,
                                 String employerName, Long mentorId, String mentorName, Integer yearOfStudy,
                                 StudentStatus status, Long aosUserId, String aosName) {
    }

    public record AllocationDto(Long id, Long studentId, Long aosUserId, String aosName, String aosEmail,
                                Long academicYearId, String academicYearLabel, LocalDate validFrom,
                                LocalDate validTo, AllocationSource source) {
    }

    public record StudentDetail(StudentSummary student, MentorDto mentor, EmployerDto employer,
                                List<AllocationDto> allocations, ProgressRow currentProgress,
                                List<MentorDto> employerMentors) {
    }

    public record ReassignRequest(@NotNull Long aosUserId, Long academicYearId, LocalDate effectiveFrom) {
    }

    /** One row of the per-student progress table (FR-22) and of an AoS's advisee list (S2). */
    public record ProgressRow(Long studentId, String matricNo, String studentName, Long programmeId,
                              String programmeCode, String programmeName, Long aosUserId, String aosName,
                              String employerName, String mentorName, int meetingsDone, int meetingsRequired,
                              LocalDate lastMeetingDate, ProgressStatus status, boolean openConcern,
                              StudentStatus studentStatus) {
    }
}

package uk.ac.dundee.ga.mms.web.dto;

import java.util.List;

/** Dashboard bodies (FR-21 to FR-24). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Summary(Long academicYearId, String academicYearLabel, Integer currentPeriodSeq, int students,
                          int meetingsRequired, int meetingsDone, double percentComplete, int zeroMeetings,
                          int openConcerns, int overdue, int due, int onTrack) {
    }

    public record AdvisorRow(Long aosUserId, String aosName, String aosEmail, int advisees, int meetingsDone,
                             int meetingsRequired, int adviseesOverdue, int adviseesDue, int openConcerns) {
    }

    public record AdviseesResponse(Summary summary, List<ReferenceDtos.ProgressRow> advisees,
                                   List<MeetingDtos.MeetingSummary> drafts) {
    }
}

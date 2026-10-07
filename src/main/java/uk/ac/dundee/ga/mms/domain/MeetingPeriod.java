package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** One slice of an academic year; a student needs one submitted meeting per period. */
@Entity
@Table(name = "meeting_period")
@Getter @Setter @NoArgsConstructor
public class MeetingPeriod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long academicYearId;
    private int seq;
    private LocalDate startDate;
    private LocalDate endDate;

    public boolean contains(LocalDate d) {
        return d != null && !d.isBefore(startDate) && !d.isAfter(endDate);
    }
}

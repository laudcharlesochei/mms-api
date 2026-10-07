package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** An academic year (e.g. 2026/27) holding the number of meetings required per student. */
@Entity
@Table(name = "academic_year")
@Getter @Setter @NoArgsConstructor
public class AcademicYear {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String label;
    private LocalDate startDate;
    private LocalDate endDate;
    private int requiredMeetings = 3;
    @Column(name = "is_current")
    private boolean current;

    public boolean contains(LocalDate d) {
        return d != null && !d.isBefore(startDate) && !d.isAfter(endDate);
    }
}

package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Links a student to an Advisor of Studies for one academic year. */
@Entity
@Table(name = "advisor_allocation")
@Getter @Setter @NoArgsConstructor
public class AdvisorAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long studentId;
    private Long aosUserId;
    private Long academicYearId;
    private LocalDate validFrom;
    private LocalDate validTo;
    @Enumerated(EnumType.STRING)
    private AllocationSource source = AllocationSource.MANUAL;

    public boolean isOpen() {
        return validTo == null;
    }
}

package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Records that a "meetings due" reminder was sent to an AoS for a period, so it is sent once (from MentorSync progress reminders). */
@Entity
@Table(name = "reminder_log")
@Getter @Setter @NoArgsConstructor
public class ReminderLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long aosUserId;
    private Long periodId;
    private String kind;
    private Instant sentAt;
}

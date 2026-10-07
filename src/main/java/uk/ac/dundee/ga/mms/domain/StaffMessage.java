package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Direct message between two users (carried over from the MentorSync messaging module). */
@Entity
@Table(name = "staff_message")
@Getter @Setter @NoArgsConstructor
public class StaffMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String body;
    private Instant createdAt;
    private Instant readAt;
}

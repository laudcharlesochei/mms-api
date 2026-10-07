package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Contact at the employer. Has no login. */
@Entity
@Table(name = "workplace_mentor")
@Getter @Setter @NoArgsConstructor
public class WorkplaceMentor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long employerId;
    private String fullName;
    private String email;
    private String phone;
    private boolean active = true;
}

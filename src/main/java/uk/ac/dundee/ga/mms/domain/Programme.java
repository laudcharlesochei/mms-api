package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A Graduate Apprenticeship degree route. */
@Entity
@Table(name = "programme")
@Getter @Setter @NoArgsConstructor
public class Programme {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private Long leadUserId;
    private boolean active = true;
}

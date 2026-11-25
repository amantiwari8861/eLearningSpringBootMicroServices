package in.iamandroid.authservice.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "trainees")
@Data
public class Trainee {

    @Id
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    private String qualification;

    private String institution;

    private Integer graduationYear;

    private String occupation;

    @Column(length = 1000)
    private String learningGoals;
}
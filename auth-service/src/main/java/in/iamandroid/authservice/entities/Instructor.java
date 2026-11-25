package in.iamandroid.authservice.entities;

import in.iamandroid.authservice.constants.InstructorStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Table(name = "instructors")
@Data
public class Instructor {

    @Id
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(length = 200)
    private String expertise;

    private Integer yearsOfExperience;

    private String qualification;

    private String designation;

    private String linkedInUrl;

    private String portfolioUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstructorStatus status = InstructorStatus.PENDING;
}
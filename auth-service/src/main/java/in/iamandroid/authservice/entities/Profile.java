package in.iamandroid.authservice.entities;

import in.iamandroid.authservice.constants.Gender;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Data
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String mobileNo;

    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String profileImageUrl;

    @Column(length = 2000)
    private String bio;

    private String city;

    private String state;

    private String country;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}

/**
 * Instant now = Instant.now();
 *
 * ZonedDateTime indianTime = now.atZone(
 *     ZoneId.of("Asia/Kolkata")
 * );
 *
 * System.out.println(indianTime);
 */
package in.iamandroid.order.entities;

import in.iamandroid.order.constants.EnrollmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {
                        "trainee_user_id",
                        "course_id"
                }
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "trainee_user_id", nullable = false)
    private UUID traineeUserId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private Instant enrolledAt;

    private Instant completedAt;

    private Instant expiresAt;

    @Version
    private Long version;
}
package in.iamandroid.order.dto;

import in.iamandroid.order.constants.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(

        UUID id,
        UUID traineeUserId,
        UUID courseId,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant completedAt,
        Instant expiresAt
) {
}

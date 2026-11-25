package in.iamandroid.order.dto;

import java.time.Instant;
import java.util.UUID;

public record ProgressResponse(

        UUID courseId,
        UUID lessonId,
        boolean completed,
        Instant completedAt,
        int completedLessons,
        int totalLessons,
        int percentage
) {
}

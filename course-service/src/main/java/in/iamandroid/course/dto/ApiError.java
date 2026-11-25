package in.iamandroid.course.dto;

import java.time.Instant;

public record ApiError(

        String code,
        String message,
        Instant timestamp
) {
}

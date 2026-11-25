package in.iamandroid.course.dto;

import in.iamandroid.course.constants.LessonType;

import java.util.UUID;

public record LessonResponse(

        UUID id,
        UUID moduleId,
        String title,
        String content,
        String videoUrl,
        String resourceUrl,
        Integer durationInSeconds,
        Integer position,
        boolean freePreview,
        LessonType type
) {
}

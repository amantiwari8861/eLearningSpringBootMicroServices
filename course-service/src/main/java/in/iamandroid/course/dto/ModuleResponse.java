package in.iamandroid.course.dto;

import java.util.List;
import java.util.UUID;

public record ModuleResponse(

        UUID id,
        UUID courseId,
        String title,
        String description,
        Integer position,
        List<LessonResponse> lessons
) {
}

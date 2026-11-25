package in.iamandroid.course.dto;

import in.iamandroid.course.constants.CourseLevel;
import in.iamandroid.course.constants.CourseStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseResponse(

        UUID id,
        UUID instructorUserId,
        UUID categoryId,
        String categorySlug,
        String title,
        String slug,
        String description,
        String thumbnailUrl,
        BigDecimal price,
        CourseLevel level,
        CourseStatus status,
        List<ModuleResponse> modules
) {
}

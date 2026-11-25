package in.iamandroid.course.dto;

import in.iamandroid.course.constants.LessonType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LessonRequest(

        @NotBlank @Size(max = 200) String title,

        String content,

        @Size(max = 2048) String videoUrl,

        @Size(max = 2048) String resourceUrl,

        @NotNull @Min(0) Integer durationInSeconds,

        @NotNull @Min(1) Integer position,

        Boolean freePreview,

        @NotNull LessonType type
) {
}

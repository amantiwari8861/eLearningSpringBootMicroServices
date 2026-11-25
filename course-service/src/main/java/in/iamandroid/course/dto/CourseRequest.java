package in.iamandroid.course.dto;

import in.iamandroid.course.constants.CourseLevel;
import in.iamandroid.course.constants.CourseStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseRequest(

        @NotNull UUID categoryId,

        @NotBlank @Size(max = 200) String title,

        @NotBlank @Size(max = 200) String slug,

        @Size(max = 10000) String description,

        @Size(max = 2048) String thumbnailUrl,

        @NotNull @DecimalMin("0.00") BigDecimal price,

        CourseLevel level,

        CourseStatus status
) {
}

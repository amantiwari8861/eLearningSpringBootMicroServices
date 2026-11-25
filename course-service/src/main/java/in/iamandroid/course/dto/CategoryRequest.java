package in.iamandroid.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank @Size(max = 100) String name,

        @NotBlank @Size(max = 150) String slug,

        @Size(max = 2000) String description,

        @Size(max = 2048) String imageUrl,

        Boolean active
) {
}

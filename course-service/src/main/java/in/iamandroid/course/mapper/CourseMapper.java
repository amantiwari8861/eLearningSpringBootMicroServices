package in.iamandroid.course.mapper;

import in.iamandroid.course.dto.CourseRequest;
import in.iamandroid.course.dto.CourseResponse;
import in.iamandroid.course.entities.Course;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = ModuleMapper.class
)
public interface CourseMapper {

    @Mapping(target = "category", ignore = true)
    @Mapping(target = "modules", ignore = true)
    @Mapping(target = "generated", source = "isGenerated")
    Course toEntity(CourseRequest request);

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categorySlug", source = "category.slug")
    @Mapping(target = "category", source = "category.name")
    @Mapping(target = "image", source = "thumbnailUrl")
    @Mapping(target = "isGenerated", source = "generated")
    CourseResponse toResponse(Course course);
}

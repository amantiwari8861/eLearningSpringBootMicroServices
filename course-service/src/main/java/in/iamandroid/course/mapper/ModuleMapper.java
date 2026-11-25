package in.iamandroid.course.mapper;

import in.iamandroid.course.dto.ModuleRequest;
import in.iamandroid.course.dto.ModuleResponse;
import in.iamandroid.course.entities.CourseModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = LessonMapper.class
)
public interface ModuleMapper {

    @Mapping(target = "course", ignore = true)
    @Mapping(target = "lessons", ignore = true)
    CourseModule toEntity(ModuleRequest request);

    @Mapping(target = "courseId", source = "course.id")
    ModuleResponse toResponse(CourseModule module);
}

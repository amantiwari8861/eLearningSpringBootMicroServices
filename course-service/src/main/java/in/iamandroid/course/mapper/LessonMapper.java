package in.iamandroid.course.mapper;

import in.iamandroid.course.dto.LessonRequest;
import in.iamandroid.course.dto.LessonResponse;
import in.iamandroid.course.entities.Lesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LessonMapper {

    @Mapping(target = "module", ignore = true)
    Lesson toEntity(LessonRequest request);

    @Mapping(target = "moduleId", source = "module.id")
    LessonResponse toResponse(Lesson lesson);
}

package in.iamandroid.course.mapper;

import in.iamandroid.course.dto.CategoryRequest;
import in.iamandroid.course.dto.CategoryResponse;
import in.iamandroid.course.entities.Category;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoryMapper {

    Category toEntity(CategoryRequest request);

    CategoryResponse toResponse(Category category);
}

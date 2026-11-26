package in.iamandroid.course.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.iamandroid.course.constants.CatalogItemType;
import in.iamandroid.course.constants.CourseLevel;
import in.iamandroid.course.constants.CourseStatus;
import in.iamandroid.course.dto.CourseRequest;
import in.iamandroid.course.entities.Category;
import in.iamandroid.course.entities.Course;
import in.iamandroid.course.mapper.CourseMapper;
import in.iamandroid.course.repository.CategoryRepository;
import in.iamandroid.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CatalogDataLoader implements ApplicationRunner {

    private static final UUID INSTRUCTOR_USER_ID = UUID.fromString("0f158e06-2d29-5d67-8c27-68b42ac92364");
    private static final String DATA_LOCATION = "catalog/learning-data.json";

    private final ObjectMapper objectMapper;
    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        ClassPathResource resource = new ClassPathResource(DATA_LOCATION);
        try (InputStream input = resource.getInputStream()) {
            LearningDataItem[] items = objectMapper.readValue(input, LearningDataItem[].class);
            for (LearningDataItem item : items) {
                String slug = item.slug().trim();
                if (courseRepository.existsBySlug(slug)) {
                    continue;
                }

                Category category = categoryRepository.findBySlug(item.categorySlug())
                        .orElseGet(() -> createCategory(item));

                CourseRequest request = new CourseRequest(
                        category.getId(),
                        item.title(),
                        slug,
                        item.description(),
                        item.imageUrl(),
                        item.price() == null ? BigDecimal.ZERO : item.price(),
                        item.level(),
                        CourseStatus.PUBLISHED,
                        itemType(item.type()),
                        item.subcategory(),
                        item.duration(),
                        item.rating(),
                        item.students(),
                        item.tags(),
                        item.courseUrl(),
                        item.featured(),
                        item.isGenerated()
                );

                Course course = courseMapper.toEntity(request);
                course.setInstructorUserId(INSTRUCTOR_USER_ID);
                course.setCategory(category);
                course.setSlug(slug);
                course.setTitle(item.title().trim());
                course.setFeatured(Boolean.TRUE.equals(item.featured()));
                course.setGenerated(Boolean.TRUE.equals(item.isGenerated()));
                if (course.getRating() == null) {
                    course.setRating(BigDecimal.ZERO);
                }
                if (course.getStudents() == null) {
                    course.setStudents(0L);
                }
                if (course.getTags() == null) {
                    course.setTags(new ArrayList<>());
                }
                if (course.getCourseUrl() == null || course.getCourseUrl().isBlank()) {
                    course.setCourseUrl("/courses/" + slug);
                }
                courseRepository.save(course);
            }
        }
    }

    private Category createCategory(LearningDataItem item) {
        Category category = new Category();
        category.setName(item.category());
        category.setSlug(item.categorySlug());
        category.setDescription("Catalogue category for " + item.category());
        category.setImageUrl(item.imageUrl());
        category.setActive(true);
        return categoryRepository.save(category);
    }

    private CatalogItemType itemType(String value) {
        return value == null || value.isBlank()
                ? CatalogItemType.COURSE
                : CatalogItemType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LearningDataItem(
            Integer sourceId,
            String type,
            String title,
            String slug,
            String category,
            String categorySlug,
            String subcategory,
            String description,
            String duration,
            BigDecimal price,
            CourseLevel level,
            BigDecimal rating,
            Long students,
            List<String> tags,
            String imageUrl,
            String courseUrl,
            Boolean featured,
            Boolean isGenerated
    ) {
    }
}

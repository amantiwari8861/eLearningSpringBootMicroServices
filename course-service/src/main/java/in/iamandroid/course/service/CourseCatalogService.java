package in.iamandroid.course.service;

import in.iamandroid.course.constants.CatalogItemType;
import in.iamandroid.course.constants.CourseLevel;
import in.iamandroid.course.constants.CourseStatus;
import in.iamandroid.course.dto.CategoryRequest;
import in.iamandroid.course.dto.CategoryResponse;
import in.iamandroid.course.dto.CourseRequest;
import in.iamandroid.course.dto.CourseResponse;
import in.iamandroid.course.dto.LessonCountResponse;
import in.iamandroid.course.dto.LessonRequest;
import in.iamandroid.course.dto.LessonResponse;
import in.iamandroid.course.dto.ModuleRequest;
import in.iamandroid.course.dto.ModuleResponse;
import in.iamandroid.course.entities.Category;
import in.iamandroid.course.entities.Course;
import in.iamandroid.course.entities.CourseModule;
import in.iamandroid.course.entities.Lesson;
import in.iamandroid.course.exception.DuplicateResourceException;
import in.iamandroid.course.exception.ResourceNotFoundException;
import in.iamandroid.course.mapper.CategoryMapper;
import in.iamandroid.course.mapper.CourseMapper;
import in.iamandroid.course.mapper.LessonMapper;
import in.iamandroid.course.mapper.ModuleMapper;
import in.iamandroid.course.repository.CategoryRepository;
import in.iamandroid.course.repository.CourseModuleRepository;
import in.iamandroid.course.repository.CourseRepository;
import in.iamandroid.course.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CourseCatalogService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository courseModuleRepository;
    private final LessonRepository lessonRepository;
    private final CategoryMapper categoryMapper;
    private final CourseMapper courseMapper;
    private final ModuleMapper moduleMapper;
    private final LessonMapper lessonMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        String slug = request.slug().trim();

        if (categoryRepository.existsByNameIgnoreCase(name) || categoryRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("A category with that name or slug already exists");
        }

        Category category = categoryMapper.toEntity(request);
        category.setName(name);
        category.setSlug(slug);
        if (request.active() != null) {
            category.setActive(request.active());
        }

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listCourses() {
        return courseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(courseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getCourse(String slug) {
        return courseRepository.findBySlug(slug)
                .map(courseMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + slug));
    }

    @Transactional(readOnly = true)
    public LessonCountResponse lessonCount(UUID courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course not found");
        }
        return new LessonCountResponse((int) lessonRepository.countByModuleCourseId(courseId));
    }

    public CourseResponse createCourse(CourseRequest request, UUID instructorUserId) {
        String slug = request.slug().trim();
        if (courseRepository.existsBySlug(slug)) {
            throw new DuplicateResourceException("A course with that slug already exists");
        }

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Course course = courseMapper.toEntity(request);
        course.setInstructorUserId(instructorUserId);
        course.setCategory(category);
        course.setSlug(slug);
        course.setTitle(request.title().trim());
        if (course.getStatus() == null) {
            course.setStatus(CourseStatus.DRAFT);
        }
        if (course.getLevel() == null) {
            course.setLevel(CourseLevel.BEGINNER);
        }
        if (course.getType() == null) {
            course.setType(CatalogItemType.COURSE);
        }
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

        return courseMapper.toResponse(courseRepository.save(course));
    }

    public ModuleResponse addModule(UUID courseId, ModuleRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        if (courseModuleRepository.existsByCourseIdAndPosition(courseId, request.position())) {
            throw new DuplicateResourceException("A module already uses that position in this course");
        }

        CourseModule module = moduleMapper.toEntity(request);
        course.addModule(module);
        module = courseModuleRepository.save(module);

        return moduleMapper.toResponse(module);
    }

    public LessonResponse addLesson(UUID courseId, UUID moduleId, LessonRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

        CourseModule module = course.getModules().stream()
                .filter(candidate -> candidate.getId().equals(moduleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Module not found in this course"));

        if (lessonRepository.existsByModuleIdAndPosition(moduleId, request.position())) {
            throw new DuplicateResourceException("A lesson already uses that position in this module");
        }

        Lesson lesson = lessonMapper.toEntity(request);
        if (request.freePreview() != null) {
            lesson.setFreePreview(request.freePreview());
        }
        module.addLesson(lesson);
        lesson = lessonRepository.save(lesson);

        return lessonMapper.toResponse(lesson);
    }
}

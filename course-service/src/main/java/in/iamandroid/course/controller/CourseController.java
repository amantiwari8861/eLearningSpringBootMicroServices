package in.iamandroid.course.controller;

import in.iamandroid.course.dto.CategoryRequest;
import in.iamandroid.course.dto.CategoryResponse;
import in.iamandroid.course.dto.CourseRequest;
import in.iamandroid.course.dto.CourseResponse;
import in.iamandroid.course.dto.LessonCountResponse;
import in.iamandroid.course.dto.LessonRequest;
import in.iamandroid.course.dto.LessonResponse;
import in.iamandroid.course.dto.ModuleRequest;
import in.iamandroid.course.dto.ModuleResponse;
import in.iamandroid.course.service.CourseCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseCatalogService catalogService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @GetMapping
    public List<CourseResponse> courses() {
        return catalogService.listCourses();
    }

    @GetMapping("/{slug}")
    public CourseResponse course(@PathVariable String slug) {
        return catalogService.getCourse(slug);
    }

    @GetMapping("/{courseId}/lesson-count")
    public LessonCountResponse lessonCount(@PathVariable UUID courseId) {
        return catalogService.lessonCount(courseId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @Valid @RequestBody CourseRequest request,
            @AuthenticationPrincipal String instructorId
    ) {
        return catalogService.createCourse(request, UUID.fromString(instructorId));
    }

    @PostMapping("/{courseId}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleResponse addModule(
            @PathVariable UUID courseId,
            @Valid @RequestBody ModuleRequest request
    ) {
        return catalogService.addModule(courseId, request);
    }

    @PostMapping("/{courseId}/modules/{moduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonResponse addLesson(
            @PathVariable UUID courseId,
            @PathVariable UUID moduleId,
            @Valid @RequestBody LessonRequest request
    ) {
        return catalogService.addLesson(courseId, moduleId, request);
    }
}

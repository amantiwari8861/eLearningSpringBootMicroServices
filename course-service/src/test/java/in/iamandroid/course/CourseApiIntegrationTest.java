package in.iamandroid.course;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.iamandroid.course.constants.CatalogItemType;
import in.iamandroid.course.constants.CourseLevel;
import in.iamandroid.course.constants.CourseStatus;
import in.iamandroid.course.constants.LessonType;
import in.iamandroid.course.dto.CategoryRequest;
import in.iamandroid.course.dto.CourseRequest;
import in.iamandroid.course.dto.LessonRequest;
import in.iamandroid.course.dto.ModuleRequest;
import in.iamandroid.course.security.IdentityHeaderAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseApiIntegrationTest {

    private static final String INSTRUCTOR_ID = "0f158e06-2d29-5d67-8c27-68b42ac92364";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicCatalogueIsReadable() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$[?(@.slug=='autocadtraining')].type").value(hasItem("COURSE")));
    }

    @Test
    void courseCreationRequiresIdentityHeaders() throws Exception {
        String suffix = UUID.randomUUID().toString();
        CategoryRequest request = categoryRequest(suffix);

        mockMvc.perform(post("/api/v1/courses/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void traineeCannotCreateCourseCategory() throws Exception {
        String suffix = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/courses/categories")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryRequest(suffix))))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorCanBuildCourseAndPublicCanReadIt() throws Exception {
        String suffix = UUID.randomUUID().toString();

        MvcResult categoryResult = mockMvc.perform(post("/api/v1/courses/categories")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryRequest(suffix))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("course-test-" + suffix))
                .andReturn();

        String categoryId = idFrom(categoryResult);
        String courseSlug = "integration-course-" + suffix;
        CourseRequest courseRequest = new CourseRequest(
                UUID.fromString(categoryId),
                "Integration Course " + suffix,
                courseSlug,
                "Created by the integration test",
                null,
                new BigDecimal("49.00"),
                CourseLevel.BEGINNER,
                CourseStatus.PUBLISHED,
                CatalogItemType.COURSE,
                "Backend",
                "3 Months",
                new BigDecimal("4.5"),
                10L,
                List.of("integration"),
                "/courses/" + courseSlug,
                true,
                true
        );

        MvcResult courseResult = mockMvc.perform(post("/api/v1/courses")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(courseRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructorUserId").value(INSTRUCTOR_ID))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.type").value("COURSE"))
                .andExpect(jsonPath("$.category").value("Course Test " + suffix))
                .andExpect(jsonPath("$.subcategory").value("Backend"))
                .andExpect(jsonPath("$.duration").value("3 Months"))
                .andExpect(jsonPath("$.rating").value(4.5))
                .andExpect(jsonPath("$.students").value(10))
                .andExpect(jsonPath("$.tags[0]").value("integration"))
                .andExpect(jsonPath("$.image").value(nullValue()))
                .andExpect(jsonPath("$.courseUrl").value("/courses/" + courseSlug))
                .andExpect(jsonPath("$.featured").value(true))
                .andExpect(jsonPath("$.isGenerated").value(true))
                .andReturn();

        String courseId = idFrom(courseResult);
        MvcResult moduleResult = mockMvc.perform(post("/api/v1/courses/" + courseId + "/modules")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ModuleRequest("Module One", "First module", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andReturn();

        String moduleId = idFrom(moduleResult);
        LessonRequest lessonRequest = new LessonRequest(
                "Lesson One", "Lesson body", null, null, 300, 1, true, LessonType.VIDEO);

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/modules/" + moduleId + "/lessons")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lessonRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moduleId").value(moduleId))
                .andExpect(jsonPath("$.freePreview").value(true));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/lesson-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonCount").value(1));

        mockMvc.perform(get("/api/v1/courses/" + courseSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(courseRequest.title()))
                .andExpect(jsonPath("$.modules[0].title").value("Module One"))
                .andExpect(jsonPath("$.modules[0].lessons[0].title").value("Lesson One"));
    }

    @Test
    void duplicateCategoryReturnsConflict() throws Exception {
        String suffix = UUID.randomUUID().toString();
        CategoryRequest request = categoryRequest(suffix);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/courses/categories")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/courses/categories")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, INSTRUCTOR_ID)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    private CategoryRequest categoryRequest(String suffix) {
        return new CategoryRequest(
                "Course Test " + suffix,
                "course-test-" + suffix,
                "Created by the integration test",
                null,
                true
        );
    }

    private String idFrom(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}

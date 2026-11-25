package in.iamandroid.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.iamandroid.order.dto.EnrollRequest;
import in.iamandroid.order.dto.ProgressRequest;
import in.iamandroid.order.security.IdentityHeaderAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${lms.course-service-url:http://localhost:8083}")
    private String courseServiceUrl;

    private MockRestServiceServer catalogServer;

    @BeforeEach
    void setUpCatalogServer() {
        catalogServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @AfterEach
    void resetCatalogServer() {
        catalogServer.reset();
    }

    @Test
    void enrollmentRequiresIdentityHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/orders/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollRequest(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void instructorCannotEnroll() throws Exception {
        mockMvc.perform(post("/api/v1/orders/enrollments")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, UUID.randomUUID())
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_INSTRUCTOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollRequest(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void traineeCanEnrollAndTrackProgress() throws Exception {
        String traineeId = UUID.randomUUID().toString();
        UUID courseId = UUID.randomUUID();
        UUID lessonOne = UUID.randomUUID();
        UUID lessonTwo = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders/enrollments")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollRequest(courseId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.traineeUserId").value(traineeId))
                .andExpect(jsonPath("$.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/orders/enrollments/me")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseId").value(courseId.toString()));

        stubLessonCount(courseId, 4);

        mockMvc.perform(post("/api/v1/orders/lessons/" + lessonOne + "/progress")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProgressRequest(courseId, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(lessonOne.toString()))
                .andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.totalLessons").value(4))
                .andExpect(jsonPath("$.percentage").value(25));

        mockMvc.perform(post("/api/v1/orders/lessons/" + lessonTwo + "/progress")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProgressRequest(courseId, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessons").value(2))
                .andExpect(jsonPath("$.percentage").value(50));

        mockMvc.perform(get("/api/v1/orders/courses/" + courseId + "/progress")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedLessons").value(2))
                .andExpect(jsonPath("$.percentage").value(50));

        mockMvc.perform(post("/api/v1/orders/lessons/" + lessonOne + "/progress")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProgressRequest(courseId, false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.percentage").value(25));
    }

    @Test
    void duplicateEnrollmentReturnsConflict() throws Exception {
        String traineeId = UUID.randomUUID().toString();
        UUID courseId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new EnrollRequest(courseId));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/orders/enrollments")
                            .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                            .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(attempt == 0 ? status().isCreated() : status().isConflict());
        }
    }

    @Test
    void progressRequiresAnEnrollment() throws Exception {
        String traineeId = UUID.randomUUID().toString();
        UUID courseId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders/lessons/" + UUID.randomUUID() + "/progress")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProgressRequest(courseId, true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void completingEveryLessonMarksEnrollmentCompleted() throws Exception {
        String traineeId = UUID.randomUUID().toString();
        UUID courseId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/orders/enrollments")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EnrollRequest(courseId))))
                .andExpect(status().isCreated())
                .andReturn();

        stubLessonCount(courseId, 1);

        mockMvc.perform(post("/api/v1/orders/lessons/" + lessonId + "/progress")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProgressRequest(courseId, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(100));

        mockMvc.perform(get("/api/v1/orders/enrollments/me")
                        .header(IdentityHeaderAuthenticationFilter.USER_ID_HEADER, traineeId)
                        .header(IdentityHeaderAuthenticationFilter.USER_ROLES_HEADER, "ROLE_TRAINEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    private void stubLessonCount(UUID courseId, int lessonCount) {
        catalogServer.expect(ExpectedCount.manyTimes(),
                        requestTo(courseServiceUrl + "/api/v1/courses/" + courseId + "/lesson-count"))
                .andRespond(withSuccess(
                        "{\"lessonCount\":" + lessonCount + "}",
                        MediaType.APPLICATION_JSON));
    }
}

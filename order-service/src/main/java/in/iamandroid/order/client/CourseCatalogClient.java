package in.iamandroid.order.client;

import in.iamandroid.order.dto.LessonCountResponse;
import in.iamandroid.order.exception.UpstreamUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseCatalogClient {

    private final RestTemplate restTemplate;

    @Value("${lms.course-service-url:http://localhost:8083}")
    private String courseServiceUrl;

    public int lessonCount(UUID courseId) {
        try {
            LessonCountResponse response = restTemplate.getForObject(
                    courseServiceUrl + "/api/v1/courses/{courseId}/lesson-count",
                    LessonCountResponse.class,
                    courseId);
            return response == null ? 0 : response.lessonCount();
        } catch (RestClientException exception) {
            throw new UpstreamUnavailableException("The course catalogue is unavailable for progress calculation");
        }
    }
}

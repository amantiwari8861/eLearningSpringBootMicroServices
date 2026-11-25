package in.iamandroid.order.controller;

import in.iamandroid.order.dto.EnrollRequest;
import in.iamandroid.order.dto.EnrollmentResponse;
import in.iamandroid.order.dto.ProgressRequest;
import in.iamandroid.order.dto.ProgressResponse;
import in.iamandroid.order.service.EnrollmentService;
import in.iamandroid.order.service.ProgressService;
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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class LearningController {

    private final EnrollmentService enrollmentService;
    private final ProgressService progressService;

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentResponse enroll(
            @Valid @RequestBody EnrollRequest request,
            @AuthenticationPrincipal String traineeId
    ) {
        return enrollmentService.enroll(UUID.fromString(traineeId), request);
    }

    @GetMapping("/enrollments/me")
    public List<EnrollmentResponse> myEnrollments(@AuthenticationPrincipal String traineeId) {
        return enrollmentService.myEnrollments(UUID.fromString(traineeId));
    }

    @PostMapping("/lessons/{lessonId}/progress")
    public ProgressResponse updateProgress(
            @PathVariable UUID lessonId,
            @Valid @RequestBody ProgressRequest request,
            @AuthenticationPrincipal String traineeId
    ) {
        return progressService.updateProgress(UUID.fromString(traineeId), lessonId, request);
    }

    @GetMapping("/courses/{courseId}/progress")
    public ProgressResponse courseProgress(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal String traineeId
    ) {
        return progressService.courseProgress(UUID.fromString(traineeId), courseId);
    }
}

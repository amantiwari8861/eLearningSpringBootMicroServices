package in.iamandroid.order.service;

import in.iamandroid.order.client.CourseCatalogClient;
import in.iamandroid.order.constants.EnrollmentStatus;
import in.iamandroid.order.dto.ProgressRequest;
import in.iamandroid.order.dto.ProgressResponse;
import in.iamandroid.order.entities.Enrollment;
import in.iamandroid.order.entities.LessonProgress;
import in.iamandroid.order.exception.ResourceNotFoundException;
import in.iamandroid.order.repository.EnrollmentRepository;
import in.iamandroid.order.repository.LessonProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProgressService {

    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CourseCatalogClient courseCatalogClient;

    public ProgressResponse updateProgress(
            UUID traineeUserId,
            UUID lessonId,
            ProgressRequest request
    ) {
        Enrollment enrollment = findEnrollment(traineeUserId, request.courseId());

        LessonProgress progress = lessonProgressRepository
                .findByEnrollmentIdAndLessonId(enrollment.getId(), lessonId)
                .orElseGet(LessonProgress::new);

        if (progress.getId() == null) {
            progress.setEnrollmentId(enrollment.getId());
            progress.setLessonId(lessonId);
        }

        progress.setCompleted(Boolean.TRUE.equals(request.completed()));
        progress.setCompletedAt(progress.isCompleted() ? Instant.now() : null);
        progress = lessonProgressRepository.save(progress);

        ProgressResponse response = buildProgress(enrollment, lessonId, progress);
        if (response.percentage() == 100 && enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(Instant.now());
            enrollmentRepository.save(enrollment);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public ProgressResponse courseProgress(UUID traineeUserId, UUID courseId) {
        return buildProgress(findEnrollment(traineeUserId, courseId), null, null);
    }

    private Enrollment findEnrollment(UUID traineeUserId, UUID courseId) {
        return enrollmentRepository.findByTraineeUserIdAndCourseId(traineeUserId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found for this trainee and course"));
    }

    private ProgressResponse buildProgress(
            Enrollment enrollment,
            UUID lessonId,
            LessonProgress progress
    ) {
        int totalLessons = courseCatalogClient.lessonCount(enrollment.getCourseId());
        long completedCount = lessonProgressRepository.countByEnrollmentIdAndCompletedTrue(enrollment.getId());
        int completedLessons = (int) Math.min(completedCount, totalLessons);
        int percentage = totalLessons == 0 ? 0 : (int) Math.round(completedLessons * 100.0 / totalLessons);

        boolean completed = progress == null
                ? totalLessons > 0 && percentage == 100
                : progress.isCompleted();
        Instant completedAt = progress == null ? null : progress.getCompletedAt();

        return new ProgressResponse(
                enrollment.getCourseId(),
                lessonId,
                completed,
                completedAt,
                completedLessons,
                totalLessons,
                percentage
        );
    }
}

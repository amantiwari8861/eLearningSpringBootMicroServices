package in.iamandroid.order.repository;

import in.iamandroid.order.entities.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    Optional<LessonProgress> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);

    List<LessonProgress> findByEnrollmentId(UUID enrollmentId);

    long countByEnrollmentIdAndCompletedTrue(UUID enrollmentId);
}

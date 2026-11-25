package in.iamandroid.order.repository;

import in.iamandroid.order.entities.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByTraineeUserIdAndCourseId(UUID traineeUserId, UUID courseId);

    boolean existsByTraineeUserIdAndCourseId(UUID traineeUserId, UUID courseId);

    List<Enrollment> findByTraineeUserIdOrderByEnrolledAtDesc(UUID traineeUserId);
}

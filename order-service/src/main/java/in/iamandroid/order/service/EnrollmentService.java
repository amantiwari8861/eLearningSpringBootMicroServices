package in.iamandroid.order.service;

import in.iamandroid.order.constants.EnrollmentStatus;
import in.iamandroid.order.dto.EnrollRequest;
import in.iamandroid.order.dto.EnrollmentResponse;
import in.iamandroid.order.entities.Enrollment;
import in.iamandroid.order.exception.DuplicateResourceException;
import in.iamandroid.order.mapper.EnrollmentMapper;
import in.iamandroid.order.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMapper enrollmentMapper;

    public EnrollmentResponse enroll(UUID traineeUserId, EnrollRequest request) {
        if (enrollmentRepository.existsByTraineeUserIdAndCourseId(traineeUserId, request.courseId())) {
            throw new DuplicateResourceException("This trainee is already enrolled in the course");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setTraineeUserId(traineeUserId);
        enrollment.setCourseId(request.courseId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setEnrolledAt(Instant.now());

        return enrollmentMapper.toResponse(enrollmentRepository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> myEnrollments(UUID traineeUserId) {
        return enrollmentRepository.findByTraineeUserIdOrderByEnrolledAtDesc(traineeUserId).stream()
                .map(enrollmentMapper::toResponse)
                .toList();
    }
}

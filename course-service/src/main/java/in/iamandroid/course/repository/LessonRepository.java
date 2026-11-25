package in.iamandroid.course.repository;

import in.iamandroid.course.entities.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    boolean existsByModuleIdAndPosition(UUID moduleId, Integer position);

    List<Lesson> findByModuleIdOrderByPositionAsc(UUID moduleId);
}

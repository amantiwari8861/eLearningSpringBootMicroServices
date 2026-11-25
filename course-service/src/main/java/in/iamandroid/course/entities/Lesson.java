package in.iamandroid.course.entities;

import in.iamandroid.course.constants.LessonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "lessons",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_module_lesson_position",
                columnNames = {"module_id", "position"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class Lesson extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    private String content;

    @Column(length = 2048)
    private String videoUrl;

    @Column(length = 2048)
    private String resourceUrl;

    @Column(nullable = false)
    private Integer durationInSeconds = 0;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private boolean freePreview = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LessonType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;
}
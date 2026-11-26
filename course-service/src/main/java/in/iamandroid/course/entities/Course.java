package in.iamandroid.course.entities;

import in.iamandroid.course.constants.CatalogItemType;
import in.iamandroid.course.constants.CourseLevel;
import in.iamandroid.course.constants.CourseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Course extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID instructorUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(length = 10000)
    private String description;

    @Column(length = 2048)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'COURSE'")
    private CatalogItemType type = CatalogItemType.COURSE;

    @Column(length = 150)
    private String subcategory;

    @Column(length = 100)
    private String duration;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 3, scale = 2)
    private BigDecimal rating;

    @Column
    private Long students;

    @Column(length = 2048)
    private String courseUrl;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean featured;

    @Column(name = "is_generated", nullable = false, columnDefinition = "boolean default false")
    private boolean generated;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "course_tags",
            joinColumns = @JoinColumn(name = "course_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_course_tag",
                    columnNames = {"course_id", "tag"}
            )
    )
    @Column(name = "tag", nullable = false, length = 80)
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private CourseLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status = CourseStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(
            mappedBy = "course",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("position ASC")
    private List<CourseModule> modules = new ArrayList<>();

    public void addModule(CourseModule module) {
        modules.add(module);
        module.setCourse(this);
    }

    public void removeModule(CourseModule module) {
        modules.remove(module);
        module.setCourse(null);
    }
}
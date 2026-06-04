package cn.bitloom.project.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("test_cases")
public class TestCaseEntity {

    @Id
    private Long id;

    @Column("project_id")
    private Long projectId;

    @Column("requirement_id")
    private Long requirementId;

    @Column("title")
    private String title;

    @Column("preconditions")
    private String preconditions;

    @Column("steps")
    private String steps;

    @Column("expected_result")
    private String expectedResult;

    @Column("status")
    private String status;

    @Column("submitter_id")
    private String submitterId;

    @Column("reviewer_id")
    private String reviewerId;

    @Column("review_comment")
    private String reviewComment;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}

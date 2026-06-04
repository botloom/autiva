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
@Table("bugs")
public class BugEntity {

    @Id
    private Long id;

    @Column("project_id")
    private Long projectId;

    @Column("title")
    private String title;

    @Column("description")
    private String description;

    @Column("severity")
    private String severity;

    @Column("status")
    private String status;

    @Column("reporter_id")
    private String reporterId;

    @Column("assignee_id")
    private String assigneeId;

    @Column("fix_description")
    private String fixDescription;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}

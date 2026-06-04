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
@Table("notifications")
public class NotificationEntity {

    @Id
    private Long id;

    @Column("type")
    private String type;

    @Column("project_id")
    private Long projectId;

    @Column("entity_type")
    private String entityType;

    @Column("entity_id")
    private Long entityId;

    @Column("title")
    private String title;

    @Column("content")
    private String content;

    @Column("status")
    private String status;

    @Column("target_client_id")
    private String targetClientId;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("sent_at")
    private LocalDateTime sentAt;
}

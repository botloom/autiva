package cn.bitloom.entity;

import com.alibaba.fastjson2.JSONObject;
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
@Table("user_services")
public class UserServiceEntity {

    @Id
    private Long id;

    @Column("client_id")
    private String clientId;

    @Column("project_name")
    private String projectName;

    @Column("subdomain")
    private String subdomain;

    @Column("runtime")
    private String runtime;

    @Column("status")
    private String status;

    @Column("container_id")
    private String containerId;

    @Column("sandbox_id")
    private String sandboxId;

    @Column("port")
    private Integer port;

    @Column("env_vars")
    private JSONObject envVars;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}

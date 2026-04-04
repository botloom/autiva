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
@Table("baas_resources")
public class BaasResourceEntity {

    @Id
    private Long id;

    @Column("service_id")
    private Long serviceId;

    @Column("resource_type")
    private String resourceType;

    @Column("resource_name")
    private String resourceName;

    @Column("connection_info")
    private JSONObject connectionInfo;

    @Column("created_at")
    private LocalDateTime createdAt;
}

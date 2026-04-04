-- Autiva 数据库初始化脚本
-- 创建用户服务表
CREATE TABLE IF NOT EXISTS user_services (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id VARCHAR(64) NOT NULL COMMENT '客户端ID',
    project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    subdomain VARCHAR(255) NOT NULL UNIQUE COMMENT '子域名',
    runtime VARCHAR(32) NOT NULL COMMENT '运行时: node, python, java',
    status VARCHAR(32) NOT NULL DEFAULT 'creating' COMMENT '状态: creating, running, stopped, error',
    container_id VARCHAR(128) COMMENT '容器ID',
    sandbox_id VARCHAR(128) COMMENT 'OpenSandbox沙箱ID',
    port INT COMMENT '服务端口',
    env_vars JSON COMMENT '环境变量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_client_id (client_id),
    INDEX idx_subdomain (subdomain),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户服务表';

-- 创建 BaaS 资源表
CREATE TABLE IF NOT EXISTS baas_resources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_id BIGINT NOT NULL COMMENT '关联的用户服务ID',
    resource_type VARCHAR(32) NOT NULL COMMENT '资源类型: mysql, redis, mongodb, minio',
    resource_name VARCHAR(128) NOT NULL COMMENT '资源名称',
    connection_info JSON NOT NULL COMMENT '连接信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (service_id) REFERENCES user_services(id) ON DELETE CASCADE,
    INDEX idx_service_id (service_id),
    INDEX idx_resource_type (resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BaaS资源表';

-- 创建服务日志表
CREATE TABLE IF NOT EXISTS service_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_id BIGINT NOT NULL COMMENT '关联的用户服务ID',
    log_level VARCHAR(16) NOT NULL COMMENT '日志级别',
    message TEXT NOT NULL COMMENT '日志内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (service_id) REFERENCES user_services(id) ON DELETE CASCADE,
    INDEX idx_service_id (service_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务日志表';

-- 创建部署记录表
CREATE TABLE IF NOT EXISTS deployment_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_id BIGINT NOT NULL COMMENT '关联的用户服务ID',
    version VARCHAR(32) NOT NULL COMMENT '版本号',
    code_hash VARCHAR(64) COMMENT '代码哈希',
    status VARCHAR(32) NOT NULL COMMENT '部署状态',
    error_message TEXT COMMENT '错误信息',
    deployed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (service_id) REFERENCES user_services(id) ON DELETE CASCADE,
    INDEX idx_service_id (service_id),
    INDEX idx_deployed_at (deployed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署记录表';

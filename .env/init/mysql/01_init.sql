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

-- ========================================
-- 项目管理系统表
-- ========================================

-- 项目表
CREATE TABLE IF NOT EXISTS projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '项目名称',
    description TEXT COMMENT '项目描述',
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING, IN_PROGRESS, REVIEW, COMPLETED, ARCHIVED',
    owner_id VARCHAR(50) NOT NULL COMMENT '项目所有者ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    INDEX idx_owner_id (owner_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- 需求表
CREATE TABLE IF NOT EXISTS requirements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    title VARCHAR(200) NOT NULL COMMENT '需求标题',
    description TEXT NOT NULL COMMENT '需求描述',
    priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级: LOW, MEDIUM, HIGH, CRITICAL',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, IMPLEMENTING, DONE',
    submitter_id VARCHAR(50) NOT NULL COMMENT '提交者ID',
    reviewer_id VARCHAR(50) COMMENT '评审者ID',
    review_comment TEXT COMMENT '评审意见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_project_id (project_id),
    INDEX idx_status (status),
    INDEX idx_submitter_id (submitter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求表';

-- 设计方案表
CREATE TABLE IF NOT EXISTS design_proposals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    requirement_id BIGINT COMMENT '关联需求ID',
    title VARCHAR(200) NOT NULL COMMENT '方案标题',
    content TEXT NOT NULL COMMENT '方案内容',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED',
    submitter_id VARCHAR(50) NOT NULL COMMENT '提交者ID',
    reviewer_id VARCHAR(50) COMMENT '评审者ID',
    review_comment TEXT COMMENT '评审意见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (requirement_id) REFERENCES requirements(id) ON DELETE SET NULL,
    INDEX idx_project_id (project_id),
    INDEX idx_requirement_id (requirement_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设计方案表';

-- 测试用例表
CREATE TABLE IF NOT EXISTS test_cases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    requirement_id BIGINT COMMENT '关联需求ID',
    title VARCHAR(200) NOT NULL COMMENT '用例标题',
    preconditions TEXT COMMENT '前置条件',
    steps TEXT NOT NULL COMMENT '测试步骤',
    expected_result TEXT NOT NULL COMMENT '预期结果',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED',
    submitter_id VARCHAR(50) NOT NULL COMMENT '提交者ID',
    reviewer_id VARCHAR(50) COMMENT '评审者ID',
    review_comment TEXT COMMENT '评审意见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (requirement_id) REFERENCES requirements(id) ON DELETE SET NULL,
    INDEX idx_project_id (project_id),
    INDEX idx_requirement_id (requirement_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例表';

-- Bug表
CREATE TABLE IF NOT EXISTS bugs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    title VARCHAR(200) NOT NULL COMMENT 'Bug标题',
    description TEXT NOT NULL COMMENT 'Bug描述',
    severity VARCHAR(16) NOT NULL DEFAULT 'MINOR' COMMENT '严重程度: TRIVIAL, MINOR, MAJOR, CRITICAL, BLOCKER',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN, ASSIGNED, FIXING, FIXED, VERIFIED, CLOSED, REOPENED',
    reporter_id VARCHAR(50) NOT NULL COMMENT '报告者ID',
    assignee_id VARCHAR(50) COMMENT '处理者ID',
    fix_description TEXT COMMENT '修复描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_project_id (project_id),
    INDEX idx_status (status),
    INDEX idx_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bug表';

-- 通知队列表（Web→Agent 通知通道）
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(50) NOT NULL COMMENT '通知类型: REQUIREMENT_SUBMITTED, REQUIREMENT_APPROVED, BUG_SUBMITTED, BUG_ASSIGNED, REVIEW_REQUEST, IMPLEMENT_DONE',
    project_id BIGINT NOT NULL COMMENT '关联项目ID',
    entity_type VARCHAR(32) NOT NULL COMMENT '实体类型: REQUIREMENT, DESIGN_PROPOSAL, TEST_CASE, BUG',
    entity_id BIGINT NOT NULL COMMENT '实体ID',
    title VARCHAR(200) NOT NULL COMMENT '通知标题',
    content TEXT COMMENT '通知内容',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, SENT, ACKNOWLEDGED',
    target_client_id VARCHAR(50) COMMENT '目标客户端ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL COMMENT '发送时间',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_status (status),
    INDEX idx_target_client_id (target_client_id),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知队列表';

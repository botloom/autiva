-- PostgreSQL 初始化脚本
CREATE TABLE IF NOT EXISTS user_services (
    id SERIAL PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    project_name VARCHAR(128) NOT NULL,
    subdomain VARCHAR(255) NOT NULL UNIQUE,
    runtime VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'creating',
    container_id VARCHAR(128),
    sandbox_id VARCHAR(128),
    port INTEGER,
    env_vars JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_services_client_id ON user_services(client_id);
CREATE INDEX idx_user_services_subdomain ON user_services(subdomain);
CREATE INDEX idx_user_services_status ON user_services(status);

# BaaS 包

## 概述
本包实现了 Backend as a Service 功能，为用户服务提供数据库、缓存、存储等后端资源。支持实际创建 MySQL 数据库、MongoDB 数据库和用户、MinIO 存储桶，Redis 使用 key prefix 隔离，并将连接信息注入沙箱环境变量。

## 核心类

### BaasManager
BaaS 资源管理器，负责创建和管理各类后端资源。

**依赖：**
- `BaasProperties`: BaaS 配置属性（替代硬编码）
- `mongodb-driver-sync`: MongoDB 同步驱动，用于创建数据库和用户
- `minio`: MinIO Java SDK，用于创建存储桶

**支持的资源类型：**
- `mysql`: MySQL 数据库（实际执行 CREATE DATABASE/USER）
- `redis`: Redis 命名空间（使用 key prefix 隔离，不生成随机密码）
- `mongodb`: MongoDB 数据库（实际执行 createUser 命令创建数据库和用户）
- `minio`: MinIO 对象存储桶（实际执行 makeBucket，使用管理员凭证）
- `rabbitmq`: RabbitMQ 队列

**核心方法：**
- `createMysqlDatabase(serviceId)`: 创建 MySQL 数据库（实际执行 CREATE DATABASE/USER）
- `createRedisNamespace(serviceId)`: 创建 Redis 命名空间（key prefix 隔离）
- `createMongodbDatabase(serviceId)`: 创建 MongoDB 数据库（实际执行 createUser 命令）
- `createMinioBucket(serviceId)`: 创建 MinIO 存储桶（实际执行 makeBucket）
- `createRabbitmqQueue(serviceId)`: 创建 RabbitMQ 队列
- `createAllResources(serviceId)`: 创建所有资源（带容错，单个资源失败不阻断）
- `resourcesToEnvVars(resources)`: 将 BaaS 资源转换为环境变量

**资源命名规则：**
- MySQL 数据库：`db_{serviceId}`
- Redis key prefix：`{serviceId}:`
- MongoDB 数据库：`{serviceId}`，用户：`user_{serviceId}`
- MinIO bucket：`{serviceId}`（下划线替换为连字符）

**连接信息：**

| 资源类型 | 返回字段 |
|---------|---------|
| MySQL | host, port, database, username, password, jdbcUrl |
| Redis | host, port, password（配置值，可为空）, keyPrefix, url |
| MongoDB | host, port, database, username, password, uri |
| MinIO | endpoint, bucket, accessKey（管理员凭证）, secretKey（管理员凭证）, region |

**环境变量映射：**

| 资源类型 | 环境变量 |
|---------|---------|
| MySQL | DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USER, DATABASE_PASSWORD, DATABASE_URL, MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD |
| Redis | REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_URL, REDIS_KEY_PREFIX |
| MongoDB | MONGODB_HOST, MONGODB_PORT, MONGODB_DATABASE, MONGODB_USER, MONGODB_PASSWORD, MONGODB_URI |
| MinIO | MINIO_ENDPOINT, MINIO_BUCKET, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_REGION |

### BaasResource
BaaS 资源记录类。

**字段：**
- `type`: 资源类型
- `name`: 资源名称
- `connectionInfo`: 连接信息 (JSON)

## 容错设计
- 所有资源创建失败时，仅生成连接信息配置，不阻断部署流程
- `createAllResources()` 使用 `onErrorResume` 确保单个资源失败不影响其他资源
- 使用 `log.warn` 记录失败信息，不影响整体服务

## 配置

通过 `BaasProperties` 配置类管理，前缀 `baas`：

```yaml
baas:
  mysql:
    host: localhost
    port: 3306
    username: autiva
    password: autiva_2024
  redis:
    host: localhost
    port: 6379
    password:              # Docker 环境下可为空
  mongodb:
    host: localhost
    port: 27017
    username:              # MongoDB 管理员用户名（可选）
    password:              # MongoDB 管理员密码（可选）
  minio:
    endpoint: http://localhost:9000
    access-key: autiva_admin
    secret-key: autiva_minio_2024
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

Docker 环境（application-docker.yml）：
```yaml
baas:
  mysql:
    host: autiva-mysql
  redis:
    host: autiva-redis
    password:
  mongodb:
    host: autiva-mongodb
  minio:
    endpoint: http://autiva-minio:9000
```

## 注意事项
1. MySQL 创建使用 JDBC 直连，需要 mysql-connector-j 依赖
2. MongoDB 创建使用 mongodb-driver-sync，通过 admin 数据库执行 createUser 命令
3. MinIO 创建使用 MinIO Java SDK，需要 minio 依赖；返回管理员凭证供应用使用
4. Redis 使用 key prefix 隔离，不生成随机密码，使用配置中的密码（Docker 环境下为空）
5. 密码自动生成（MySQL、MongoDB 用户密码），确保安全
6. 资源名称基于 serviceId 生成，确保唯一性
7. 环境变量会写入沙箱的 `/app/.env` 文件

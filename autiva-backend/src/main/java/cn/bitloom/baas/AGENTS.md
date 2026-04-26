# BaaS 包

## 概述
本包实现了 Backend as a Service 功能，为用户服务提供数据库、缓存、存储等后端资源。支持实际创建 MySQL 数据库和 MinIO 存储桶，并将连接信息注入沙箱环境变量。

## 核心类

### BaasManager
BaaS 资源管理器，负责创建和管理各类后端资源。

**依赖：**
- `BaasProperties`: BaaS 配置属性（替代硬编码）

**支持的资源类型：**
- `mysql`: MySQL 数据库（实际创建数据库和用户）
- `redis`: Redis 命名空间
- `mongodb`: MongoDB 数据库
- `minio`: MinIO 对象存储桶（实际创建桶）
- `rabbitmq`: RabbitMQ 队列

**核心方法：**
- `createMysqlDatabase(serviceId)`: 创建 MySQL 数据库（实际执行 CREATE DATABASE/USER）
- `createRedisNamespace(serviceId)`: 创建 Redis 命名空间
- `createMongodbDatabase(serviceId)`: 创建 MongoDB 数据库
- `createMinioBucket(serviceId)`: 创建 MinIO 存储桶（实际执行 makeBucket）
- `createRabbitmqQueue(serviceId)`: 创建 RabbitMQ 队列
- `createAllResources(serviceId)`: 创建所有资源
- `resourcesToEnvVars(resources)`: 将 BaaS 资源转换为环境变量（新增）

**环境变量映射（新增）：**

| 资源类型 | 环境变量 |
|---------|---------|
| MySQL | DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USER, DATABASE_PASSWORD, DATABASE_URL, MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD |
| Redis | REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_URL |
| MongoDB | MONGODB_HOST, MONGODB_PORT, MONGODB_DATABASE, MONGODB_USER, MONGODB_PASSWORD, MONGODB_URI |
| MinIO | MINIO_ENDPOINT, MINIO_BUCKET, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_REGION |

### BaasResource
BaaS 资源记录类。

**字段：**
- `type`: 资源类型
- `name`: 资源名称
- `connectionInfo`: 连接信息 (JSON)

## 容错设计
- MySQL 和 MinIO 创建失败时，仅生成连接信息配置，不阻断部署流程
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
    password: autiva_redis_2024
  mongodb:
    host: localhost
    port: 27017
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

## 注意事项
1. MySQL 创建使用 JDBC 直连，需要 mysql-connector-j 依赖
2. MinIO 创建使用 MinIO Java SDK，需要 minio 依赖
3. 密码自动生成，确保安全
4. 资源名称基于 serviceId 生成，确保唯一性
5. 环境变量会写入沙箱的 `/app/.env` 文件

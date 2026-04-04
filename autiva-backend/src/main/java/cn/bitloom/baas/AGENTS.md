# BaaS 包

## 概述
本包实现了 Backend as a Service 功能，为用户服务提供数据库、缓存、存储等后端资源。

## 核心类

### BaasManager
BaaS 资源管理器，负责创建和管理各类后端资源。

**支持的资源类型：**
- `mysql`: MySQL 数据库
- `redis`: Redis 命名空间
- `mongodb`: MongoDB 数据库
- `minio`: MinIO 对象存储桶
- `rabbitmq`: RabbitMQ 队列

**核心方法：**
- `createMysqlDatabase(serviceId)`: 创建 MySQL 数据库
- `createRedisNamespace(serviceId)`: 创建 Redis 命名空间
- `createMongodbDatabase(serviceId)`: 创建 MongoDB 数据库
- `createMinioBucket(serviceId)`: 创建 MinIO 存储桶
- `createRabbitmqQueue(serviceId)`: 创建 RabbitMQ 队列
- `createAllResources(serviceId)`: 创建所有资源

### BaasResource
BaaS 资源记录类。

**字段：**
- `type`: 资源类型
- `name`: 资源名称
- `connectionInfo`: 连接信息 (JSON)

## 连接信息格式

### MySQL
```json
{
    "host": "autiva-mysql",
    "port": 3306,
    "database": "db_xxx",
    "username": "user_xxx",
    "password": "xxx",
    "jdbcUrl": "jdbc:mysql://autiva-mysql:3306/db_xxx"
}
```

### Redis
```json
{
    "host": "autiva-redis",
    "port": 6379,
    "password": "xxx",
    "namespace": "ns_xxx",
    "url": "redis://:xxx@autiva-redis:6379/ns_xxx"
}
```

### MongoDB
```json
{
    "host": "autiva-mongodb",
    "port": 27017,
    "database": "db_xxx",
    "username": "user_xxx",
    "password": "xxx",
    "uri": "mongodb://user_xxx:xxx@autiva-mongodb:27017/db_xxx"
}
```

### MinIO
```json
{
    "endpoint": "http://autiva-minio:9000",
    "bucket": "bucket-xxx",
    "accessKey": "ak_xxx",
    "secretKey": "sk_xxx",
    "region": "us-east-1"
}
```

## 配置

```yaml
baas:
  mysql:
    host: localhost
    port: 3306
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
```

## 注意事项
1. 需要先启动对应的 BaaS 服务容器
2. 密码自动生成，确保安全
3. 资源名称基于 serviceId 生成，确保唯一性

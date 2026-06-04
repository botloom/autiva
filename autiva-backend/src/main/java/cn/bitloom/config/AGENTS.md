# Config 包

## 概述
本包提供配置属性类。

## 核心类

### GatewayProperties
网关配置属性。

**配置项：**
- `base-domain`: 基础域名 (如 autiva.dev)
- `default-target`: 默认路由目标

**配置前缀：** `gateway`

### BaasProperties
BaaS 服务配置属性（新增）。

**配置项：**
- `mysql.host`: MySQL 主机
- `mysql.port`: MySQL 端口
- `mysql.username`: MySQL 管理员用户名
- `mysql.password`: MySQL 管理员密码
- `redis.host`: Redis 主机
- `redis.port`: Redis 端口
- `redis.password`: Redis 密码
- `mongodb.host`: MongoDB 主机
- `mongodb.port`: MongoDB 端口
- `minio.endpoint`: MinIO 端点
- `minio.access-key`: MinIO 管理员 Access Key
- `minio.secret-key`: MinIO 管理员 Secret Key
- `rabbitmq.host`: RabbitMQ 主机
- `rabbitmq.port`: RabbitMQ 端口
- `rabbitmq.username`: RabbitMQ 用户名
- `rabbitmq.password`: RabbitMQ 密码

**配置前缀：** `baas`

### WebFluxConfig
WebFlux 配置类，实现 WebFluxConfigurer。

**功能：**
- 静态资源处理：将 `/**` 映射到 `classpath:/static/`，用于服务 autiva-web 构建产物
- CORS 配置：允许所有来源访问 `/api/**` 端点，支持开发时跨域调用

## 配置示例

```yaml
gateway:
  base-domain: autiva.dev
  default-target: http://localhost:3000

baas:
  mysql:
    host: localhost
    port: 3306
    username: autiva
    password: autiva_2024
```

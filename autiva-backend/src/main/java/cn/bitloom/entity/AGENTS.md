# Entity 包

## 概述
本包定义了数据库实体类，使用 Spring Data R2DBC 进行响应式数据库操作。

## 核心类

### UserServiceEntity
用户服务实体，存储用户部署的服务信息。

**字段：**
- `id`: 主键
- `clientId`: 客户端 ID
- `projectName`: 项目名称
- `subdomain`: 子域名 (唯一)
- `runtime`: 运行时 (node/python/java)
- `status`: 状态 (creating/running/stopped/error)
- `containerId`: 容器 ID
- `sandboxId`: OpenSandbox 沙箱 ID
- `port`: 服务端口
- `envVars`: 环境变量 (JSON)
- `createdAt`: 创建时间
- `updatedAt`: 更新时间

### BaasResourceEntity
BaaS 资源实体，存储用户服务的后端资源信息。

**字段：**
- `id`: 主键
- `serviceId`: 关联的用户服务 ID
- `resourceType`: 资源类型
- `resourceName`: 资源名称
- `connectionInfo`: 连接信息 (JSON)
- `createdAt`: 创建时间

## 表结构

参见 `init/mysql/01_init.sql`

## 注意事项
1. 使用 R2DBC 进行响应式操作
2. JSON 字段使用 FastJSON 序列化
3. 子域名需要唯一索引

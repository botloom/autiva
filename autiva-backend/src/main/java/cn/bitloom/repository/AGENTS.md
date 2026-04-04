# Repository 包

## 概述
本包定义了响应式数据访问层，使用 Spring Data R2DBC。

## 核心接口

### UserServiceRepository
用户服务数据访问接口。

**自定义方法：**
- `findByClientId(clientId)`: 按客户端 ID 查询
- `findBySubdomain(subdomain)`: 按子域名查询
- `existsBySubdomain(subdomain)`: 检查子域名是否存在
- `findByStatus(status)`: 按状态查询

### BaasResourceRepository
BaaS 资源数据访问接口。

**自定义方法：**
- `findByServiceId(serviceId)`: 按服务 ID 查询资源
- `findByServiceIdAndResourceType(serviceId, resourceType)`: 按服务 ID 和资源类型查询

## 使用示例

```java
userServiceRepository.findByClientId(clientId)
    .filter(service -> service.getProjectName().equals(projectName))
    .next()
    .flatMap(service -> {
        // 处理服务
    });
```

## 注意事项
1. 所有方法返回 Mono 或 Flux
2. 使用响应式编程模式
3. 不支持阻塞操作

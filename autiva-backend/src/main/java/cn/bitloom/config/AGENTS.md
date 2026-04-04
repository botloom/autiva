# Config 包

## 概述
本包提供配置属性类。

## 核心类

### GatewayProperties
网关配置属性。

**配置项：**
- `base-domain`: 基础域名 (如 autiva.dev)
- `default-target`: 默认路由目标

## 配置示例

```yaml
gateway:
  base-domain: autiva.dev
  default-target: http://localhost:3000
```

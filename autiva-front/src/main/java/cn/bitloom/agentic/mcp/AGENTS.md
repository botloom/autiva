# MCP 包

## 概述
本包实现了 MCP (Model Context Protocol) 客户端管理，支持多种传输协议连接外部 MCP 服务器。

## 核心类

### McpServer
MCP 服务器配置实体。

**字段：**
- `name`: 服务器名称
- `transportType`: 传输类型（STDIO/SSE/STREAMABLE_HTTP）
- `host`: 主机地址
- `port`: 端口号
- `command`: STDIO 模式的命令
- `args`: 命令参数列表
- `env`: 环境变量映射
- `url`: HTTP 模式的 URL
- `endpoint`: HTTP 端点
- `sseEndpoint`: SSE 端点

### McpTransportTypeEnum
传输协议枚举：
- `STDIO`: 标准输入输出模式
- `SSE`: Server-Sent Events 模式
- `STREAMABLE_HTTP`: 可流式 HTTP 模式

### McpManager
MCP 服务器管理器。

**功能：**
- 从配置文件加载 MCP 服务器配置
- 添加/更新/删除服务器配置
- 保存配置到文件
- `loadMcpServersConfig()`: 公开方法，支持手动触发加载配置

**配置文件路径：** `~/.autiva/mcp/mcp-servers.json`

**配置文件格式：**
```json
{
  "mcpServers": {
    "server-name": {
      "transportType": "STDIO",
      "command": "/path/to/command",
      "args": ["arg1", "arg2"],
      "env": {"KEY": "value"}
    }
  }
}
```

### McpClientConfig
MCP 客户端配置类，动态注册 McpAsyncClient Bean。

**注解：**
- `@Configuration(proxyBeanMethods = false)`: 禁用代理模式，避免Bean创建过早的警告

**实现方式：**
- 实现 `BeanDefinitionRegistryPostProcessor` 接口
- 在 `postProcessBeanDefinitionRegistry` 方法中动态注册 Bean
- 通过 `BeanFactory` 获取 `McpManager` 来读取服务器配置
- 手动调用 `mcpManager.loadMcpServersConfig()` 加载配置（因为@PostConstruct在此时可能未执行）

**功能：**
- 根据传输类型创建对应的客户端
- STDIO: 使用 StdioClientTransport
- SSE: 使用 WebFluxSseClientTransport
- STREAMABLE_HTTP: 使用 WebClientStreamableHttpTransport
- 自动初始化和关闭客户端

## 使用示例

### 添加 MCP 服务器
```java
McpServer server = McpServer.builder()
    .name("my-server")
    .transportType(McpTransportTypeEnum.STDIO)
    .command("/usr/local/bin/my-mcp-server")
    .args(List.of("--port", "8080"))
    .build();
mcpManager.addServer(server);
```

### 使用 MCP 客户端
```java
@Resource
private McpAsyncClient myServerClient;

public void useMcpTools() {
    ListToolsResult tools = myServerClient.listTools().block();
    // 使用工具...
}
```

## 设计模式
- 工厂模式：根据传输类型创建不同的客户端
- 注册器模式：动态注册 Spring Bean

## 注意事项
1. MCP 服务器配置保存在用户目录下
2. 客户端会自动初始化，确保服务器可用
3. 不同传输类型需要不同的配置字段
4. 客户端会在应用关闭时自动清理

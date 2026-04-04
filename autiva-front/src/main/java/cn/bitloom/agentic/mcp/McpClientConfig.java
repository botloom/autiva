package cn.bitloom.agentic.mcp;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class McpClientConfig implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) registry;
        McpManager mcpManager = beanFactory.getBean(McpManager.class);
        mcpManager.loadMcpServersConfig();
        
        List<McpServer> mcpServerList = mcpManager.getMcpServers().values().stream().toList();
        for (McpServer mcpServer : mcpServerList) {
            McpAsyncClient mcpClient;
            switch (mcpServer.getTransportType()) {
                case STDIO:
                    ServerParameters serverParam = ServerParameters.builder(mcpServer.getCommand())
                            .args(mcpServer.getArgs())
                            .env(mcpServer.getEnv())
                            .build();
                    mcpClient = McpClient.async(new StdioClientTransport(serverParam, McpJsonMapper.createDefault()))
                            .build();
                    break;
                case SSE:
                    WebClient.Builder sseWebClientBuilder = WebClient.builder()
                            .baseUrl(mcpServer.getUrl());
                    WebFluxSseClientTransport sseClientTransport = WebFluxSseClientTransport.builder(sseWebClientBuilder)
                            .sseEndpoint(mcpServer.getSseEndpoint())
                            .jsonMapper(McpJsonMapper.createDefault())
                            .build();
                    mcpClient = McpClient.async(sseClientTransport)
                            .build();
                    break;
                case STREAMABLE_HTTP:
                    WebClient.Builder streamableHttpWebClientBuilder = WebClient.builder()
                            .baseUrl(mcpServer.getUrl());
                    WebClientStreamableHttpTransport streamableHttpTransport = WebClientStreamableHttpTransport.builder(streamableHttpWebClientBuilder)
                            .endpoint(mcpServer.getEndpoint())
                            .jsonMapper(McpJsonMapper.createDefault())
                            .build();
                    mcpClient = McpClient.async(streamableHttpTransport)
                            .build();
                    break;
                default:
                    throw new IllegalStateException("Unknown MCP Server Transport Type: " + mcpServer.getTransportType());
            }
            AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(McpAsyncClient.class, () -> mcpClient)
                    .setDestroyMethodName("close")
                    .setInitMethodName("initialize")
                    .getBeanDefinition();
            registry.registerBeanDefinition(mcpServer.getName(), beanDefinition);
            log.info("Registered MCP client bean: {}", mcpServer.getName());
        }
    }

}

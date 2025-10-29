package cn.bitloom.autiva.agentic.flow.config;

import cn.bitloom.autiva.agentic.exception.WorkFlowException;
import cn.bitloom.autiva.agentic.flow.graph.Graph;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流配置加载器，负责从JSON文件加载工作流配置
 */
@Component
public class WorkflowConfigLoader {

    private final ResourceLoader resourceLoader;
    private final Map<String, WorkflowConfig> workflowConfigCache = new ConcurrentHashMap<>();

    @Autowired
    public WorkflowConfigLoader(@Qualifier("webApplicationContext") ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 从资源路径加载工作流配置
     *
     * @param resourcePath 资源路径
     * @return WorkflowConfig 对象
     */
    public WorkflowConfig loadWorkflowConfig(String resourcePath) {
        if (workflowConfigCache.containsKey(resourcePath)) {
            return workflowConfigCache.get(resourcePath);
        }
        Resource resource = resourceLoader.getResource(resourcePath);
        try (InputStream inputStream = resource.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            String jsonContent = new String(bytes, StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(jsonContent);
            WorkflowConfig config = WorkflowConfig.builder()
                    .id(json.getString("id"))
                    .name(json.getString("name"))
                    .description(json.getString("description"))
                    .graph(Graph.createGraphFromJson(json.getJSONObject("graph")))
                    .build();
            workflowConfigCache.put(resourcePath, config);
            return config;
        } catch (IOException e) {
            throw new WorkFlowException("加载配置文件失败", e);
        }
    }

}
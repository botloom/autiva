package cn.bitloom.autiva.agentic.flow.graph;

import lombok.Data;

import java.util.Set;

/**
 * The type Vertex param.
 *
 * @author ningyu
 */
@Data
public class VertexParam {
    private String systemPrompt;
    private Integer retryCount;
    private Integer timeout;
    private Boolean enabled;
    private Set<String> toolSet;
    private Set<String> advisorSet;
}

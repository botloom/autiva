package cn.bitloom.agentic.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServer {

    private String name;
    private McpTransportTypeEnum transportType;
    private String host;
    private int port;
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private String url;
    private String endpoint;
    private String sseEndpoint;

}
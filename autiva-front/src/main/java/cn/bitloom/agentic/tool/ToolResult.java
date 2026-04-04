package cn.bitloom.agentic.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    private boolean success;
    private String message;
    private Object data;

    public static ToolResult success() {
        return ToolResult.builder()
                .success(true)
                .build();
    }

    public static ToolResult success(String message) {
        return ToolResult.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ToolResult success(String message, Object data) {
        return ToolResult.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static ToolResult failure(String message) {
        return ToolResult.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static ToolResult failure(String message, String error) {
        return ToolResult.builder()
                .success(false)
                .message(message)
                .message(message)
                .build();
    }

}

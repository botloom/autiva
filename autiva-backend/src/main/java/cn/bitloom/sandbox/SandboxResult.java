package cn.bitloom.sandbox;

public record SandboxResult(
    boolean success,
    String url,
    String message,
    String containerId
) {
    public static SandboxResult success(String url, String containerId) {
        return new SandboxResult(true, url, "Deployed successfully", containerId);
    }

    public static SandboxResult failure(String message) {
        return new SandboxResult(false, null, message, null);
    }
}

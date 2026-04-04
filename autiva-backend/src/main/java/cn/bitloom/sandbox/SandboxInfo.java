package cn.bitloom.sandbox;

public record SandboxInfo(
    String containerId,
    String projectName,
    String runtime,
    String subdomain,
    String status
) {}

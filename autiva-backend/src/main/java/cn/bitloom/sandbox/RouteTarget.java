package cn.bitloom.sandbox;

public record RouteTarget(
    String targetUrl,
    boolean isUserSandbox
) {}

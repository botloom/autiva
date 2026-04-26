package cn.bitloom.dto;

public record DeployResult(
        boolean success,
        String url,
        String message,
        String sandboxId,
        String subdomain
) {
    public static DeployResult success(String url, String sandboxId, String subdomain) {
        return new DeployResult(true, url, "Deployed successfully", sandboxId, subdomain);
    }

    public static DeployResult failure(String message) {
        return new DeployResult(false, null, message, null, null);
    }
}

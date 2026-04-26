package cn.bitloom.dto;

import java.util.List;
import java.util.Map;

public record DeployRequest(
        String clientId,
        String projectName,
        List<ProjectFile> files,
        String runtime,
        Map<String, String> envVars
) {}

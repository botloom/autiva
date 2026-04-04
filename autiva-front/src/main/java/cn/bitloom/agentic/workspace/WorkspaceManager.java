package cn.bitloom.agentic.workspace;

import cn.bitloom.constant.AppConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Component
public class WorkspaceManager {

    @PostConstruct
    public void init() {
        initConfigFile();
    }

    private void initConfigFile() {
        if (!Files.exists(AppConstants.Base.CONFIG_FILE)) {
            try {
                Files.createFile(AppConstants.Base.CONFIG_FILE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}

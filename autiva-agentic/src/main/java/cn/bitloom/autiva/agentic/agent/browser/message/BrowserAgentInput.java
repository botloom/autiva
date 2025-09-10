package cn.bitloom.autiva.agentic.agent.browser.message;

import lombok.Data;

import java.util.List;

/**
 * The type Browser agent input.
 *
 * @author bitloom
 */
@Data
public class BrowserAgentInput {
    private String sessionId;
    private String target;
    private List<BrowserAgentMemory> history;
    private BrowserStatus browser;

    /**
     * The type Browser status.
     */
    @Data
    public static class BrowserStatus {
        private String currentPage;
        private List<PageStatus> pageList;
        private List<PageStatus> dialogList;
        private String elementTree;
        private String event;
    }

    /**
     * The type Page status.
     */
    @Data
    public static class PageStatus {
        private String id;
        private String url;
        private Boolean isActive;
        private String type;
        private String message;
    }
}



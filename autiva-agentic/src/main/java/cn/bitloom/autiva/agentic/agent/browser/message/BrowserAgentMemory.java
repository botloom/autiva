package cn.bitloom.autiva.agentic.agent.browser.message;

import lombok.Data;

/**
 * The type Memory.
 */
@Data
public class BrowserAgentMemory {
    private String id;
    private String preStepAssessment;
    private String memory;
    private String nextStep;
    private String result;
}
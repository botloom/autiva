package cn.bitloom.autiva.agentic.agent.browser.message;

import lombok.Data;

/**
 * The type Output.
 *
 * @author bitloom
 */
@Data
public class BrowserAgentOutput {
    private String think;
    private String act;
    private String preStepAssessment;
    private String memory;
    private String nextStep;
    private String result;
}


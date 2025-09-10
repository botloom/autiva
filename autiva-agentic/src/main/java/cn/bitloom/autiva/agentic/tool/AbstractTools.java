package cn.bitloom.autiva.agentic.tool;

import cn.bitloom.autiva.agentic.core.AbstractAgent;

/**
 * The type Abstract tools.
 *
 * @param <T> the type parameter
 * @author bitloom
 */
public abstract class AbstractTools<T extends AbstractAgent> {

    /**
     * The Agent.
     */
    protected final T AGENT;


    /**
     * Instantiates a new Abstract tools.
     *
     * @param AGENT the agent
     */
    public AbstractTools(T AGENT) {
        this.AGENT = AGENT;
    }
}

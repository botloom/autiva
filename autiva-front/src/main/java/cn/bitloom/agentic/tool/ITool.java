package cn.bitloom.agentic.tool;

/**
 * The type Abstract tool.
 *
 * @author bitloom
 */
public interface ITool {
    /**
     * Is safe boolean.
     *
     * @param path the path
     * @return the boolean
     */
    default Boolean isSafe(String path) {
        return false;
    }
}

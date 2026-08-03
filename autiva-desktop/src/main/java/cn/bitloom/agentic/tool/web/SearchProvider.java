package cn.bitloom.agentic.tool.web;

import java.util.List;

/**
 * 搜索引擎策略接口，定义统一的搜索方法。
 */
public interface SearchProvider {

    List<WebSearchTool.SearchResult> search(String query, int count);

}

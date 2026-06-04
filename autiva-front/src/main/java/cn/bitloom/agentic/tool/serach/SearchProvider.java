package cn.bitloom.agentic.tool.serach;

import java.util.List;

public interface SearchProvider {

	List<WebSearchTool.SearchResult> search(String query, int count);

}

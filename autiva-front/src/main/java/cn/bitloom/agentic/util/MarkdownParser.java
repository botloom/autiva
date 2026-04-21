/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package cn.bitloom.agentic.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于解析带有可选YAML前置元数据的Markdown文档的解析器。
 * <p>
 * 此解析器从Markdown文档中提取YAML前置元数据（元数据）和内容。
 * 前置元数据由文档开头的三条横线（---）分隔，包含YAML格式的键值对。
 * <p>
 * 带有前置元数据的Markdown示例：
 *
 * <pre>{@code
 * ---
 * title: 我的文档
 * author: 张三
 * date: 2024-01-15
 * ---
 *
 * # 标题
 *
 * 文档内容在这里。
 * }</pre>
 * <p>
 * 该解析器支持：
 * <ul>
 * <li>由冒号分隔的键值对形式的前置元数据</li>
 * <li>带引号或不带引号的值（支持单引号和双引号）</li>
 * <li>没有前置元数据的文档（全部内容视为正文）</li>
 * <li>空或null的markdown输入</li>
 * </ul>
 *
 * @author Christian Tzolov
 */
public class MarkdownParser {

	/**
	 * 包含已解析的前置元数据键值对的映射。
	 */
	private final Map<String, Object> frontMatter;

	/**
	 * markdown文档的内容（前置元数据之后的所有内容）。
	 */
	private String content;

	/**
	 * 构造新的MarkdownParser并解析提供的markdown内容。解析markdown内容以提取
	 * 前置元数据和正文内容。
	 * <p>
	 * 前置元数据必须以"---"开头，以另一个"---"结尾。这两个分隔符之间的所有内容
	 * 被解析为前置元数据。结束分隔符之后的所有内容被视为正文内容。
	 * @param markdown 要解析的markdown字符串，可能包含由三条横线（---）分隔的前置元数据。可以为null或空。
	 */
	public MarkdownParser(String markdown) {

		frontMatter = new HashMap<>();
		content = "";

		if (markdown == null || markdown.isEmpty()) {
			return;
		}

		if (markdown.startsWith("---")) {
			int endIndex = markdown.indexOf("---", 3);

			if (endIndex != -1) {
				String frontMatterSection = markdown.substring(3, endIndex).trim();
				parseFrontMatter(frontMatterSection);

				content = markdown.substring(endIndex + 3).trim();
			}
			else {
				content = markdown;
			}
		}
		else {
			content = markdown;
		}

	}

	private void parseFrontMatter(String frontMatterSection) {
		String[] lines = frontMatterSection.split("\n");

		for (String line : lines) {
			line = line.trim();

			if (line.isEmpty()) {
				continue;
			}

			int colonIndex = line.indexOf(':');
			if (colonIndex > 0) {
				String key = line.substring(0, colonIndex).trim();
				String value = line.substring(colonIndex + 1).trim();

				value = removeQuotes(value);

				frontMatter.put(key, value);
			}
		}
	}

	private String removeQuotes(String value) {
		if (value.length() >= 2) {
			if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
				return value.substring(1, value.length() - 1);
			}
		}
		return value;
	}

	/**
	 * 返回已解析的前置元数据的副本映射。
	 * <p>
	 * 返回的映射包含从前置元数据部分提取的所有键值对。如果没有前置元数据
	 * 或输入为null/空，则返回空映射。
	 * @return 包含前置元数据键值对的新映射
	 */
	public Map<String, Object> getFrontMatter() {
		return new HashMap<>(frontMatter);
	}

	/**
	 * 返回markdown文档的内容部分。
	 * <p>
	 * 这是关闭前置元数据分隔符（---）之后的所有内容，已去除前导和尾随空白。
	 * 如果没有前置元数据，则返回整个文档。如果输入为null或空，则返回空字符串。
	 * @return markdown内容字符串
	 */
	public String getContent() {
		return content;
	}
}

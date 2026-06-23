package cn.bitloom.agentic.util;

/**
 * Token 估算工具类
 * 用于估算文本的 Token 数量，避免超出上下文窗口限制
 */
public class TokenEstimator {

	/**
	 * 平均每个 Token 对应的字符数（英文）
	 * Claude/GPT 模型通常 1 Token ≈ 4 字符（英文）
	 * 中文通常 1 Token ≈ 1.5-2 字符
	 * 这里使用保守估计：1 Token ≈ 3 字符
	 */
	private static final double CHARS_PER_TOKEN = 3.0;

	/**
	 * 估算文本的 Token 数量
	 * 使用简单的字符计数方法，适用于快速估算
	 *
	 * @param text 要估算的文本
	 * @return 估算的 Token 数量
	 */
	public static int estimateTokens(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		
		// 简单估算：字符数 / 平均每个 Token 的字符数
		// 这是一个粗略估计，实际 Token 数量需要使用分词器
		return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
	}

	/**
	 * 估算单行的 Token 数量
	 * 考虑行号前缀的额外 Token
	 *
	 * @param line 文本行（不含行号）
	 * @param lineNumber 行号
	 * @return 估算的 Token 数量
	 */
	public static int estimateLineTokens(String line, int lineNumber) {
		if (line == null) {
			return 0;
		}
		
		// 行号前缀格式："%6d\t" ≈ 7-8 字符
		int lineNumberTokens = 3; // 行号和制表符的 Token 数
		return lineNumberTokens + estimateTokens(line);
	}

	/**
	 * 检查文件大小是否在安全范围内
	 * 
	 * @param fileSizeBytes 文件大小（字节）
	 * @param maxSizeMB 最大允许的文件大小（MB）
	 * @return true 如果文件大小在安全范围内
	 */
	public static boolean isFileSizeSafe(long fileSizeBytes, int maxSizeMB) {
		long maxSizeBytes = (long) maxSizeMB * 1024 * 1024;
		return fileSizeBytes <= maxSizeBytes;
	}

	/**
	 * 计算基于 Token 预算的最大可读取字符数
	 * 
	 * @param tokenBudget Token 预算
	 * @return 最大可读取字符数
	 */
	public static int calculateMaxChars(int tokenBudget) {
		return (int) (tokenBudget * CHARS_PER_TOKEN);
	}
}
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
package cn.bitloom.agentic.tool;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

/**
 * 用于在AI智能体执行过程中向用户提问澄清问题的工具。
 *
 * <p>
 * 这是Claude Code的AskUserQuestion工具的Spring AI实现，使AI智能体能够在执行过程中
 * 收集用户偏好、澄清模糊指令，并获取关于实现选择的决策。
 *
 * <p>
 * <strong>线程安全：</strong>此类是线程安全的。该工具可以安全地被多个线程并发使用。
 * 但是，线程安全性取决于提供的{@link QuestionHandler}是否线程安全。如果处理器维护了
 * 可变的共享状态，调用者必须确保正确的同步。
 *
 * <p>
 *
 * 关于异常处理的说明：该工具会验证{@link QuestionHandler}返回的答案。如果答案无效
 * （例如，缺少问题的答案、值为null），将抛出{@link InvalidUserAnswerException}。
 * 此异常表示用户提供了无效输入。此类异常通常在验证答案映射后抛出，必须传播给用户，
 * 而不是AI智能体。
 * 需要设置<code>spring.ai.tools.throw-exception-on-error=cn.bitloom.agentic.tool.AskUserQuestionTool$InvalidUserAnswerException</code>
 * 以启用此行为。
 *
 * @author Christian Tzolov
 * @see <a href=
 * "https://platform.claude.com/docs/en/agent-sdk/user-input#question-format"> Claude
 * Agent SDK - 问题格式</a>
 */
public class AskUserQuestionTool {

	private static final Logger logger = LoggerFactory.getLogger(AskUserQuestionTool.class);

	/**
	 * 用于处理用户问题的函数式接口。
	 *
	 * <p>
	 * 实现应将问题呈现给用户，并返回以问题文本为键的答案映射。答案值应为选项标签
	 * （多选时为逗号分隔的标签）或自由文本。
	 *
	 * <p>
	 * <strong>线程安全：</strong>实现可能会被并发调用，如果维护了可变的共享状态，
	 * 则必须保证线程安全。
	 */
	@FunctionalInterface
	public interface QuestionHandler {
		Map<String, String> handle(List<Question> questions);
	}

	private final QuestionHandler questionHandler;


	private final boolean answersValidation;

	protected AskUserQuestionTool(QuestionHandler questionHandler,
			boolean answersValidation) {
		this.questionHandler = questionHandler;
		this.answersValidation = answersValidation;
	}

	/**
	 * 表示要向用户提出的单个问题
	 */
	public record Question(
			/*
			  要向用户提出的完整问题。应清晰、具体，并以问号结尾。
			  示例："我们应该使用哪个库来进行日期格式化？"
			 */
			@JsonPropertyDescription("要向用户提出的完整问题。应清晰、具体，并以问号结尾。示例：\"我们应该使用哪个库来进行日期格式化？\"") String question,

			/*
			  显示为标签/标记的极短标签（最多12个字符）。示例："认证方式"、"库"、"方案"
			 */
			@JsonPropertyDescription("显示为标签/标记的极短标签（最多12个字符）。示例：\"认证方式\"、\"库\"、\"方案\"") String header,

			/*
			  此问题的可用选项。必须有2-4个选项。每个选项应为不同的、互斥的选择
			  （除非启用了multiSelect）。
			 */
			@JsonPropertyDescription("此问题的可用选项。必须有2-4个选项。每个选项应为不同的、互斥的选择（除非启用了multiSelect）。") List<Option> options,

			/*
			  设为true以允许用户选择多个选项而不是仅选择一个。当选项不是互斥时使用。
			  如果为null则默认为false。
			 */
			@JsonPropertyDescription("设为true以允许用户选择多个选项而不是仅选择一个。当选项不是互斥时使用。如果为null则默认为false。") Boolean multiSelect) {

		public Question {
			if (question == null || question.isBlank()) {
				throw new IllegalArgumentException("问题文本不能为null或空白");
			}

			if (header == null || header.isBlank()) {
				throw new IllegalArgumentException("标题不能为null或空白");
			}
			if (header.length() > 12) {
				logger.warn("标题长度超过12个字符: {}", header);
			}

			if (options == null || options.size() < 2 || options.size() > 4) {
				logger.warn("选项数量必须在2到4之间，实际为: {}", options == null ? 0 : options.size());
			}

			if (multiSelect == null) {
				multiSelect = false;
			}

			options = List.copyOf(options);
		}

		/**
		 * 表示问题的单个选项/选择
		 */
		public record Option(
				/*
				  用户将看到并选择的此选项的显示文本。应简洁（1-5个词）并清楚地描述选择。
				 */
				@JsonPropertyDescription("用户将看到并选择的此选项的显示文本。应简洁（1-5个词）并清楚地描述选择。") String label,

				/*
				  解释此选项的含义或选择后会发生什么。有助于提供关于权衡或影响的上下文。
				 */
				@JsonPropertyDescription("解释此选项的含义或选择后会发生什么。有助于提供关于权衡或影响的上下文。") String description) {

			public Option {
				if (label == null || label.isBlank()) {
					throw new IllegalArgumentException("选项标签不能为null或空白");
				}
				if (description == null || description.isBlank()) {
					throw new IllegalArgumentException("选项描述不能为null或空白");
				}
			}
		}
	}

	@Tool(name = "AskUserQuestionTool",
			description = """
					当你在执行过程中需要向用户提问时使用此工具。此工具允许你：
					1. 收集用户偏好或需求
					2. 澄清模糊的指令
					3. 在工作中获取关于实现选择的决策
					4. 向用户提供关于方向的选择。

					使用说明：
					- 用户始终可以选择"其他"来提供自定义文本输入
					- 使用multiSelect: true允许为问题选择多个答案
					- 如果你推荐特定选项，请将其放在列表的第一个位置，并在标签末尾添加"（推荐）"
					""")
	public String askUserQuestion(
			@ToolParam(description = "要向用户提出的问题（1-4个问题）") List<Question> questions,
			@ToolParam(description = "由权限组件收集的用户答案",
					required = false) Map<String, String> answers) {

		this.validateQuestions(questions);

		logger.debug("向用户提问 {} 个问题", questions.size());
		if (logger.isTraceEnabled()) {
			questions.forEach(q -> logger.trace("问题: {}", q.question()));
		}

		Map<String, String> result = this.questionHandler.handle(questions);

		if (this.answersValidation) {
			this.validateAnswers(questions, result);
		}

		if (logger.isDebugEnabled() && result != null) {
			logger.debug("收到用户 {} 个答案", result.size());
		}

		return "用户已回答你的问题: " + JsonParser.toJson(result);
	}

	/**
	 * 根据以下规则验证问题列表：
	 * - 问题列表必须包含1-4个问题
	 * - 每个问题由其紧凑构造函数验证
	 * @param questions 要验证的问题列表
	 * @throws IllegalArgumentException 如果验证失败
	 */
	private void validateQuestions(List<Question> questions) {
		if (questions == null) {
			throw new IllegalArgumentException("问题列表不能为null");
		}

		if (questions.isEmpty() || questions.size() > 4) {
			throw new IllegalArgumentException("问题列表必须包含1-4个问题，实际为: " + questions.size());
		}

		for (int i = 0; i < questions.size(); i++) {
			Question question = questions.get(i);
			if (question == null) {
				throw new IllegalArgumentException("索引 " + i + " 处的问题为null");
			}
		}
	}

	/**
	 * 验证由{@link QuestionHandler}返回的答案映射。确保所有问题都有非null答案，
	 * 且映射键与问题文本匹配。
	 * @param questions 原始提出的问题
	 * @param answers 由处理器返回的答案映射
	 * @throws InvalidUserAnswerException 如果验证失败
	 */
	private void validateAnswers(List<Question> questions, Map<String, String> answers) {
		if (answers == null) {
			throw new InvalidUserAnswerException(
					"questionHandler返回了null。必须返回非null的Map<String, String>");
		}

		for (Question question : questions) {
			String questionText = question.question();

			if (!answers.containsKey(questionText)) {
				throw new InvalidUserAnswerException(
						"缺少问题的答案: \"" + questionText + "\"。所有问题都必须有答案。");
			}

			String answerValue = answers.get(questionText);
			if (answerValue == null) {
				throw new InvalidUserAnswerException("问题 \"" + questionText
						+ "\" 的答案为null。答案值不应为null（空字符串是可接受的）。");
			}
		}

		if (answers.size() > questions.size()) {
			for (String answerKey : answers.keySet()) {
				boolean foundMatch = questions.stream().anyMatch(q -> q.question().equals(answerKey));
				if (!foundMatch) {
					logger.warn("答案映射包含不匹配任何问题的意外键: \"{}\"",
							answerKey);
				}
			}
		}
	}

	/**
	 * 当用户提供的答案无效时抛出的异常。这些异常通常在验证答案映射后抛出，
	 * 必须传播给用户而不是AI智能体。
	 *
	 * 需要设置<code>spring.ai.tools.throw-exception-on-error=cn.bitloom.agentic.tool.AskUserQuestionTool$InvalidUserAnswerException</code>
	 * 以启用此行为。
	 */
	public static class InvalidUserAnswerException extends RuntimeException {

		public InvalidUserAnswerException(String message) {
			super(message);
		}

	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private QuestionHandler questionHandler;

		private boolean answersValidation = true;

		public Builder answersValidation(boolean answersValidation) {
			this.answersValidation = answersValidation;
			return this;
		}

		public Builder questionHandler(QuestionHandler questionHandler) {
			Assert.notNull(questionHandler, "questionHandler不能为null");
			this.questionHandler = questionHandler;
			return this;
		}

		public AskUserQuestionTool build() {
			Assert.notNull(questionHandler, "必须提供questionHandler");
			return new AskUserQuestionTool(questionHandler, answersValidation);
		}

	}

}

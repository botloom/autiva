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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import cn.bitloom.agentic.tool.AskUserQuestionTool.Question;
import cn.bitloom.agentic.tool.AskUserQuestionTool.Question.Option;
import cn.bitloom.agentic.tool.AskUserQuestionTool.QuestionHandler;

/**
 * @author Christian Tzolov
 */
public class CommandLineQuestionHandler implements QuestionHandler {

	@Override
	public Map<String, String> handle(List<Question> questions) {
		Map<String, String> answers = new HashMap<>();

		Scanner scanner = new Scanner(System.in);

		for (Question q : questions) {
			System.out.println("\n" + q.header() + ": " + q.question());

			List<Option> options = q.options();
			for (int i = 0; i < options.size(); i++) {
				Option opt = options.get(i);
				System.out.printf("  %d. %s - %s%n", i + 1, opt.label(), opt.description());
			}

			if (q.multiSelect()) {
				System.out.println("  （输入用逗号分隔的数字，或输入自定义文本）");
			}
			else {
				System.out.println("  （输入数字，或输入自定义文本）");
			}

			String response = scanner.nextLine().trim();
			answers.put(q.question(), parseResponse(response, options));
		}

		return answers;
	}

	private static String parseResponse(String response, List<Option> options) {
		try {
			String[] parts = response.split(",");
			List<String> labels = new ArrayList<>();
			for (String part : parts) {
				int index = Integer.parseInt(part.trim()) - 1;
				if (index >= 0 && index < options.size()) {
					labels.add(options.get(index).label());
				}
			}
			return labels.isEmpty() ? response : String.join(", ", labels);
		}
		catch (NumberFormatException e) {
			return response;
		}
	}

}

/*
* Copyright 2026 - 2026 the original author or authors.
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
package cn.bitloom.agentic.agent.subagent.a2a;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cn.bitloom.agentic.agent.subagent.SubagentDefinition;
import cn.bitloom.agentic.agent.subagent.SubagentExecutor;
import cn.bitloom.agentic.agent.subagent.TaskCall;

/**
 * 通过A2A协议执行任务，向远程代理发送消息。
 * 演示如何为远程代理通信实现{@link SubagentExecutor}。
 *
 * @author Christian Tzolov
 * @see <a href="https://google.github.io/A2A/">A2A协议规范</a>
 */
public class A2ASubagentExecutor implements SubagentExecutor {

	private static final Logger logger = LoggerFactory.getLogger(A2ASubagentExecutor.class);

	@Override
	public String getKind() {
		return A2ASubagentDefinition.KIND;
	}

	@Override
	public String execute(TaskCall taskCall, SubagentDefinition subagent) {

		AgentCard agentCard = ((A2ASubagentDefinition) subagent).getAgentCard();

		try {
			// 创建消息
			Message message = new Message.Builder().role(Message.Role.USER)
				.parts(List.of(new TextPart(taskCall.prompt(), null)))
				.build();

			// 使用CompletableFuture等待响应
			CompletableFuture<String> responseFuture = new CompletableFuture<>();
			AtomicReference<String> responseText = new AtomicReference<>("");

			BiConsumer<ClientEvent, AgentCard> consumer = (event, card) -> {
				if (event instanceof TaskEvent taskEvent) {
					Task completedTask = taskEvent.getTask();
					logger.info("收到任务响应：status={}", completedTask.getStatus().state());

					// 从工件中提取文本
					if (completedTask.getArtifacts() != null) {
						StringBuilder sb = new StringBuilder();
						for (Artifact artifact : completedTask.getArtifacts()) {
							if (artifact.parts() != null) {
								for (Part<?> part : artifact.parts()) {
									if (part instanceof TextPart textPart) {
										sb.append(textPart.getText());
									}
								}
							}
						}
						responseText.set(sb.toString());
					}
					responseFuture.complete(responseText.get());
				}
			};

			// 通过构建器创建带有消费者的客户端
			ClientConfig clientConfig = new ClientConfig.Builder().setAcceptedOutputModes(List.of("text")).build();
			
			Client client = Client.builder(agentCard)
				.clientConfig(clientConfig)
				.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
				.addConsumers(List.of(consumer))
				.build();

			client.sendMessage(message);

			// 等待响应（带超时）
			String result = responseFuture.get(60, java.util.concurrent.TimeUnit.SECONDS);
			logger.info("代理'{}'的响应：{}", subagent.getName(), result);
			return result;
		}
		catch (Exception e) {
			logger.error("向代理'{}'发送消息时出错：{}", subagent.getName(), e.getMessage());
			return String.format("与代理'%s'通信时出错：%s", subagent.getName(), e.getMessage());
		}
	}

}

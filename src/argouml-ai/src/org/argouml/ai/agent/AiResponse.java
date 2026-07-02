/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.argouml.ai.infrastructure.json.JsonMini;

/**
 * A single assistant turn, containing the natural-language reply
 * (used by the chat pane in Task 10) and the list of tool invocations
 * the LLM wants executed (consumed by {@code PlannedOpParser} in
 * Task 3).
 *
 * <p>Instances are immutable. {@code content} may be {@code null} when
 * the assistant emits only tool calls and no prose. The tool-call list
 * is stored in declaration order because the preview table (Task 7)
 * and the executor both rely on that order matching the LLM's intent.
 */
public class AiResponse {

    private final String content;
    private final List<ToolCall> toolCalls;

    /**
     * @param content   the assistant's natural-language reply, or
     *                  {@code null} if the model emitted only tool calls.
     * @param toolCalls the list of tool invocations, in execution
     *                  order; may be empty but must not be {@code null}.
     */
    public AiResponse(String content, List<ToolCall> toolCalls) {
        if (toolCalls == null) {
            throw new IllegalArgumentException("toolCalls must not be null");
        }
        this.content = content;
        this.toolCalls = Collections.unmodifiableList(
                new ArrayList<ToolCall>(toolCalls));
    }

    /**
     * @return the natural-language reply, or {@code null} if absent.
     */
    public String getContent() {
        return content;
    }

    /**
     * @return an unmodifiable view of the tool-call list in execution
     *         order. Never {@code null} (an empty list is returned
     *         when the model produced no tool calls).
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    /**
     * Parse an OpenAI Chat Completions response body into an
     * {@link AiResponse}. The expected shape is:
     *
     * <pre>
     * {
     *   "choices": [{
     *     "message": {
     *       "role": "assistant",
     *       "content": "...",                  // string or null
     *       "tool_calls": [{
     *         "function": {
     *           "name": "...",
     *           "arguments": "..."              // JSON string OR nested object
     *         }
     *       }]
     *     }
     *   }]
     * }
     * </pre>
     *
     * <p>Lenient bits:
     * <ul>
     *   <li>{@code content} may be absent or {@code null} (the
     *       assistant only emitted tool calls).</li>
     *   <li>{@code tool_calls} may be absent (an empty list is
     *       returned).</li>
     *   <li>{@code function.arguments} may be either a JSON string
     *       (the OpenAI wire format) or a nested object (some
     *       sidecars serialize it that way). Nested objects are
     *       re-serialized via {@link JsonMini#stringify(Object)} so
     *       downstream code always sees a JSON string.</li>
     * </ul>
     *
     * @param json the response body; must not be {@code null}.
     * @return the parsed response.
     * @throws IllegalArgumentException for any structural problem
     *         (missing {@code choices}, empty {@code choices},
     *         missing {@code message}, missing {@code function},
     *         missing {@code name}, or arguments of an unexpected
     *         type).
     */
    public static AiResponse fromJson(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        Object root = JsonMini.parse(json);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException(
                    "expected JSON object at root, got "
                    + (root == null ? "null"
                       : root.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) root;
        Object choicesRaw = rootMap.get("choices");
        if (!(choicesRaw instanceof List)) {
            throw new IllegalArgumentException(
                    "OpenAI response missing 'choices' array");
        }
        @SuppressWarnings("unchecked")
        List<Object> choices = (List<Object>) choicesRaw;
        if (choices.isEmpty()) {
            throw new IllegalArgumentException(
                    "OpenAI response has empty 'choices' array");
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map)) {
            throw new IllegalArgumentException(
                    "choices[0] is not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> choiceMap =
                (Map<String, Object>) firstChoice;
        Object messageRaw = choiceMap.get("message");
        if (!(messageRaw instanceof Map)) {
            throw new IllegalArgumentException(
                    "choices[0].message is missing or not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> messageMap =
                (Map<String, Object>) messageRaw;
        String content = JsonMini.getString(messageMap, "content");
        List<ToolCall> calls = parseToolCalls(messageMap);
        return new AiResponse(content, calls);
    }

    private static List<ToolCall> parseToolCalls(
            Map<String, Object> messageMap) {
        Object raw = messageMap.get("tool_calls");
        if (raw == null) {
            return new ArrayList<ToolCall>();
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException(
                    "message.tool_calls is not an array");
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) raw;
        List<ToolCall> out = new ArrayList<ToolCall>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException(
                        "tool_calls[" + i + "] is not a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> tcMap = (Map<String, Object>) item;
            Object fnRaw = tcMap.get("function");
            if (!(fnRaw instanceof Map)) {
                throw new IllegalArgumentException(
                        "tool_calls[" + i
                        + "].function is missing or not a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fnMap = (Map<String, Object>) fnRaw;
            String name = JsonMini.getString(fnMap, "name");
            if (name == null) {
                throw new IllegalArgumentException(
                        "tool_calls[" + i
                        + "].function.name is missing");
            }
            Object argsRaw = fnMap.get("arguments");
            String arguments;
            if (argsRaw == null) {
                arguments = null;
            } else if (argsRaw instanceof String) {
                arguments = (String) argsRaw;
            } else if (argsRaw instanceof Map) {
                arguments = JsonMini.stringify(argsRaw);
            } else {
                throw new IllegalArgumentException(
                        "tool_calls[" + i
                        + "].function.arguments is neither a string"
                        + " nor an object");
            }
            out.add(new ToolCall(name, arguments));
        }
        return out;
    }
}

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.infrastructure.json.JsonMini;
import org.argouml.ai.tools.ToolDefinition;

/**
 * A Chat Completions request body the AI client ships to the sidecar.
 *
 * <p>The shape mirrors OpenAI's {@code POST /v1/chat/completions}
 * input: a {@code model} string, an ordered {@code messages} array of
 * {@code {role, content}} objects, and an optional {@code tools}
 * array of function definitions.
 *
 * <p>{@link #toJson()} delegates to
 * {@link JsonMini#stringify(Object)} (the reverse of
 * {@link JsonMini#parse(String)}) so the format is consistent with
 * what {@link AiResponse#fromJson(String)} consumes on the response
 * side. Message and tool order is preserved (LinkedHashMap-backed),
 * which is required for the conversation to read sensibly and for
 * tool-call execution order to match the LLM's intent.
 *
 * <p>Instances are mutable: callers populate {@code model},
 * {@code messages} and (optionally) {@code tools} and then hand
 * {@link #toJson()} to the HTTP client. Nothing here is threadsafe.
 */
public class AiRequest {

    private String model;
    private List<Message> messages;
    private List<ToolDefinition> tools;

    public AiRequest() {
        this.messages = new ArrayList<Message>();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ToolDefinition> tools) {
        this.tools = tools;
    }

    /**
     * Convenience: append a {@code {role, content}} message to the
     * current {@code messages} list, allocating it if it was never
     * set or was previously set to {@code null}.
     */
    public void addMessage(String role, String content) {
        if (messages == null) {
            messages = new ArrayList<Message>();
        }
        messages.add(new Message(role, content));
    }

    /**
     * @return the request serialized as a JSON object string. The
     *         {@code tools} key is omitted when {@link #getTools()}
     *         is {@code null} or empty (sending {@code "tools":[]}
     *         to OpenAI is legal but unnecessary).
     */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("model", model);
        List<Object> msgList = new ArrayList<Object>();
        if (messages != null) {
            for (Message m : messages) {
                Map<String, Object> mm = new LinkedHashMap<String, Object>();
                mm.put("role", m.getRole());
                mm.put("content", m.getContent());
                msgList.add(mm);
            }
        }
        root.put("messages", msgList);
        if (tools != null && !tools.isEmpty()) {
            List<Object> toolList = new ArrayList<Object>();
            for (ToolDefinition td : tools) {
                toolList.add(td.toJsonObject());
            }
            root.put("tools", toolList);
        }
        return JsonMini.stringify(root);
    }

    /**
     * A single chat-completion message: {@code role} ("system",
     * "user", "assistant", "tool", ...) and {@code content} (may be
     * {@code null} for assistant tool-call turns or empty strings).
     */
    public static class Message {

        private String role;
        private String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
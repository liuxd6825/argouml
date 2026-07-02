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

/**
 * A single tool invocation requested by the LLM. The {@code arguments}
 * field carries the raw JSON string as emitted by the model (OpenAI
 * returns arguments as a JSON-encoded string, not a nested object).
 *
 * <p>This class is intentionally minimal: Task 3 uses it to hand
 * {@code name} + {@code arguments} to {@code PlannedOpParser}, and Task 4
 * will add a static {@code fromJson(String)} factory to
 * {@link AiResponse} that constructs {@code ToolCall} instances when
 * deserialising the wire format.
 *
 * <p>Instances are immutable: once a tool call is built it cannot be
 * mutated, which matches the request/response model where a tool call
 * is a fact about a particular assistant message.
 */
public class ToolCall {

    private final String name;
    private final String arguments;

    /**
     * @param name      the tool name as it appears in the assistant
     *                  message (e.g. {@code "add_class"}); must not be
     *                  {@code null}.
     * @param arguments the JSON-encoded argument object as a string;
     *                  may be {@code null} or the empty string when the
     *                  tool takes no parameters.
     */
    public ToolCall(String name, String arguments) {
        if (name == null) {
            throw new IllegalArgumentException("tool name must not be null");
        }
        this.name = name;
        this.arguments = arguments;
    }

    /**
     * @return the tool name exactly as supplied (no trimming).
     */
    public String getName() {
        return name;
    }

    /**
     * @return the raw JSON arguments string. May be {@code null} or
     *         empty; the parser is responsible for validating it.
     */
    public String getArguments() {
        return arguments;
    }
}

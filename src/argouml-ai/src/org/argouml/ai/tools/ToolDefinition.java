/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description of a single tool that the LLM is allowed to invoke,
 * in OpenAI Chat Completions function-calling wire format:
 *
 * <pre>
 * {
 *   "type": "function",
 *   "function": {
 *     "name": "...",
 *     "description": "...",          // optional
 *     "parameters": { ... JSON Schema ... }  // optional
 *   }
 * }
 * </pre>
 *
 * <p>This is the value object an {@code AiRequest} carries on its
 * {@code tools} list. It is intentionally minimal: Task 5 will
 * populate a catalogue of {@code ClassDiagramTools} from this shape
 * (and the wire format will be filled in by {@code toJsonObject()}
 * via {@link org.argouml.ai.infrastructure.json.JsonMini#stringify(Object)}).
 */
public class ToolDefinition {

    private String name;
    private String description;
    private Map<String, Object> parameters;

    public ToolDefinition() {
    }

    /**
     * @param name        the tool name as it will appear in assistant
     *                    tool-call messages (e.g. {@code "add_class"}).
     * @param description human-readable description for the LLM; may
     *                    be {@code null} to omit the field.
     * @param parameters  JSON Schema object describing the tool
     *                    arguments; may be {@code null} to omit.
     */
    public ToolDefinition(String name, String description,
            Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the raw JSON Schema map for the parameters, or
     *         {@code null} when no schema was supplied.
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    /**
     * @return a nested {@code Map} ready to be fed to
     *         {@link org.argouml.ai.infrastructure.json.JsonMini#stringify(Object)}.
     *         Key order is stable (LinkedHashMap) so the wire format
     *         is deterministic.
     */
    public Map<String, Object> toJsonObject() {
        Map<String, Object> outer = new LinkedHashMap<String, Object>();
        outer.put("type", "function");
        Map<String, Object> fn = new LinkedHashMap<String, Object>();
        fn.put("name", name);
        if (description != null) {
            fn.put("description", description);
        }
        if (parameters != null) {
            fn.put("parameters", parameters);
        }
        outer.put("function", fn);
        return outer;
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.argouml.ai.agent.AiResponse;
import org.argouml.ai.agent.ToolCall;
import org.argouml.ai.infrastructure.json.JsonMini;

/**
 * Converts an {@link AiResponse} carrying one or more tool calls into
 * the ordered {@link PlannedOp} list the executor consumes.
 *
 * <p>The parser is strict: any tool name not in the known set, any
 * tool whose {@code arguments} is not a JSON object, or any JSON
 * syntax error throws {@link IllegalArgumentException}. Strict
 * validation surfaces prompt / schema drift immediately rather than
 * silently dropping the malformed call.
 *
 * <p>Tool-call order from the wire is preserved, matching OpenAI
 * semantics; the preview table in Task 7 and the executor both rely
 * on this ordering.
 */
public class PlannedOpParser {

    /**
     * @param response an assistant turn; must not be {@code null}.
     * @return the ordered list of planned ops for the tool calls
     *         contained in {@code response} (possibly empty).
     * @throws IllegalArgumentException if {@code response} is
     *         {@code null}, any tool name is unknown, or any
     *         arguments string is not a well-formed JSON object.
     */
    public List<PlannedOp> parse(AiResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("response must not be null");
        }
        List<ToolCall> calls = response.getToolCalls();
        List<PlannedOp> out = new ArrayList<PlannedOp>(calls.size());
        for (int i = 0; i < calls.size(); i++) {
            out.add(parseOne(calls.get(i), i));
        }
        return out;
    }

    private PlannedOp parseOne(ToolCall call, int index) {
        String name = call.getName();
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException(
                    "tool call #" + index + " has empty name");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c)) {
                throw new IllegalArgumentException(
                        "tool call #" + index
                        + " has whitespace in name '" + name + "'");
            }
        }
        Map<String, Object> args = parseArgs(call.getArguments(), name, index);
        if ("add_class".equals(name))           return opAddClass(args);
        if ("add_interface".equals(name))       return opAddInterface(args);
        if ("add_attribute".equals(name))       return opAddAttribute(args);
        if ("add_operation".equals(name))       return opAddOperation(args);
        if ("add_association".equals(name))     return opAddAssociation(args);
        if ("add_generalization".equals(name))  return opAddGeneralization(args);
        if ("add_dependency".equals(name))      return opAddDependency(args);
        if ("rename_class".equals(name))        return opRenameClass(args);
        if ("delete_class".equals(name))        return opDeleteClass(args);
        if ("list_classes".equals(name))        return opListClasses(args);
        throw new IllegalArgumentException(
                "unknown tool name '" + name + "' at call #" + index);
    }

    private static Map<String, Object> parseArgs(
            String arguments, String toolName, int index) {
        if (arguments == null) {
            throw new IllegalArgumentException(
                    "tool '" + toolName + "' (call #" + index
                    + ") has null arguments; expected a JSON object");
        }
        String trimmed = arguments.trim();
        if (trimmed.length() == 0) {
            return new java.util.LinkedHashMap<String, Object>();
        }
        try {
            return JsonMini.parseObject(arguments);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "tool '" + toolName + "' (call #" + index
                    + ") has invalid arguments: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------
    // Per-tool builders
    // -----------------------------------------------------------------

    private static PlannedOp opAddClass(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", JsonMini.getString(a, "name"));
        op.setInt("x", JsonMini.getInt(a, "x"));
        op.setInt("y", JsonMini.getInt(a, "y"));
        putIfPresent(op, "stereotype", JsonMini.getString(a, "stereotype"));
        if (JsonMini.getBoolean(a, "isAbstract")) {
            op.setBoolean("isAbstract", true);
        }
        return op;
    }

    private static PlannedOp opAddInterface(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_INTERFACE);
        op.setString("name", JsonMini.getString(a, "name"));
        op.setInt("x", JsonMini.getInt(a, "x"));
        op.setInt("y", JsonMini.getInt(a, "y"));
        putIfPresent(op, "stereotype", JsonMini.getString(a, "stereotype"));
        return op;
    }

    private static PlannedOp opAddAttribute(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_ATTRIBUTE);
        op.setString("className", JsonMini.getString(a, "className"));
        op.setString("name", JsonMini.getString(a, "name"));
        op.setString("type", JsonMini.getString(a, "type"));
        op.setString("visibility", JsonMini.getString(a, "visibility"));
        return op;
    }

    private static PlannedOp opAddOperation(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_OPERATION);
        op.setString("className", JsonMini.getString(a, "className"));
        op.setString("name", JsonMini.getString(a, "name"));
        op.setString("returnType", JsonMini.getString(a, "returnType"));
        op.setString("visibility", JsonMini.getString(a, "visibility"));
        return op;
    }

    private static PlannedOp opAddAssociation(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_ASSOCIATION);
        op.setString("classA", JsonMini.getString(a, "classA"));
        op.setString("classB", JsonMini.getString(a, "classB"));
        putIfPresent(op, "labelA", JsonMini.getString(a, "labelA"));
        putIfPresent(op, "labelB", JsonMini.getString(a, "labelB"));
        op.setString("multA", JsonMini.getString(a, "multA"));
        op.setString("multB", JsonMini.getString(a, "multB"));
        op.setString("name", JsonMini.getString(a, "name"));
        return op;
    }

    private static PlannedOp opAddGeneralization(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_GENERALIZATION);
        op.setString("subclass", JsonMini.getString(a, "subclass"));
        op.setString("superclass", JsonMini.getString(a, "superclass"));
        return op;
    }

    private static PlannedOp opAddDependency(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_DEPENDENCY);
        op.setString("client", JsonMini.getString(a, "client"));
        op.setString("supplier", JsonMini.getString(a, "supplier"));
        op.setString("name", JsonMini.getString(a, "name"));
        return op;
    }

    private static PlannedOp opRenameClass(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.RENAME_CLASS);
        op.setString("oldName", JsonMini.getString(a, "oldName"));
        op.setString("newName", JsonMini.getString(a, "newName"));
        return op;
    }

    private static PlannedOp opDeleteClass(Map<String, Object> a) {
        PlannedOp op = new PlannedOp(PlannedOp.Type.DELETE_CLASS);
        op.setString("name", JsonMini.getString(a, "name"));
        return op;
    }

    private static PlannedOp opListClasses(Map<String, Object> a) {
        return new PlannedOp(PlannedOp.Type.LIST_CLASSES);
    }

    private static void putIfPresent(PlannedOp op, String key, String v) {
        if (v != null) {
            op.setString(key, v);
        }
    }
}

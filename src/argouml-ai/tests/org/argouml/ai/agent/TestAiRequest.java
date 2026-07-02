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

import junit.framework.TestCase;

import org.argouml.ai.infrastructure.json.JsonMini;
import org.argouml.ai.tools.ToolDefinition;

/**
 * Tests {@link AiRequest#toJson()} which serialises an outgoing
 * Chat Completions request (model + messages + optional tools).
 */
public class TestAiRequest extends TestCase {

    public void testToJsonContainsModel() {
        AiRequest req = new AiRequest();
        req.setModel("gpt-4o-mini");
        req.addMessage("user", "hi");
        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        assertEquals("gpt-4o-mini", JsonMini.getString(obj, "model"));
    }

    public void testToJsonSerializesMessagesInOrder() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("system", "You are a UML assistant.");
        req.addMessage("user", "Add a class called Order.");
        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        @SuppressWarnings("unchecked")
        List<Object> msgs = (List<Object>) obj.get("messages");
        assertEquals(2, msgs.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> m0 = (Map<String, Object>) msgs.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> m1 = (Map<String, Object>) msgs.get(1);
        assertEquals("system", JsonMini.getString(m0, "role"));
        assertEquals("You are a UML assistant.",
                JsonMini.getString(m0, "content"));
        assertEquals("user", JsonMini.getString(m1, "role"));
        assertEquals("Add a class called Order.",
                JsonMini.getString(m1, "content"));
    }

    public void testToJsonOmitsToolsWhenNull() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");
        req.setTools(null);
        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        assertFalse("tools should be absent when set to null",
                obj.containsKey("tools"));
    }

    public void testToJsonOmitsToolsWhenEmpty() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");
        req.setTools(new ArrayList<ToolDefinition>());
        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        assertFalse("tools should be absent when empty",
                obj.containsKey("tools"));
    }

    public void testToJsonIncludesToolsWhenPresent() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        Map<String, Object> nameProp = new LinkedHashMap<String, Object>();
        nameProp.put("type", "string");
        props.put("name", nameProp);
        schema.put("properties", props);

        ToolDefinition td = new ToolDefinition("add_class",
                "Add a UML class.", schema);
        List<ToolDefinition> tools = new ArrayList<ToolDefinition>();
        tools.add(td);
        req.setTools(tools);

        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        @SuppressWarnings("unchecked")
        List<Object> ts = (List<Object>) obj.get("tools");
        assertEquals(1, ts.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> toolEntry =
                (Map<String, Object>) ts.get(0);
        assertEquals("function", JsonMini.getString(toolEntry, "type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> fn =
                (Map<String, Object>) toolEntry.get("function");
        assertEquals("add_class",
                JsonMini.getString(fn, "name"));
        assertEquals("Add a UML class.",
                JsonMini.getString(fn, "description"));
        @SuppressWarnings("unchecked")
        Map<String, Object> params =
                (Map<String, Object>) fn.get("parameters");
        assertEquals("object", JsonMini.getString(params, "type"));
    }

    public void testToJsonIncludesMultipleTools() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");
        List<ToolDefinition> tools = new ArrayList<ToolDefinition>();
        tools.add(new ToolDefinition("add_class", null, null));
        tools.add(new ToolDefinition("delete_class", null, null));
        req.setTools(tools);

        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        @SuppressWarnings("unchecked")
        List<Object> ts = (List<Object>) obj.get("tools");
        assertEquals(2, ts.size());
    }

    public void testToJsonEscapesSpecialCharsInContent() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "He said \"hi\"\nNewline\\backslash");
        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        @SuppressWarnings("unchecked")
        List<Object> msgs = (List<Object>) obj.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> m0 = (Map<String, Object>) msgs.get(0);
        assertEquals("He said \"hi\"\nNewline\\backslash",
                JsonMini.getString(m0, "content"));
    }

    public void testToJsonOmitsDescriptionAndParametersWhenUnset() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");
        ToolDefinition td = new ToolDefinition("noop", null, null);
        List<ToolDefinition> tools = new ArrayList<ToolDefinition>();
        tools.add(td);
        req.setTools(tools);

        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        @SuppressWarnings("unchecked")
        List<Object> ts = (List<Object>) obj.get("tools");
        @SuppressWarnings("unchecked")
        Map<String, Object> fn =
                (Map<String, Object>) ((Map<String, Object>) ts.get(0))
                .get("function");
        assertEquals("noop", JsonMini.getString(fn, "name"));
        assertFalse("description should be absent when null",
                fn.containsKey("description"));
        assertFalse("parameters should be absent when null",
                fn.containsKey("parameters"));
    }

    public void testMessageStoredAsRoleAndContent() {
        AiRequest.Message msg = new AiRequest.Message("assistant", "hello");
        assertEquals("assistant", msg.getRole());
        assertEquals("hello", msg.getContent());
    }

    public void testMessageSettersRoundtrip() {
        AiRequest.Message msg = new AiRequest.Message();
        msg.setRole("user");
        msg.setContent("hi");
        assertEquals("user", msg.getRole());
        assertEquals("hi", msg.getContent());
    }

    public void testAddMessageAppends() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "a");
        req.addMessage("user", "b");
        req.addMessage("assistant", "c");
        assertEquals(3, req.getMessages().size());
        assertEquals("a", req.getMessages().get(0).getContent());
        assertEquals("b", req.getMessages().get(1).getContent());
        assertEquals("c", req.getMessages().get(2).getContent());
    }

    public void testAiRequestFieldsAreSettable() {
        AiRequest req = new AiRequest();
        req.setModel("x");
        req.addMessage("user", "u");
        List<ToolDefinition> tools = new ArrayList<ToolDefinition>();
        req.setTools(tools);
        assertEquals("x", req.getModel());
        assertEquals(1, req.getMessages().size());
        assertSame(tools, req.getTools());
    }

    public void testToJsonHandlesUnicodeInContent() {
        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "\u8ba2\u5355\u7c7b");
        Map<String, Object> obj = JsonMini.parseObject(req.toJson());
        @SuppressWarnings("unchecked")
        List<Object> msgs = (List<Object>) obj.get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> m0 = (Map<String, Object>) msgs.get(0);
        assertEquals("\u8ba2\u5355\u7c7b",
                JsonMini.getString(m0, "content"));
    }
}
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.infrastructure.json.JsonMini;

/**
 * Tests {@link AiResponse#fromJson(String)} against canonical
 * OpenAI Chat Completions wire-format fixtures.
 */
public class TestAiResponse extends TestCase {

    public void testFromJsonContentAndToolCalls() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"Hello there.\","
                + "\"tool_calls\":[{\"function\":{"
                + "\"name\":\"add_class\","
                + "\"arguments\":\"{\\\"name\\\":\\\"Order\\\"}\""
                + "}}]"
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertEquals("Hello there.", r.getContent());
        assertEquals(1, r.getToolCalls().size());
        assertEquals("add_class", r.getToolCalls().get(0).getName());
        assertEquals("{\"name\":\"Order\"}",
                r.getToolCalls().get(0).getArguments());
    }

    public void testFromJsonContentOnly() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"Sure.\""
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertEquals("Sure.", r.getContent());
        assertEquals(0, r.getToolCalls().size());
    }

    public void testFromJsonContentPresentAndToolCallsKeyAbsent() {
        // Some sidecars omit tool_calls entirely when no tools were called.
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"Hi\""
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertEquals("Hi", r.getContent());
        assertEquals(0, r.getToolCalls().size());
    }

    public void testFromJsonContentAbsentAndToolCallsAbsent() {
        // Both fields missing -- should produce null content and empty list.
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\""
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertNull(r.getContent());
        assertEquals(0, r.getToolCalls().size());
    }

    public void testFromJsonToolCallsOnlyNullContent() {
        // OpenAI emits null content when the assistant only emitted
        // tool calls.
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":null,"
                + "\"tool_calls\":[{\"function\":{"
                + "\"name\":\"add_class\","
                + "\"arguments\":\"{}\""
                + "}}]"
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertNull(r.getContent());
        assertEquals(1, r.getToolCalls().size());
        assertEquals("add_class", r.getToolCalls().get(0).getName());
        assertEquals("{}", r.getToolCalls().get(0).getArguments());
    }

    public void testFromJsonNestedArgumentsObjectIsReserialized() {
        // Some sidecars emit arguments as a nested object instead of
        // the OpenAI-standard JSON-encoded string.
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\","
                + "\"tool_calls\":[{\"function\":{"
                + "\"name\":\"add_class\","
                + "\"arguments\":{\"name\":\"Order\"}"
                + "}}]"
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        ToolCall tc = r.getToolCalls().get(0);
        assertEquals("add_class", tc.getName());
        // arguments must be a JSON string (re-serialized).
        assertEquals("{\"name\":\"Order\"}", tc.getArguments());
        // And the resulting string must round-trip through JsonMini.
        Map<String, Object> m = JsonMini.parseObject(tc.getArguments());
        assertEquals("Order", JsonMini.getString(m, "name"));
    }

    public void testFromJsonArgumentsAbsentYieldsNull() {
        // OpenAI usually sends arguments as a (possibly empty) string,
        // but be lenient if the field is absent entirely.
        String json = "{\"choices\":[{\"message\":{"
                + "\"tool_calls\":[{\"function\":{"
                + "\"name\":\"add_class\""
                + "}}]"
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertEquals("add_class", r.getToolCalls().get(0).getName());
        assertNull(r.getToolCalls().get(0).getArguments());
    }

    public void testFromJsonMultipleToolCallsPreservesOrder() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"tool_calls\":["
                + "{\"function\":{\"name\":\"add_class\",\"arguments\":\"{}\"}},"
                + "{\"function\":{\"name\":\"rename_class\",\"arguments\":\"{}\"}},"
                + "{\"function\":{\"name\":\"add_attribute\",\"arguments\":\"{}\"}}"
                + "]}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertEquals(3, r.getToolCalls().size());
        assertEquals("add_class",     r.getToolCalls().get(0).getName());
        assertEquals("rename_class",  r.getToolCalls().get(1).getName());
        assertEquals("add_attribute", r.getToolCalls().get(2).getName());
    }

    public void testFromJsonEmptyToolCallsList() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"role\":\"assistant\","
                + "\"content\":\"Hi\","
                + "\"tool_calls\":[]"
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        assertEquals("Hi", r.getContent());
        assertEquals(0, r.getToolCalls().size());
    }

    public void testFromJsonEmptyChoicesIsRejected() {
        String json = "{\"choices\":[]}";
        try {
            AiResponse.fromJson(json);
            fail("expected IllegalArgumentException for empty choices");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention choices: "
                    + expected.getMessage(),
                    expected.getMessage().contains("choices"));
        }
    }

    public void testFromJsonMissingChoicesIsRejected() {
        String json = "{}";
        try {
            AiResponse.fromJson(json);
            fail("expected IllegalArgumentException for missing choices");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention 'choices': "
                    + expected.getMessage(),
                    expected.getMessage().contains("choices"));
        }
    }

    public void testFromJsonMissingMessageIsRejected() {
        String json = "{\"choices\":[{}]}";
        try {
            AiResponse.fromJson(json);
            fail("expected IllegalArgumentException for missing message");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention 'message': "
                    + expected.getMessage(),
                    expected.getMessage().contains("message"));
        }
    }

    public void testFromJsonToolCallMissingFunctionIsRejected() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"tool_calls\":[{\"id\":\"1\"}]"
                + "}}]}";
        try {
            AiResponse.fromJson(json);
            fail("expected IllegalArgumentException for missing function");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention 'function': "
                    + expected.getMessage(),
                    expected.getMessage().contains("function"));
        }
    }

    public void testFromJsonToolCallMissingNameIsRejected() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"tool_calls\":[{\"function\":{\"arguments\":\"{}\"}}]"
                + "}}]}";
        try {
            AiResponse.fromJson(json);
            fail("expected IllegalArgumentException for missing name");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention 'name': "
                    + expected.getMessage(),
                    expected.getMessage().contains("name"));
        }
    }

    public void testFromJsonRejectsNullJson() {
        try {
            AiResponse.fromJson(null);
            fail("expected IllegalArgumentException for null");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testFromJsonRejectsMalformedJson() {
        try {
            AiResponse.fromJson("{not json");
            fail("expected IllegalArgumentException for malformed JSON");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testFromJsonRejectsNonObjectRoot() {
        try {
            AiResponse.fromJson("[1,2,3]");
            fail("expected IllegalArgumentException for array root");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testFromJsonLoadsFixtureFile() throws IOException {
        InputStream in = getClass().getResourceAsStream(
                "fixtures/openai-response.json");
        assertNotNull("fixture resource missing", in);
        try {
            String json = readAll(in);
            AiResponse r = AiResponse.fromJson(json);
            assertEquals("Hi", r.getContent());
            assertEquals(1, r.getToolCalls().size());
            assertEquals("add_class", r.getToolCalls().get(0).getName());
            String args = r.getToolCalls().get(0).getArguments();
            assertNotNull(args);
            // arguments may be either a string or a nested object;
            // either way the parser must hand back valid JSON.
            Map<String, Object> m = JsonMini.parseObject(args);
            assertEquals("Order", JsonMini.getString(m, "name"));
        } finally {
            in.close();
        }
    }

    public void testFromJsonToolCallsListIsUnmodifiable() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"content\":\"x\","
                + "\"tool_calls\":[{\"function\":{"
                + "\"name\":\"add_class\",\"arguments\":\"{}\"}}]"
                + "}}]}";
        AiResponse r = AiResponse.fromJson(json);
        try {
            r.getToolCalls().add(new ToolCall("foo", "{}"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    public void testFromJsonNonStringNonObjectArgumentsIsRejected() {
        String json = "{\"choices\":[{\"message\":{"
                + "\"tool_calls\":[{\"function\":{"
                + "\"name\":\"add_class\",\"arguments\":42"
                + "}}]"
                + "}}]}";
        try {
            AiResponse.fromJson(json);
            fail("expected IllegalArgumentException for numeric args");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("arguments"));
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toString("UTF-8");
    }
}
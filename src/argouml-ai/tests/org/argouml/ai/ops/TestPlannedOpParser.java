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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.agent.AiResponse;
import org.argouml.ai.agent.ToolCall;
import org.argouml.ai.infrastructure.json.JsonMini;

/**
 * Tests the {@link PlannedOpParser} and the supporting
 * {@link JsonMini} helper, plus minimal smoke tests for
 * {@link AiResponse} and {@link ToolCall} since the parser depends
 * on them.
 */
public class TestPlannedOpParser extends TestCase {

    private final PlannedOpParser parser = new PlannedOpParser();

    // -----------------------------------------------------------------
    // Plan-mandated cases
    // -----------------------------------------------------------------

    public void testSingleAddClassProducesOneOp() {
        AiResponse resp = response(call("add_class",
                "{\"name\":\"Order\",\"x\":200,\"y\":100}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        PlannedOp op = ops.get(0);
        assertEquals(PlannedOp.Type.ADD_CLASS, op.getType());
        assertEquals("Order", op.getString("name"));
        assertEquals(200, op.getInt("x"));
        assertEquals(100, op.getInt("y"));
    }

    public void testMultipleToolCallsArePreservedInOrder() {
        AiResponse resp = response(
                call("add_class",    "{\"name\":\"A\",\"x\":10,\"y\":20}"),
                call("add_class",    "{\"name\":\"B\",\"x\":30,\"y\":40}"),
                call("rename_class", "{\"oldName\":\"A\",\"newName\":\"Alpha\"}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(3, ops.size());
        assertEquals(PlannedOp.Type.ADD_CLASS,    ops.get(0).getType());
        assertEquals("A",    ops.get(0).getString("name"));
        assertEquals(PlannedOp.Type.ADD_CLASS,    ops.get(1).getType());
        assertEquals("B",    ops.get(1).getString("name"));
        assertEquals(PlannedOp.Type.RENAME_CLASS, ops.get(2).getType());
        assertEquals("Alpha", ops.get(2).getString("newName"));
    }

    public void testArgumentsNotJsonObjectIsRejected() {
        AiResponse resp = response(call("add_class", "[1, 2, 3]"));
        try {
            parser.parse(resp);
            fail("expected IllegalArgumentException for non-object args");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention tool name: "
                    + expected.getMessage(),
                    expected.getMessage().contains("add_class"));
        }
    }

    public void testMalformedJsonArgumentsIsRejected() {
        AiResponse resp = response(call("add_class", "{not json"));
        try {
            parser.parse(resp);
            fail("expected IllegalArgumentException for malformed JSON");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("add_class"));
        }
    }

    public void testUnknownToolNameIsRejected() {
        AiResponse resp = response(call("delete_universe", "{}"));
        try {
            parser.parse(resp);
            fail("expected IllegalArgumentException for unknown tool");
        } catch (IllegalArgumentException expected) {
            assertTrue("message should mention tool name: "
                    + expected.getMessage(),
                    expected.getMessage().contains("delete_universe"));
        }
    }

    // -----------------------------------------------------------------
    // Recommended expansion
    // -----------------------------------------------------------------

    public void testAddClassIgnoresUnknownFields() {
        AiResponse resp = response(call("add_class",
                "{\"name\":\"X\",\"x\":1,\"y\":2,\"ignored\":42,"
                + "\"also\":[1,2]}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        assertEquals("X", ops.get(0).getString("name"));
        assertEquals(1, ops.get(0).getInt("x"));
        assertEquals(2, ops.get(0).getInt("y"));
        assertNull(ops.get(0).getString("ignored"));
    }

    public void testAddAttributeCarriesNamedField() {
        AiResponse resp = response(call("add_attribute",
                "{\"className\":\"Order\",\"name\":\"total\","
                + "\"type\":\"int\",\"visibility\":\"public\"}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        assertEquals(PlannedOp.Type.ADD_ATTRIBUTE, ops.get(0).getType());
        assertEquals("Order",  ops.get(0).getString("className"));
        assertEquals("total",  ops.get(0).getString("name"));
        assertEquals("int",    ops.get(0).getString("type"));
        assertEquals("public", ops.get(0).getString("visibility"));
    }

    public void testAddAssociationCarriesMultiplicities() {
        AiResponse resp = response(call("add_association",
                "{\"classA\":\"Order\",\"classB\":\"LineItem\","
                + "\"multA\":\"1\",\"multB\":\"*\","
                + "\"name\":\"contains\"}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        assertEquals(PlannedOp.Type.ADD_ASSOCIATION, ops.get(0).getType());
        assertEquals("Order",    ops.get(0).getString("classA"));
        assertEquals("LineItem", ops.get(0).getString("classB"));
        assertEquals("1",        ops.get(0).getString("multA"));
        assertEquals("*",        ops.get(0).getString("multB"));
        assertEquals("contains", ops.get(0).getString("name"));
    }

    public void testAddGeneralizationMinimal() {
        AiResponse resp = response(call("add_generalization",
                "{\"subclass\":\"VIPOrder\",\"superclass\":\"Order\"}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        assertEquals(PlannedOp.Type.ADD_GENERALIZATION, ops.get(0).getType());
        assertEquals("VIPOrder", ops.get(0).getString("subclass"));
        assertEquals("Order",    ops.get(0).getString("superclass"));
    }

    public void testListClassesTakesNoArgs() {
        AiResponse resp = response(call("list_classes", "{}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        assertEquals(PlannedOp.Type.LIST_CLASSES, ops.get(0).getType());
    }

    public void testListClassesTakesEmptyArgs() {
        AiResponse resp = response(call("list_classes", ""));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals(1, ops.size());
        assertEquals(PlannedOp.Type.LIST_CLASSES, ops.get(0).getType());
    }

    public void testToolNameWithWhitespaceIsRejected() {
        AiResponse resp = response(call(" add_class", "{}"));
        try {
            parser.parse(resp);
            fail("expected IllegalArgumentException for whitespace in name");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("whitespace"));
        }
    }

    public void testEmptyToolCallListReturnsEmptyResult() {
        AiResponse resp = new AiResponse("hi", new ArrayList<ToolCall>());
        List<PlannedOp> ops = parser.parse(resp);
        assertNotNull(ops);
        assertTrue(ops.isEmpty());
    }

    public void testNullResponseIsRejected() {
        try {
            parser.parse(null);
            fail("expected IllegalArgumentException for null response");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testChineseClassName() {
        AiResponse resp = response(call("add_class",
                "{\"name\":\"\\u8ba2\\u5355\\u7c7b\",\"x\":0,\"y\":0}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals("\u8ba2\u5355\u7c7b", ops.get(0).getString("name"));
    }

    public void testStringEscapesAreUnescaped() {
        AiResponse resp = response(call("add_class",
                "{\"name\":\"a\\\"b\\\\c\\nd\"}"));
        List<PlannedOp> ops = parser.parse(resp);
        assertEquals("a\"b\\c\nd", ops.get(0).getString("name"));
    }

    // -----------------------------------------------------------------
    // JsonMini direct coverage
    // -----------------------------------------------------------------

    public void testJsonMiniParsesNestedObjects() {
        Object v = JsonMini.parse(
                "{\"a\":1,\"b\":[true,false,null,\"x\"],\"c\":{\"k\":\"v\"}}");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        assertEquals(1, JsonMini.getInt(m, "a"));
        @SuppressWarnings("unchecked")
        List<Object> b = (List<Object>) m.get("b");
        assertEquals(4, b.size());
        assertEquals(Boolean.TRUE, b.get(0));
        assertEquals(Boolean.FALSE, b.get(1));
        assertNull(b.get(2));
        assertEquals("x", b.get(3));
        assertFalse(JsonMini.getBoolean(m, "missing"));
        assertEquals(0, JsonMini.getInt(m, "missing"));
        assertNull(JsonMini.getString(m, "missing"));
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) m.get("c");
        assertEquals("v", JsonMini.getString(c, "k"));
    }

    public void testJsonMiniPreservesKeyOrder() {
        Object v = JsonMini.parse("{\"z\":1,\"a\":2,\"m\":3}");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        assertEquals(Arrays.asList("z", "a", "m"), new ArrayList<Object>(
                m.keySet()));
    }

    public void testJsonMiniUnicodeEscapeRoundtrips() {
        Object v = JsonMini.parse("{\"k\":\"\\u4e2d\\u6587\"}");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        assertEquals("\u4e2d\u6587", JsonMini.getString(m, "k"));
    }

    public void testJsonMiniRejectsMalformedInput() {
        try {
            JsonMini.parse("{");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testJsonMiniRejectsUnknownEscape() {
        try {
            JsonMini.parse("{\"k\":\"\\q\"}");
            fail("expected IllegalArgumentException for \\q");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testJsonMiniRejectsDecimalNumbers() {
        try {
            JsonMini.parse("{\"k\":1.5}");
            fail("expected IllegalArgumentException for decimal");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testParseObjectRejectsArray() {
        try {
            JsonMini.parseObject("[1,2]");
            fail("expected IllegalArgumentException for array root");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testJsonMiniAcceptsEmptyObject() {
        Map<String, Object> m = JsonMini.parseObject("{}");
        assertTrue(m.isEmpty());
    }

    public void testJsonMiniAcceptsWhitespaceAround() {
        Object v = JsonMini.parse("  \n\t{ \"a\" : 1 }\r\n ");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        assertEquals(1, JsonMini.getInt(m, "a"));
    }

    public void testJsonMiniRejectsTrailingGarbage() {
        try {
            JsonMini.parse("{\"a\":1} junk");
            fail("expected IllegalArgumentException for trailing content");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testJsonMiniNullInputIsRejected() {
        try {
            JsonMini.parse(null);
            fail("expected IllegalArgumentException for null input");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // -----------------------------------------------------------------
    // AiResponse / ToolCall smoke tests
    // -----------------------------------------------------------------

    public void testToolCallRejectsNullName() {
        try {
            new ToolCall(null, "{}");
            fail("expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    public void testToolCallStoresFields() {
        ToolCall tc = new ToolCall("add_class", "{\"name\":\"X\"}");
        assertEquals("add_class", tc.getName());
        assertEquals("{\"name\":\"X\"}", tc.getArguments());
    }

    public void testAiResponseStoresContentAndCalls() {
        ToolCall a = new ToolCall("add_class", "{\"name\":\"A\"}");
        ToolCall b = new ToolCall("add_class", "{\"name\":\"B\"}");
        AiResponse r = new AiResponse("hello",
                Arrays.asList(new ToolCall[] {a, b}));
        assertEquals("hello", r.getContent());
        assertEquals(2, r.getToolCalls().size());
        assertEquals("{\"name\":\"A\"}",
                r.getToolCalls().get(0).getArguments());
        assertEquals("add_class", r.getToolCalls().get(1).getName());
    }

    public void testAiResponseNullContentIsAllowed() {
        AiResponse r = new AiResponse(null, new ArrayList<ToolCall>());
        assertNull(r.getContent());
        assertEquals(0, r.getToolCalls().size());
    }

    public void testAiResponseToolCallListIsUnmodifiable() {
        AiResponse r = new AiResponse(null, new ArrayList<ToolCall>());
        try {
            r.getToolCalls().add(new ToolCall("add_class", "{}"));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    public void testAiResponseRejectsNullToolCalls() {
        try {
            new AiResponse(null, null);
            fail("expected IllegalArgumentException for null toolCalls");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static ToolCall call(String name, String arguments) {
        return new ToolCall(name, arguments);
    }

    private static AiResponse response(ToolCall... calls) {
        if (calls == null) {
            return new AiResponse(null, new ArrayList<ToolCall>());
        }
        return new AiResponse(null, Collections.unmodifiableList(
                new ArrayList<ToolCall>(Arrays.asList(calls))));
    }
}

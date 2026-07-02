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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.argouml.ai.infrastructure.json.JsonMini;

/**
 * Smoke and shape tests for {@link ClassDiagramTools}.
 *
 * <p>Concerns covered (in priority order):
 * <ol>
 *   <li>the catalogue has the expected 10 entries;</li>
 *   <li>each entry serializes to a non-empty OpenAI-shape JSON
 *       object;</li>
 *   <li>tool names exactly match the set
 *       {@link org.argouml.ai.ops.PlannedOpParser} switches on
 *       (cross-task consistency \u2014 if these drift, every LLM
 *       response is rejected);</li>
 *   <li>every {@code required} name is also a {@code properties}
 *       key (otherwise the schema is contradictory);</li>
 *   <li>{@link ToolDefinition#toJsonObject()} emits only the two
 *       OpenAI-mandated top-level keys.</li>
 * </ol>
 */
public class TestClassDiagramTools extends TestCase {

    /**
     * The 10 tool names {@link ClassDiagramTools#all()} must expose.
     * Kept in lock-step with the {@code if} chain in
     * {@link org.argouml.ai.ops.PlannedOpParser#parseOne}; if the
     * parser gains or loses a name, this list and that chain must
     * change together.
     */
    private static final List<String> EXPECTED_TOOL_NAMES =
            Collections.unmodifiableList(Arrays.asList(new String[] {
                    "list_classes",
                    "add_class",
                    "add_interface",
                    "add_attribute",
                    "add_operation",
                    "add_association",
                    "add_generalization",
                    "add_dependency",
                    "rename_class",
                    "delete_class"
            }));

    // -----------------------------------------------------------------
    // Spec-required smoke
    // -----------------------------------------------------------------

    public void testTenTools() {
        List<ToolDefinition> tools = ClassDiagramTools.all();
        assertNotNull("all() must not return null", tools);
        assertEquals("catalogue must have exactly 10 tools",
                EXPECTED_TOOL_NAMES.size(), tools.size());
        for (int i = 0; i < tools.size(); i++) {
            ToolDefinition t = tools.get(i);
            assertNotNull("tool #" + i + " must not be null", t);
            String json = JsonMini.stringify(t.toJsonObject());
            assertFalse("toJsonObject() for '" + t.getName()
                    + "' must be non-empty", json.length() == 0);
            assertTrue("toJsonObject() for '" + t.getName()
                    + "' must contain 'name': " + json,
                    json.contains("\"name\""));
            assertTrue("toJsonObject() for '" + t.getName()
                    + "' must contain 'parameters': " + json,
                    json.contains("\"parameters\""));
        }
    }

    // -----------------------------------------------------------------
    // Recommended expansion
    // -----------------------------------------------------------------

    public void testAllReturnsUnmodifiableList() {
        try {
            ClassDiagramTools.all().add(new ToolDefinition(
                    "rogue", "rogue",
                    new LinkedHashMap<String, Object>()));
            fail("expected UnsupportedOperationException on mutation");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    public void testToolNamesAreUnique() {
        List<ToolDefinition> tools = ClassDiagramTools.all();
        Set<String> seen = new HashSet<String>();
        for (ToolDefinition t : tools) {
            assertNotNull(t.getName());
            assertTrue("duplicate tool name '" + t.getName() + "'",
                    seen.add(t.getName()));
        }
        assertEquals(tools.size(), seen.size());
    }

    public void testToolNamesMatchParserType() {
        List<ToolDefinition> tools = ClassDiagramTools.all();
        List<String> got = new ArrayList<String>(tools.size());
        for (ToolDefinition t : tools) {
            got.add(t.getName());
        }
        // Both sets must match exactly. Sorting avoids relying on
        // declaration order while still catching drift either way.
        List<String> gotSorted = new ArrayList<String>(got);
        List<String> expectedSorted = new ArrayList<String>(
                EXPECTED_TOOL_NAMES);
        Collections.sort(gotSorted);
        Collections.sort(expectedSorted);
        assertEquals("tool name set must equal parser-known set",
                expectedSorted, gotSorted);
    }

    public void testNoExtraFieldsInJsonObject() {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            Map<String, Object> j = t.toJsonObject();
            Set<String> keys = j.keySet();
            assertEquals("only 'type' and 'function' allowed at top"
                    + " level for tool '" + t.getName() + "', got "
                    + keys, 2, keys.size());
            assertTrue("must have 'type' key", keys.contains("type"));
            assertTrue("must have 'function' key",
                    keys.contains("function"));
            assertEquals("'type' must be 'function'",
                    "function", j.get("type"));
            Map<String, Object> fn = (Map<String, Object>) j.get("function");
            assertNotNull("'function' must be an object", fn);
            assertTrue("function.name required for '"
                    + t.getName() + "'",
                    fn.containsKey("name"));
        }
    }

    public void testEachToJsonStringIsValidJson() {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            String json = JsonMini.stringify(t.toJsonObject());
            Object parsed;
            try {
                parsed = JsonMini.parse(json);
            } catch (IllegalArgumentException e) {
                fail("toJsonObject() for '" + t.getName()
                        + "' did not round-trip through JsonMini: "
                        + e.getMessage());
                return;
            }
            assertTrue("round-trip must produce an object for '"
                    + t.getName() + "'", parsed instanceof Map);
        }
    }

    public void testRequiredFieldsAreSubset() {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            Map<String, Object> params = t.getParameters();
            if (params == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>)
                    params.get("properties");
            @SuppressWarnings("unchecked")
            List<Object> required = (List<Object>) params.get("required");
            if (required == null) {
                continue;
            }
            Set<String> propKeys = new LinkedHashMap<String, Object>(
                    props == null
                            ? Collections.<String, Object>emptyMap()
                            : props).keySet();
            for (Object r : required) {
                assertTrue("tool '" + t.getName()
                        + "': required field '" + r
                        + "' is missing from properties ("
                        + propKeys + ")",
                        r instanceof String && propKeys.contains(r));
            }
        }
    }

    public void testEveryParameterHasTypeAndDescription() {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            Map<String, Object> params = t.getParameters();
            if (params == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>)
                    params.get("properties");
            if (props == null) {
                continue;
            }
            for (Map.Entry<String, Object> e : props.entrySet()) {
                String pname = e.getKey();
                Object pv = e.getValue();
                assertTrue("tool '" + t.getName()
                        + "' parameter '" + pname
                        + "' must be a JSON Schema object",
                        pv instanceof Map);
                @SuppressWarnings("unchecked")
                Map<String, Object> pmap = (Map<String, Object>) pv;
                assertTrue("parameter '" + pname
                        + "' of '" + t.getName()
                        + "' must have 'type'",
                        pmap.containsKey("type"));
                assertTrue("parameter '" + pname
                        + "' of '" + t.getName()
                        + "' must have 'description'",
                        pmap.containsKey("description"));
                Object d = pmap.get("description");
                assertTrue("parameter '" + pname + "' of '"
                        + t.getName()
                        + "' description must be non-empty string",
                        d instanceof String
                        && ((String) d).length() > 0);
            }
        }
    }

    public void testParametersSchemaIsObjectTyped() {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            Map<String, Object> params = t.getParameters();
            if (params == null) {
                continue;
            }
            assertEquals("parameters schema for '" + t.getName()
                    + "' must declare type=object",
                    "object", params.get("type"));
        }
    }

    public void testDescriptionsAreNonEmpty() {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            String d = t.getDescription();
            assertNotNull("description for '" + t.getName()
                    + "' must not be null", d);
            assertTrue("description for '" + t.getName()
                    + "' must be non-empty", d.length() > 0);
        }
    }

    // -----------------------------------------------------------------
    // Per-tool schema spot checks
    // -----------------------------------------------------------------

    public void testAddClassRequiresNameXY() {
        ToolDefinition t = findByName("add_class");
        assertRequired(t, "name", "x", "y");
        assertPropertyType(t, "x", "integer");
        assertPropertyType(t, "y", "integer");
        assertPropertyType(t, "name", "string");
        assertPropertyType(t, "stereotype", "string");
    }

    public void testAddInterfaceRequiresNameXY() {
        ToolDefinition t = findByName("add_interface");
        assertRequired(t, "name", "x", "y");
    }

    public void testAddAttributeRequiresClassNameNameType() {
        ToolDefinition t = findByName("add_attribute");
        assertRequired(t, "className", "name", "type");
    }

    public void testAddOperationRequiresClassNameName() {
        ToolDefinition t = findByName("add_operation");
        assertRequired(t, "className", "name");
        assertPropertyType(t, "returnType", "string");
    }

    public void testAddAssociationRequiresClassAClassB() {
        ToolDefinition t = findByName("add_association");
        assertRequired(t, "classA", "classB");
        assertPropertyType(t, "multA", "string");
        assertPropertyType(t, "multB", "string");
    }

    public void testAddGeneralizationRequiresSubclassSuperclass() {
        ToolDefinition t = findByName("add_generalization");
        assertRequired(t, "subclass", "superclass");
    }

    public void testAddDependencyRequiresClientSupplier() {
        ToolDefinition t = findByName("add_dependency");
        assertRequired(t, "client", "supplier");
    }

    public void testRenameClassRequiresOldNewName() {
        ToolDefinition t = findByName("rename_class");
        assertRequired(t, "oldName", "newName");
    }

    public void testDeleteClassRequiresName() {
        ToolDefinition t = findByName("delete_class");
        assertRequired(t, "name");
    }

    public void testListClassesTakesNoArgs() {
        ToolDefinition t = findByName("list_classes");
        Map<String, Object> params = t.getParameters();
        assertNotNull("list_classes should still declare an empty"
                + " parameters object", params);
        assertEquals("object", params.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>)
                params.get("properties");
        assertTrue("list_classes properties must be empty, got "
                + (props == null ? "null" : props.keySet()),
                props == null || props.isEmpty());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static ToolDefinition findByName(String name) {
        for (ToolDefinition t : ClassDiagramTools.all()) {
            if (name.equals(t.getName())) {
                return t;
            }
        }
        fail("no tool named '" + name + "' in catalogue");
        return null;
    }

    private static Map<String, Object> paramsOf(ToolDefinition t) {
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>)
                t.getParameters().get("properties");
        return props;
    }

    private static List<Object> requiredOf(ToolDefinition t) {
        @SuppressWarnings("unchecked")
        List<Object> req = (List<Object>)
                t.getParameters().get("required");
        return req == null
                ? Collections.<Object>emptyList() : req;
    }

    private static void assertRequired(ToolDefinition t, String... keys) {
        List<Object> got = requiredOf(t);
        Set<String> gotSet = new HashSet<String>();
        for (Object o : got) {
            gotSet.add(String.valueOf(o));
        }
        Set<String> want = new HashSet<String>(Arrays.asList(keys));
        assertEquals("required fields for '" + t.getName() + "'",
                want, gotSet);
    }

    private static void assertPropertyType(
            ToolDefinition t, String propName, String expectedType) {
        Map<String, Object> props = paramsOf(t);
        assertNotNull("'" + t.getName() + "' must have properties",
                props);
        assertTrue("'" + t.getName() + "' must declare parameter '"
                + propName + "'", props.containsKey(propName));
        @SuppressWarnings("unchecked")
        Map<String, Object> pmap = (Map<String, Object>)
                props.get(propName);
        assertEquals("parameter '" + propName + "' of '"
                + t.getName() + "' type", expectedType,
                pmap.get("type"));
    }
}
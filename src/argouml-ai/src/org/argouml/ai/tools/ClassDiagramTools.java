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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue of the 10 OpenAI Chat Completions function-calling tool
 * definitions the AI sidecar is allowed to invoke on the current
 * class diagram. Each {@link ToolDefinition} carries:
 *
 * <ul>
 *   <li>a stable {@code name} the parser in
 *       {@link org.argouml.ai.ops.PlannedOpParser} switches on;</li>
 *   <li>a Chinese-language {@code description} for the LLM (encoded
 *       as Java Unicode escapes so this source file remains
 *       ISO-8859-1 clean per AGENTS.md);</li>
 *   <li>a JSON Schema {@code parameters} object whose property keys
 *       exactly match the keys
 *       {@link org.argouml.ai.ops.PlannedOpParser} reads. Mismatches
 *       here would silently break end-to-end LLM \u2192 parser flow,
 *       so the schema is deliberately a thin layer over the parser's
 *       expected fields (no design-doc-only parameters).</li>
 * </ul>
 *
 * <p>{@link #all()} is the only entry point; the list is unmodifiable
 * but the {@code ToolDefinition} objects themselves are mutable so
 * tests can inspect them.
 */
public final class ClassDiagramTools {

    private ClassDiagramTools() {
        // utility class
    }

    /**
     * @return the canonical 10-tool catalogue, in declaration order.
     *         The returned list is unmodifiable.
     */
    public static List<ToolDefinition> all() {
        List<ToolDefinition> out = new ArrayList<ToolDefinition>(10);
        out.add(listClasses());
        out.add(addClass());
        out.add(addInterface());
        out.add(addAttribute());
        out.add(addOperation());
        out.add(addAssociation());
        out.add(addGeneralization());
        out.add(addDependency());
        out.add(renameClass());
        out.add(deleteClass());
        return Collections.unmodifiableList(out);
    }

    // -----------------------------------------------------------------
    // Per-tool builders
    // -----------------------------------------------------------------

    private static ToolDefinition listClasses() {
        Map<String, Object> schema = objectSchema(
                Collections.<String, Object>emptyMap(),
                Collections.<String>emptyList());
        return new ToolDefinition(
                "list_classes",
                "\u67e5\u8be2\u5f53\u524d\u7c7b\u56fe\u5df2\u6709"
                + "\u54ea\u4e9b\u7c7b\u548c\u63a5\u53e3",
                schema);
    }

    private static ToolDefinition addClass() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("name",      stringParam("\u7c7b\u540d"));
        props.put("x",         intParam("\u753b\u5e03 X \u5750\u6807"));
        props.put("y",         intParam("\u753b\u5e03 Y \u5750\u6807"));
        props.put("stereotype", stringParam(
                "\u53ef\u9009\u7684\u6784\u9020\u578b"));
        props.put("isAbstract", boolParam(
                "\u662f\u5426\u4e3a\u62bd\u8c61\u7c7b\uff0c\u9ed8\u8ba4 false"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"name", "x", "y"}));
        return new ToolDefinition(
                "add_class",
                "\u65b0\u5efa\u4e00\u4e2a UML \u7c7b\u5e76\u653e\u5230"
                + "\u753b\u5e03\u6307\u5b9a\u5750\u6807",
                schema);
    }

    private static ToolDefinition addInterface() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("name",      stringParam("\u63a5\u53e3\u540d"));
        props.put("x",         intParam("\u753b\u5e03 X \u5750\u6807"));
        props.put("y",         intParam("\u753b\u5e03 Y \u5750\u6807"));
        props.put("stereotype", stringParam(
                "\u53ef\u9009\u7684\u6784\u9020\u578b"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"name", "x", "y"}));
        return new ToolDefinition(
                "add_interface",
                "\u65b0\u5efa\u4e00\u4e2a UML \u63a5\u53e3",
                schema);
    }

    private static ToolDefinition addAttribute() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("className",  stringParam(
                "\u76ee\u6807\u5df2\u6709\u7c7b\u7684\u540d\u5b57"));
        props.put("name",       stringParam("\u5c5e\u6027\u540d"));
        props.put("type",       stringParam(
                "\u5c5e\u6027\u7684\u7c7b\u578b\uff0c\u5982 int\u3001"
                + "String\u3001Date"));
        props.put("visibility", stringParam(
                "\u53ef\u89c1\u6027\uff0c\u53ef\u9009 public / private"
                + " / protected / package"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"className", "name", "type"}));
        return new ToolDefinition(
                "add_attribute",
                "\u7ed9\u5df2\u6709\u7c7b\u589e\u52a0\u4e00\u4e2a\u5c5e"
                + "\u6027\uff08\u542b\u7c7b\u578b\u548c\u53ef\u89c1"
                + "\u6027\uff09",
                schema);
    }

    private static ToolDefinition addOperation() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("className",  stringParam(
                "\u76ee\u6807\u5df2\u6709\u7c7b\u7684\u540d\u5b57"));
        props.put("name",       stringParam("\u65b9\u6cd5\u540d"));
        props.put("returnType", stringParam(
                "\u8fd4\u56de\u7c7b\u578b\uff0c\u4e0d\u4f20\u53c2\u6570"
                + "\u65f6\u4e3a void"));
        props.put("visibility", stringParam(
                "\u53ef\u89c1\u6027\uff0c\u53ef\u9009 public / private"
                + " / protected / package"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"className", "name"}));
        return new ToolDefinition(
                "add_operation",
                "\u7ed9\u5df2\u6709\u7c7b\u589e\u52a0\u4e00\u4e2a\u65b9"
                + "\u6cd5\uff08\u64cd\u4f5c\uff09",
                schema);
    }

    private static ToolDefinition addAssociation() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("classA", stringParam(
                "\u5173\u8054\u7aef A \u7684\u7c7b\u540d"));
        props.put("classB", stringParam(
                "\u5173\u8054\u7aef B \u7684\u7c7b\u540d"));
        props.put("labelA", stringParam(
                "A \u7aef\u89d2\u8272\u540d\uff0c\u53ef\u9009"));
        props.put("labelB", stringParam(
                "B \u7aef\u89d2\u8272\u540d\uff0c\u53ef\u9009"));
        props.put("multA",  stringParam(
                "A \u7aef\u91cd\u6570\uff0c\u5982 1\u3001*\u3001"
                + "0..1\u30011..*"));
        props.put("multB",  stringParam(
                "B \u7aef\u91cd\u6570\uff0c\u5982 1\u3001*\u3001"
                + "0..1\u30011..*"));
        props.put("name",   stringParam("\u5173\u8054\u540d\u79f0\uff0c"
                + "\u53ef\u9009"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"classA", "classB"}));
        return new ToolDefinition(
                "add_association",
                "\u5728\u4e24\u4e2a\u7c7b\u4e4b\u95f4\u5efa\u7acb\u5173"
                + "\u8054\uff0c\u53ef\u6307\u5b9a\u4e24\u7aef\u89d2\u8272"
                + "\u540d\u4e0e\u91cd\u6570",
                schema);
    }

    private static ToolDefinition addGeneralization() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("subclass",   stringParam(
                "\u5b50\u7c7b\uff08\u7ee7\u627f\u65b9\uff09\u7c7b\u540d"));
        props.put("superclass", stringParam(
                "\u7236\u7c7b\uff08\u88ab\u7ee7\u627f\u65b9\uff09"
                + "\u7c7b\u540d"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"subclass", "superclass"}));
        return new ToolDefinition(
                "add_generalization",
                "\u5efa\u7acb\u4e24\u4e2a\u7c7b\u4e4b\u95f4\u7684\u6cdb"
                + "\u5316\uff08\u7ee7\u627f\uff09\u5173\u7cfb",
                schema);
    }

    private static ToolDefinition addDependency() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("client",   stringParam(
                "\u4f9d\u8d56\u65b9\uff08\u5ba2\u6237\u7aef\uff09"
                + "\u7c7b\u540d"));
        props.put("supplier", stringParam(
                "\u88ab\u4f9d\u8d56\u65b9\uff08\u4f9b\u5e94\u65b9\uff09"
                + "\u7c7b\u540d"));
        props.put("name",     stringParam(
                "\u4f9d\u8d56\u540d\u79f0\uff0c\u53ef\u9009"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"client", "supplier"}));
        return new ToolDefinition(
                "add_dependency",
                "\u5efa\u7acb\u4e24\u4e2a\u7c7b\u4e4b\u95f4\u7684\u4f9d"
                + "\u8d56\u5173\u7cfb",
                schema);
    }

    private static ToolDefinition renameClass() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("oldName", stringParam("\u65e7\u7684\u7c7b\u540d"));
        props.put("newName", stringParam("\u65b0\u7684\u7c7b\u540d"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"oldName", "newName"}));
        return new ToolDefinition(
                "rename_class",
                "\u4fee\u6539\u4e00\u4e2a\u7c7b\u7684\u540d\u5b57",
                schema);
    }

    private static ToolDefinition deleteClass() {
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("name", stringParam("\u8981\u5220\u9664\u7684\u7c7b"
                + "\u540d"));
        Map<String, Object> schema = objectSchema(props,
                Arrays.asList(new String[] {"name"}));
        return new ToolDefinition(
                "delete_class",
                "\u5220\u9664\u4e00\u4e2a\u7c7b\uff08\u542b\u753b\u5e03"
                + "\u4e0a\u7684\u56fe\u8282\u70b9\uff09",
                schema);
    }

    // -----------------------------------------------------------------
    // Schema helpers
    // -----------------------------------------------------------------

    /**
     * Wrap a property map + required list as a JSON Schema object:
     * {@code {"type":"object", "properties":..., "required":[...]}}.
     * The required list is omitted when empty.
     */
    private static Map<String, Object> objectSchema(
            Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", new ArrayList<String>(required));
        }
        return schema;
    }

    /**
     * @return a {@code {"type":"string","description":...}} schema
     *         fragment. Description text must already be supplied as
     *         a literal {@code String} (use Java Unicode escapes
     *         to keep the source file ASCII-only).
     */
    private static Map<String, Object> stringParam(String description) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "string");
        p.put("description", description);
        return p;
    }

    /**
     * @return a {@code {"type":"integer","description":...}} schema
     *         fragment.
     */
    private static Map<String, Object> intParam(String description) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "integer");
        p.put("description", description);
        return p;
    }

    /**
     * @return a {@code {"type":"boolean","description":...}} schema
     *         fragment.
     */
    private static Map<String, Object> boolParam(String description) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("type", "boolean");
        p.put("description", description);
        return p;
    }
}
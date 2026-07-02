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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.infrastructure.json.JsonMini;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.GraphModel;

/**
 * Captures a {@link ArgoDiagram} as a JSON-serializable POJO that
 * downstream tools (the LLM sidecar, snapshot diffs, project save
 * payloads, etc.) can consume without a live ArgoUML model.
 *
 * <p>The shape mirrors design section 3.4:
 * <pre>{@code
 * {
 *   "diagram": {"id":"d1","type":"Class","namespace":"MyModel"},
 *   "classes": [
 *     {"name":"Customer","attrs":["id:int","name:String"],
 *      "ops":["save():void"]},
 *     {"name":"Order","attrs":["id:int","date:Date"],"ops":[]}
 *   ],
 *   "associations": [{"a":"Customer","b":"Order",
 *                     "multA":"1","multB":"0..*"}]
 * }
 * }</pre>
 *
 * <p>{@link #snapshot(ArgoDiagram)} is the only entry point. It walks
 * the diagram's graph model (no presentation / Fig layer) so the
 * output is decoupled from rendering state and from any single
 * UI selection.
 *
 * <p>Scope (MVP):
 * <ul>
 *   <li>classifiers limited to UML {@code Class} and {@code Interface};</li>
 *   <li>edges limited to UML {@code Association};</li>
 *   <li>attribute / operation signatures use the compact
 *       {@code "name:type"} / {@code "name(params):returnType"} shape
 *       shown above;</li>
 *   <li>multiplicities are emitted as UML-style strings
 *       ({@code "1"}, {@code "0..*"}, etc.). A null multiplicity
 *       collapses to the empty string.</li>
 * </ul>
 */
public final class ProjectSnapshot {

    private ProjectSnapshot() {
    }

    /**
     * Snapshot {@code diagram} into a POJO.
     *
     * <p>{@code diagram.getGraphModel().getNodes()} yields the model
     * elements represented on the diagram (not Figs); the same for
     * edges. Any element not classified as a class, interface, or
     * association is skipped silently so the snapshot stays focused
     * on what the LLM needs to reason about.
     *
     * @param diagram the diagram to snapshot; must not be {@code null}.
     * @return a {@link Snapshot} whose {@link Snapshot#toJson()}
     *         produces the JSON shown in the class Javadoc.
     * @throws IllegalArgumentException if {@code diagram} is
     *         {@code null} or its graph model returns {@code null}.
     */
    public static Snapshot snapshot(ArgoDiagram diagram) {
        if (diagram == null) {
            throw new IllegalArgumentException("diagram must not be null");
        }
        GraphModel gm = diagram.getGraphModel();
        if (gm == null) {
            throw new IllegalArgumentException(
                    "diagram.getGraphModel() must not be null");
        }

        Snapshot out = new Snapshot();
        out.diagram.put("id", "d" + Integer.toHexString(
                System.identityHashCode(diagram)));
        out.diagram.put("type", "Class");
        Object ns = diagram.getNamespace();
        out.diagram.put("namespace", ns == null
                ? ""
                : stringOrEmpty(Model.getFacade().getName(ns)));

        Facade facade = Model.getFacade();

        List nodes = gm.getNodes();
        if (nodes != null) {
            for (Iterator it = nodes.iterator(); it.hasNext();) {
                Object node = it.next();
                if (facade.isAClass(node)) {
                    out.classes.add(extractClass(node));
                } else if (facade.isAInterface(node)) {
                    out.classes.add(extractInterface(node));
                }
            }
        }

        List edges = gm.getEdges();
        if (edges != null) {
            for (Iterator it = edges.iterator(); it.hasNext();) {
                Object edge = it.next();
                if (facade.isAAssociation(edge)) {
                    out.associations.add(extractAssociation(edge));
                }
            }
        }

        return out;
    }

    // -----------------------------------------------------------------
    // Extractors
    // -----------------------------------------------------------------

    private static Map<String, Object> extractClass(Object cls) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("name", stringOrEmpty(Model.getFacade().getName(cls)));

        List<String> attrs = new ArrayList<String>();
        Collection classifierAttrs = Model.getFacade().getAttributes(cls);
        if (classifierAttrs != null) {
            for (Iterator it = classifierAttrs.iterator(); it.hasNext();) {
                attrs.add(extractAttribute(it.next()));
            }
        }
        out.put("attrs", attrs);

        List<String> ops = new ArrayList<String>();
        Collection classifierOps = Model.getFacade().getOperations(cls);
        if (classifierOps != null) {
            for (Iterator it = classifierOps.iterator(); it.hasNext();) {
                ops.add(extractOperation(it.next()));
            }
        }
        out.put("ops", ops);
        return out;
    }

    private static Map<String, Object> extractInterface(Object iface) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("name", stringOrEmpty(Model.getFacade().getName(iface)));
        // Interfaces have no owned attributes; emit empty list for
        // shape consistency with the class branch.
        out.put("attrs", new ArrayList<String>());

        List<String> ops = new ArrayList<String>();
        Collection ifaceOps = Model.getFacade().getOperations(iface);
        if (ifaceOps != null) {
            for (Iterator it = ifaceOps.iterator(); it.hasNext();) {
                ops.add(extractOperation(it.next()));
            }
        }
        out.put("ops", ops);
        return out;
    }

    private static Map<String, Object> extractAssociation(Object assoc) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        Collection ends = Model.getFacade().getConnections(assoc);
        if (ends == null || ends.size() < 2) {
            // Unary / n-ary or malformed: still emit the shell so the
            // consumer sees the association is present.
            out.put("a", "");
            out.put("b", "");
            out.put("multA", "");
            out.put("multB", "");
            return out;
        }
        Object[] endsArr = ends.toArray();
        Object endA = endsArr[0];
        Object endB = endsArr[1];
        out.put("a", typeName(Model.getFacade().getType(endA)));
        out.put("b", typeName(Model.getFacade().getType(endB)));
        out.put("multA", formatMultiplicity(
                Model.getFacade().getMultiplicity(endA)));
        out.put("multB", formatMultiplicity(
                Model.getFacade().getMultiplicity(endB)));
        return out;
    }

    private static String extractAttribute(Object attr) {
        String name = stringOrEmpty(Model.getFacade().getName(attr));
        String type = typeName(Model.getFacade().getType(attr));
        return name + ":" + type;
    }

    private static String extractOperation(Object op) {
        StringBuilder sb = new StringBuilder();
        sb.append(stringOrEmpty(Model.getFacade().getName(op)));
        sb.append('(');
        Collection params = Model.getFacade().getParameters(op);
        Facade facade = Model.getFacade();
        if (params != null) {
            boolean first = true;
            for (Iterator it = params.iterator(); it.hasNext();) {
                Object p = it.next();
                if (facade.isReturn(p)) {
                    // Return parameters appear in getParameters() but
                    // are emitted via the trailing ":returnType" instead.
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(extractParameter(p));
            }
        }
        sb.append(')').append(':');
        sb.append(returnTypeName(op));
        return sb.toString();
    }

    private static String extractParameter(Object param) {
        String name = stringOrEmpty(Model.getFacade().getName(param));
        String type = typeName(Model.getFacade().getType(param));
        return name + ":" + type;
    }

    /**
     * Resolves the return type of {@code op}. We pull the type off
     * the first return parameter rather than asking the operation
     * directly, because the model facade does not expose a
     * {@code getReturnType()} method - the canonical return-type
     * accessor is {@code CoreHelper.getReturnParameters()} which
     * returns a list (a UML behavioral feature can in principle carry
     * several return parameters).
     */
    private static String returnTypeName(Object op) {
        Collection rets = Model.getCoreHelper().getReturnParameters(op);
        if (rets == null || rets.isEmpty()) {
            return "void";
        }
        Object first = rets.iterator().next();
        Object type = Model.getFacade().getType(first);
        return type == null ? "void" : typeName(type);
    }

    // -----------------------------------------------------------------
    // Formatting helpers
    // -----------------------------------------------------------------

    /**
     * @param mult a multiplicity object, or {@code null}.
     * @return {@code "1"} for {@code 1..1}, {@code "0..*"} for the
     *         unbounded range, {@code "lo..hi"} for {@code lo != hi},
     *         {@code "lo"} for the single-point range, or the empty
     *         string if {@code mult} is {@code null}.
     */
    static String formatMultiplicity(Object mult) {
        if (mult == null) {
            return "";
        }
        int lower = Model.getFacade().getLower(mult);
        int upper = Model.getFacade().getUpper(mult);
        return formatMultiplicity(lower, upper);
    }

    /**
     * @return the compact UML-style string for the given bounds.
     */
    static String formatMultiplicity(int lower, int upper) {
        if (upper < 0) {
            return lower + "..*";
        }
        if (lower == upper) {
            return String.valueOf(lower);
        }
        return lower + ".." + upper;
    }

    private static String typeName(Object type) {
        if (type == null) {
            return "";
        }
        return stringOrEmpty(Model.getFacade().getName(type));
    }

    private static String stringOrEmpty(String s) {
        return s == null ? "" : s;
    }

    // -----------------------------------------------------------------
    // POJO
    // -----------------------------------------------------------------

    /**
     * Mutable POJO returned by {@link ProjectSnapshot#snapshot}. The
     * {@link #toJson()} method serializes the three top-level keys
     * ({@code diagram}, {@code classes}, {@code associations}) in a
     * fixed order so repeated snapshots are byte-stable.
     */
    public static class Snapshot {

        private final Map<String, Object> diagram =
                new LinkedHashMap<String, Object>();
        private final List<Map<String, Object>> classes =
                new ArrayList<Map<String, Object>>();
        private final List<Map<String, Object>> associations =
                new ArrayList<Map<String, Object>>();

        public Map<String, Object> getDiagram() {
            return diagram;
        }

        public List<Map<String, Object>> getClasses() {
            return classes;
        }

        public List<Map<String, Object>> getAssociations() {
            return associations;
        }

        /**
         * @return the JSON encoding of this snapshot using the
         *         {@link JsonMini} formatter. The result is suitable
         *         for {@code JsonMini.parse} round-tripping.
         */
        public String toJson() {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("diagram", diagram);
            out.put("classes", classes);
            out.put("associations", associations);
            return JsonMini.stringify(out);
        }

        @Override
        public String toString() {
            return toJson();
        }
    }
}
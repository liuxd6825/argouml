/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common.handlers.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Enumerates the diagrams in the current project. Emits a JSON array
 * of {@code {name, kind, namespace}} triples. Sorts by name for
 * deterministic output. When no project is open, responds 404 with
 * code {@code PROJECT_NOT_FOUND} so the client can distinguish
 * "empty model" from "server misrouted the request".
 *
 * <p>{@code kind} is the lowercase wire value of the diagram type
 * (e.g. {@code "class"}); MVP only knows class diagrams but the
 * field is present so the response shape is stable across future
 * kinds.</p>
 */
public final class ListDiagramsHandler implements IRequestHandler {

    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p == null) {
            throw new NotFoundException("PROJECT_NOT_FOUND",
                    "No project is currently open");
        }
        List<ArgoDiagram> diagrams = p.getDiagramList();
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (diagrams != null) {
            for (ArgoDiagram d : diagrams) {
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("name", d.getName());
                entry.put("kind", kindOf(d));
                Object ns = d.getNamespace();
                entry.put("namespace", ns == null
                        ? ""
                        : stringOrEmpty(ns.toString()));
                out.add(entry);
            }
        }
        // Sort by name for stable wire output.
        java.util.Collections.sort(out, new java.util.Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                String an = (String) a.get("name");
                String bn = (String) b.get("name");
                if (an == null) return bn == null ? 0 : -1;
                if (bn == null) return 1;
                return an.compareTo(bn);
            }
        });
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }

    /**
     * @return the lowercase short name of the diagram's class, or
     *         {@code "unknown"} when the class cannot be resolved
     */
    private static String kindOf(ArgoDiagram d) {
        if (d == null) {
            return "unknown";
        }
        String name = d.getClass().getSimpleName();
        // UMLClassDiagram -> "class"; we keep it simple: lowercase,
        // strip the "UML" prefix and the "Diagram" suffix.
        name = name.replaceFirst("^UML", "");
        name = name.replaceFirst("Diagram$", "");
        if (name.isEmpty()) {
            return "unknown";
        }
        return name.toLowerCase();
    }

    private static String stringOrEmpty(String s) {
        return s == null ? "" : s;
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.classdiagram.handlers.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Helper to convert a list of {@link ClassDiagramService.ClassView}
 * into the wire shape {@code [{name, x, y}, ...]} used by the
 * layout endpoints. Kept package-private and stateless so both
 * {@link GetLayoutHandler} and {@link PostLayoutHandler} share it
 * without duplicating the field-name conventions.
 */
final class LayoutPayload {

    private LayoutPayload() {
        // utility class
    }

    static List<Map<String, Object>> positions(List<ClassDiagramService.ClassView> views) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (views == null) {
            return out;
        }
        for (ClassDiagramService.ClassView v : views) {
            Map<String, Object> p = new LinkedHashMap<String, Object>();
            p.put("name", v.name);
            p.put("x", v.x);
            p.put("y", v.y);
            out.add(p);
        }
        return out;
    }

    static Map<String, Object> envelope(String diagram, int count,
                                        List<Map<String, Object>> positions,
                                        String countField) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("diagram", diagram);
        out.put(countField, count);
        out.put("classes", positions);
        return out;
    }

    static String toJson(Map<String, Object> envelope) {
        return JsonWriter.ok(envelope);
    }
}

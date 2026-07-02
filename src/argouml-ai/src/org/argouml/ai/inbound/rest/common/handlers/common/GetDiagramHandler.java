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

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Returns the meta for a single diagram, looked up by name from the
 * URL path parameter {@code d}. The body shape mirrors one entry
 * from {@link ListDiagramsHandler}:
 * {@code {ok:true, data:{name, kind, namespace}}.
 *
 * <p>{@code DiagramLocator.byName} throws
 * {@link DiagramLocator.DiagramNotFoundException} on miss; we
 * translate that into the application-layer
 * {@link NotFoundException} with code {@code DIAGRAM_NOT_FOUND} so
 * the dispatcher's exception → HTTP mapping surfaces a 404.</p>
 */
public final class GetDiagramHandler implements IRequestHandler {

    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String name = pathParams == null ? null : pathParams.get("d");
        ArgoDiagram d;
        try {
            d = DiagramLocator.byName(name);
        } catch (DiagramLocator.DiagramNotFoundException ex) {
            throw new NotFoundException("DIAGRAM_NOT_FOUND", ex.getMessage());
        }
        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("name", d.getName());
        entry.put("kind", kindOf(d));
        Object ns = d.getNamespace();
        entry.put("namespace", ns == null ? "" : ns.toString());
        return ResponseEnvelope.json(200, JsonWriter.ok(entry));
    }

    private static String kindOf(ArgoDiagram d) {
        if (d == null) {
            return "unknown";
        }
        String name = d.getClass().getSimpleName();
        name = name.replaceFirst("^UML", "");
        name = name.replaceFirst("Diagram$", "");
        if (name.isEmpty()) {
            return "unknown";
        }
        return name.toLowerCase();
    }
}
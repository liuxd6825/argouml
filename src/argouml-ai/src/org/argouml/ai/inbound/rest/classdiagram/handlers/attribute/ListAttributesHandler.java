/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.attribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/classes/{c}/attributes}. Returns the
 * read shape for every attribute on the named class on the named
 * diagram, wrapped in the standard
 * {@code {ok:true, data:[...]}} envelope.
 *
 * <p>Each entry in {@code data} is shaped as
 * {@code {"name":"<simpleName>","type":"<typeName>"}}; the read
 * pipeline reuses {@link ClassDiagramService.ClassView#attributes}
 * which carries attributes encoded as {@code "name:type"} strings
 * (the same shape {@code ProjectSnapshot} uses for the AI read
 * path). Visibility is not part of {@code ClassView} so it is
 * intentionally absent from the list payload - exposing it would
 * require a second model lookup per attribute, which the read
 * pipeline currently does not perform.</p>
 *
 * <p>Read-only; does not open an {@code UndoScope} on the model.</p>
 */
public final class ListAttributesHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public ListAttributesHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagramName = pathParams == null ? null : pathParams.get("d");
        String className = pathParams == null ? null : pathParams.get("c");
        ClassDiagramService.ClassView v =
                svc.getClass(diagramName, className);
        List<Map<String, Object>> out =
                new ArrayList<Map<String, Object>>(v.attributes.size());
        for (String encoded : v.attributes) {
            out.add(toView(encoded));
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }

    private static Map<String, Object> toView(String encoded) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        int colon = encoded.indexOf(':');
        if (colon < 0) {
            m.put("name", encoded);
            m.put("type", "");
        } else {
            m.put("name", encoded.substring(0, colon));
            m.put("type", encoded.substring(colon + 1));
        }
        return m;
    }
}
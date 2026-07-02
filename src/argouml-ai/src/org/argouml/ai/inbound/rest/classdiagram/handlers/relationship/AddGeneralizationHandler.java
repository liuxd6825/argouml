/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.relationship;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/generalizations}. Body shape:
 * <pre>
 *   { "subclass":"Order", "superclass":"Document" }
 * </pre>
 *
 * <p>The wire field names ({@code subclass} / {@code superclass}) map
 * onto the service's parameter names ({@code child} / {@code parent})
 * - the substitution is done here so the client-facing vocabulary
 * stays UML-natural while the service-side contract keeps the
 * direction-neutral pair the domain layer uses.</p>
 *
 * <p>Both fields are required; a missing or empty value surfaces as
 * {@code INVALID_NAME} (400) from the service. Returns 201 with the
 * new generalisation's resolved {@code child}|{@code parent} pair
 * and an {@code "id"} string of the form {@code "child|parent"} - the
 * same shape {@code ClassDiagramService.deleteRelationship} accepts,
 * so the client can immediately follow up with a {@code DELETE}
 * carrying the {@code id} (and {@code ?type=generalization}).</p>
 */
public final class AddGeneralizationHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public AddGeneralizationHandler(ClassDiagramService svc) {
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
        Map<String, Object> json = JsonBodyReader.readMap(body);
        String child = strEmpty(json.get("subclass"));
        String parent = strEmpty(json.get("superclass"));
        svc.addGeneralization(diagramName, child, parent);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("id", child + "|" + parent);
        v.put("child", child);
        v.put("parent", parent);
        return ResponseEnvelope.json(201, JsonWriter.ok(v));
    }

    private static String strEmpty(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString();
        return (s.isEmpty()) ? null : s;
    }
}
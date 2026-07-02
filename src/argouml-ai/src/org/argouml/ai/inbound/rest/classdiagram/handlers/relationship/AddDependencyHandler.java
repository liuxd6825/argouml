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
 * Handler for {@code POST /d/{d}/dependencies}. Body shape:
 * <pre>
 *   { "client":"Order", "supplier":"Inventory" }
 * </pre>
 *
 * <p>Both fields are required; a missing or empty value surfaces as
 * {@code INVALID_NAME} (400) from the service. The wire field names
 * match the service's parameter names directly.</p>
 *
 * <p>Returns 201 with the new dependency's resolved client /
 * supplier pair and an {@code "id"} string of the form
 * {@code "client|supplier"} - the same shape
 * {@code ClassDiagramService.deleteRelationship} accepts, so the
 * client can immediately follow up with a {@code DELETE} carrying
 * the {@code id} (and {@code ?type=dependency}).</p>
 */
public final class AddDependencyHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public AddDependencyHandler(ClassDiagramService svc) {
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
        String client = strEmpty(json.get("client"));
        String supplier = strEmpty(json.get("supplier"));
        svc.addDependency(diagramName, client, supplier);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("id", client + "|" + supplier);
        v.put("client", client);
        v.put("supplier", supplier);
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
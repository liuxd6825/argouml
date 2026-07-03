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
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /project/packages}. Body shape:
 * <pre>{ "name": "domain", "parent": "domain.model" (optional) }</pre>
 *
 * <p>Returns 201 + the new package's view on success. 400 on
 * missing/empty name, bad JSON body, or missing parent package.
 * 409 if a sibling package with that name already exists.</p>
 */
public final class CreatePackageHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public CreatePackageHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        Map<String, Object> json;
        try {
            json = JsonBodyReader.readMap(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    ex.getMessage() == null ? "malformed JSON body" : ex.getMessage()));
        }
        if (json == null || json.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "Request body must be a JSON object"));
        }
        String name = json.get("name") == null ? null
                : json.get("name").toString();
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Field 'name' is required and must be non-empty"));
        }
        String parent = json.get("parent") == null ? null
                : json.get("parent").toString();
        ClassDiagramService.PackageView pv = svc.createPackage(name, parent);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", pv.name);
        v.put("qualifiedName", pv.qualifiedName);
        v.put("parent", pv.parent);
        v.put("classCount", pv.classCount);
        return ResponseEnvelope.json(201, JsonWriter.ok(v));
    }
}

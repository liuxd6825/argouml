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

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /project/packages/{name}}. Returns 200 +
 * the package view, or 404 if not found.
 */
public final class GetPackageHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public GetPackageHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String name = pathParams == null ? null : pathParams.get("name");
        try {
            ClassDiagramService.PackageView pv = svc.getPackage(name);
            Map<String, Object> v = new LinkedHashMap<String, Object>();
            v.put("name", pv.name);
            v.put("qualifiedName", pv.qualifiedName);
            v.put("parent", pv.parent);
            v.put("classCount", pv.classCount);
            return ResponseEnvelope.json(200, JsonWriter.ok(v));
        } catch (org.argouml.ai.application.common.NotFoundException e) {
            return ResponseEnvelope.json(404, JsonError.of(e.code(), e.getMessage()));
        } catch (org.argouml.ai.application.common.InvalidArgumentException e) {
            return ResponseEnvelope.json(400, JsonError.of(e.code(), e.getMessage()));
        }
    }
}

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

import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /project/packages/{name}/classes/{c}}.
 * Moves class {@code c} into package {@code name}. 204 on
 * success; 400 if the class name is missing; 404 if either
 * the class or the target package is not found.
 */
public final class MoveClassToPackageHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public MoveClassToPackageHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String pkgName = pathParams == null ? null
                : pathParams.get("name");
        String className = pathParams == null ? null
                : pathParams.get("c");
        try {
            svc.moveClassToPackage(className, pkgName);
            Map<String, Object> v = new java.util.LinkedHashMap<String, Object>();
            v.put("package", pkgName);
            v.put("class", className);
            return ResponseEnvelope.json(200, JsonWriter.ok(v));
        } catch (org.argouml.ai.application.common.NotFoundException e) {
            return ResponseEnvelope.json(404, JsonError.of(e.code(), e.getMessage()));
        } catch (org.argouml.ai.application.common.InvalidArgumentException e) {
            return ResponseEnvelope.json(400, JsonError.of(e.code(), e.getMessage()));
        }
    }
}

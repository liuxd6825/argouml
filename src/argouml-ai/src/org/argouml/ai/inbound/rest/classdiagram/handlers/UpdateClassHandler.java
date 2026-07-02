/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code PUT /d/{d}/classes/{c}}. Body shape is a
 * partial subset of the create payload:
 * <pre>
 *   { "newName":"NewOrder", "x":120, "y":200,
 *     "stereotype":"entity", "isAbstract":true }
 * </pre>
 *
 * <p>Every field is optional. The handler distinguishes "absent"
 * from "explicitly the default value" so {@code isAbstract:false}
 * can clear an existing abstract flag and {@code newName:""} is
 * treated as "do not rename".</p>
 *
 * <p>Position ({@code x}/{@code y}) is moved only when BOTH are
 * present - this matches the service-side contract that requires
 * a complete coordinate pair.</p>
 */
public final class UpdateClassHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public UpdateClassHandler(ClassDiagramService svc) {
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
        Map<String, Object> json = JsonBodyReader.readMap(body);
        String newName = JsonFields.strEmpty(json.get("newName"));
        Integer x = JsonFields.intOpt(json.get("x"), 0);
        Integer y = JsonFields.intOpt(json.get("y"), 0);
        String stereotype = JsonFields.strEmpty(json.get("stereotype"));
        Boolean isAbstract = JsonFields.boolOpt(json.get("isAbstract"), false);

        ClassDiagramService.ClassElement ce = svc.updateClass(
                diagramName, className, newName, x, y, stereotype, isAbstract);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", ce.name);
        return ResponseEnvelope.json(200, JsonWriter.ok(v));
    }
}
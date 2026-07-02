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
 * Handler for {@code POST /d/{d}/classes}. Body shape:
 * <pre>
 *   { "name":"Order", "x":100, "y":100,
 *     "stereotype":"entity", "isAbstract":false }
 * </pre>
 *
 * <p>{@code name}, {@code x}, {@code y} are required in spirit
 * (the service throws {@code INVALID_NAME} on a null/empty name,
 * and the default placement is {@code (100,100)} when absent).
 * {@code stereotype} and {@code isAbstract} are optional.</p>
 *
 * <p>Returns 201 with the new class's simple name so the client
 * can immediately follow up with attribute/operation calls.</p>
 */
public final class CreateClassHandler implements IRequestHandler {

    private static final int DEFAULT_X = 100;
    private static final int DEFAULT_Y = 100;

    private final ClassDiagramService svc;

    public CreateClassHandler(ClassDiagramService svc) {
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
        String name = JsonFields.strEmpty(json.get("name"));
        int x = JsonFields.intVal(json.get("x"), DEFAULT_X);
        int y = JsonFields.intVal(json.get("y"), DEFAULT_Y);
        String stereotype = JsonFields.strEmpty(json.get("stereotype"));
        boolean isAbstract = JsonFields.boolVal(json.get("isAbstract"), false);

        ClassDiagramService.ClassElement ce = svc.createClass(
                diagramName, name, x, y, stereotype, isAbstract);
        return ResponseEnvelope.json(201, JsonWriter.ok(toView(ce)));
    }

    private static Map<String, Object> toView(
            ClassDiagramService.ClassElement ce) {
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", ce.name);
        return v;
    }
}
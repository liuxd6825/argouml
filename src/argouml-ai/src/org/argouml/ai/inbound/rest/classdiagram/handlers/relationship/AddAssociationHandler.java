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
 * Handler for {@code POST /d/{d}/associations}. Body shape:
 * <pre>
 *   { "classA":"Customer", "classB":"Order",
 *     "multA":"1", "multB":"0..*",
 *     "labelA":"places", "labelB":"placedBy" }
 * </pre>
 *
 * <p>Both class names are required (the service throws
 * {@code INVALID_NAME} on null/empty). Multiplicity and role-name
 * fields are optional - any null/empty value leaves that attribute
 * at its UML default. Unknown multiplicity strings surface from
 * {@code CoreHelper.setMultiplicity} as an
 * {@code IllegalArgumentException}, mapped to 500 by the
 * dispatcher.</p>
 *
 * <p>Returns 201 with the new association's resolved
 * {@code "a"|"b"} endpoint pair and an {@code "id"} string of the
 * form {@code "a|b"} - the same shape
 * {@code ClassDiagramService.deleteRelationship} accepts, so the
 * client can immediately follow up with a {@code DELETE} carrying
 * the {@code id} (and {@code ?type=association}).</p>
 */
public final class AddAssociationHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public AddAssociationHandler(ClassDiagramService svc) {
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
        String a = strEmpty(json.get("classA"));
        String b = strEmpty(json.get("classB"));
        String multA = strEmpty(json.get("multA"));
        String multB = strEmpty(json.get("multB"));
        String labelA = strEmpty(json.get("labelA"));
        String labelB = strEmpty(json.get("labelB"));
        svc.addAssociation(diagramName, a, b, multA, multB, labelA, labelB);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("id", a + "|" + b);
        v.put("a", a);
        v.put("b", b);
        v.put("multA", multA == null ? "" : multA);
        v.put("multB", multB == null ? "" : multB);
        v.put("labelA", labelA == null ? "" : labelA);
        v.put("labelB", labelB == null ? "" : labelB);
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
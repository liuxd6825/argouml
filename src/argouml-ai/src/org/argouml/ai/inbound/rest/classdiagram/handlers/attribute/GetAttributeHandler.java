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

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/classes/{c}/attributes/{a}}. Returns
 * one attribute of one class on one diagram as a
 * {@code {"name","type"}} object wrapped in the standard success
 * envelope.
 *
 * <p>Three error shapes map to 4xx:</p>
 * <ul>
 *   <li>{@code DIAGRAM_NOT_FOUND} (404) when {@code {d}} does not
 *       resolve on the current project;</li>
 *   <li>{@code CLASS_NOT_FOUND} (404) when {@code {c}} does not
 *       resolve on that diagram (raised by the service);</li>
 *   <li>{@code ATTRIBUTE_NOT_FOUND} (404) when no attribute of
 *       name {@code {a}} exists on the resolved class;</li>
 *   <li>{@code INVALID_NAME} (400) when {@code {a}} is empty or
 *       carries a character that cannot legally appear inside a
 *       URL path segment ({@code '/'} or {@code '%'}).</li>
 * </ul>
 *
 * <p>The dispatcher's {@code DiagramServiceException}-based mapping
 * keeps all four on the expected status; the codes differ so clients
 * can tell which side of the lookup missed.</p>
 */
public final class GetAttributeHandler implements IRequestHandler {

    private static final String CODE_INVALID_NAME = "INVALID_NAME";
    private static final String CODE_ATTRIBUTE_NOT_FOUND =
            "ATTRIBUTE_NOT_FOUND";

    private final ClassDiagramService svc;

    public GetAttributeHandler(ClassDiagramService svc) {
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
        String attrName = pathParams == null ? null : pathParams.get("a");
        requireValidName(attrName);
        ClassDiagramService.ClassView v =
                svc.getClass(diagramName, className);
        for (String encoded : v.attributes) {
            int colon = encoded.indexOf(':');
            String n = colon < 0 ? encoded : encoded.substring(0, colon);
            if (n.equals(attrName)) {
                return ResponseEnvelope.json(200,
                        JsonWriter.ok(toView(encoded)));
            }
        }
        throw new NotFoundException(CODE_ATTRIBUTE_NOT_FOUND,
                "Attribute '" + attrName + "' not found on class '"
                + className + "' on diagram '" + diagramName + "'");
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

    private static void requireValidName(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Attribute name must not be empty");
        }
        if (name.indexOf('/') >= 0 || name.indexOf('%') >= 0) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Attribute name must not contain '/' or '%'");
        }
    }
}
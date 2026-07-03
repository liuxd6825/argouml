/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.classdiagram.handlers.layout;

import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;

/**
 * Handler for {@code GET /d/{diagramName}/layout}. Returns the
 * current (x, y) of every class on the named diagram, sorted by
 * class name for stable client rendering.
 *
 * <p>Response (200):
 * <pre>
 *   {
 *     "ok": true,
 *     "data": {
 *       "diagram": "类图",
 *       "count": 5,
 *       "classes": [
 *         {"name": "公司", "x": 500, "y": 80},
 *         ...
 *       ]
 *     }
 *   }
 * </pre>
 *
 * <p>Errors: 404 DIAGRAM_NOT_FOUND when the diagram does not
 * exist.</p>
 */
public final class GetLayoutHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public GetLayoutHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        List<ClassDiagramService.ClassView> views = svc.getLayout(diagram);
        return ResponseEnvelope.json(200,
                LayoutPayload.toJson(LayoutPayload.envelope(
                        diagram, views.size(),
                        LayoutPayload.positions(views), "count")));
    }
}

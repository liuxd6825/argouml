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
 * Handler for {@code POST /d/{diagramName}/layout}. Runs
 * ArgoUML's {@link org.argouml.uml.diagram.static_structure.layout.ClassdiagramLayouter}
 * over the named diagram, repositioning every class fig into a
 * row/column grid. The change is wrapped in an {@code UndoScope}
 * so a single GUI undo reverts it.
 *
 * <p>Response (200) carries the new positions so the client can
 * re-render without a follow-up GET:
 * <pre>
 *   {
 *     "ok": true,
 *     "data": {
 *       "diagram": "类图",
 *       "moved": 5,
 *       "classes": [
 *         {"name": "公司", "x": 240, "y": 50},
 *         ...
 *       ]
 *     }
 *   }
 * </pre>
 *
 * <p>Errors:</p>
 * <ul>
 *   <li>404 DIAGRAM_NOT_FOUND when the diagram does not exist;</li>
 *   <li>400 UNSUPPORTED_DIAGRAM_TYPE when the diagram is not a
 *       class diagram (the only kind ArgoUML's
 *       {@code ClassdiagramLayouter} knows how to lay out).</li>
 * </ul>
 */
public final class PostLayoutHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public PostLayoutHandler(ClassDiagramService svc) {
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
        List<ClassDiagramService.ClassView> views = svc.reLayout(diagram);
        return ResponseEnvelope.json(200,
                LayoutPayload.toJson(LayoutPayload.envelope(
                        diagram, views.size(),
                        LayoutPayload.positions(views), "moved")));
    }
}

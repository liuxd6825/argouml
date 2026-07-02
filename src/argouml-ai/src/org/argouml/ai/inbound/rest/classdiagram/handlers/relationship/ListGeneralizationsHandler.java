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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.GraphModel;

/**
 * Handler for {@code GET /d/{d}/generalizations}. Walks the diagram's
 * graph model edges, keeps only those for which the facade returns
 * true for {@link Facade#isAGeneralization}, and emits one entry per
 * surviving edge.
 *
 * <p>Each entry is shaped as
 * {@code {"child":"<specific>","parent":"<general>"}}, with the
 * specific end resolved via {@code Facade.getSpecific} and the general
 * end via {@code Facade.getGeneral} (so the field names match the
 * UML generalisation direction: a {@code Generalization} whose
 * {@code parent} is {@code Animal} and {@code child} is {@code Dog}
 * yields {@code {"child":"Dog","parent":"Animal"}}).</p>
 *
 * <p>Read-only; does not open an {@code UndoScope} on the model.
 * The {@code ClassDiagramService} constructor argument is retained
 * for batch uniformity even though the list path does not call any
 * service method - the diagram is resolved through the same
 * {@link DiagramLocator} the service uses internally.</p>
 */
public final class ListGeneralizationsHandler implements IRequestHandler {

    @SuppressWarnings("unused")
    private final ClassDiagramService svc;

    public ListGeneralizationsHandler(ClassDiagramService svc) {
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
        ArgoDiagram d;
        try {
            d = DiagramLocator.byName(diagramName);
        } catch (DiagramLocator.DiagramNotFoundException ex) {
            throw new NotFoundException("DIAGRAM_NOT_FOUND", ex.getMessage());
        }
        GraphModel gm = d.getGraphModel();
        List<Map<String, Object>> out =
                new ArrayList<Map<String, Object>>();
        if (gm != null) {
            Collection edges = gm.getEdges();
            if (edges != null) {
                Facade facade = Model.getFacade();
                for (Iterator it = edges.iterator(); it.hasNext();) {
                    Object edge = it.next();
                    if (!facade.isAGeneralization(edge)) {
                        continue;
                    }
                    out.add(toView(facade, edge));
                }
            }
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }

    private static Map<String, Object> toView(Facade facade, Object gen) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("child", stringOrEmpty(
                facade.getName(facade.getSpecific(gen))));
        m.put("parent", stringOrEmpty(
                facade.getName(facade.getGeneral(gen))));
        return m;
    }

    private static String stringOrEmpty(String s) {
        return s == null ? "" : s;
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.common.AbstractDiagramServiceHelper;
import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Handler for {@code GET /d/{d}/sequencediagram/figs}.
 *
 * <p>Returns a diagnostic snapshot of the diagram's graph model and
 * visible-layer state. Used to verify that API mutations actually
 * create Figs in the diagram's layer (not just bookkeeping in the
 * model). Three counts are reported:</p>
 * <ul>
 *   <li>{@code nodes} — number of graph-model nodes (roles + lifelines +
 *       anything else added to the model)</li>
 *   <li>{@code edges} — number of graph-model edges (messages)</li>
 *   <li>{@code figs} — number of visible-layer figs (what the user
 *       actually sees in the canvas). If {@code figs == nodes +
 *       edges}, every element is rendered.</li>
 * </ul>
 */
public final class GetDiagramStateHandler implements IRequestHandler {

    private final SequenceDiagramService svc;

    public GetDiagramStateHandler(SequenceDiagramService svc) {
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
        try {
            AbstractDiagramServiceHelper.requireNonEmptyName(diagram);
        } catch (DiagramServiceException ex) {
            return ResponseEnvelope.json(400, JsonError.of(ex.code(),
                    ex.getMessage()));
        }
        org.argouml.uml.diagram.ArgoDiagram d;
        try {
            d = org.argouml.ai.domain.common.DiagramLocator.byName(diagram);
        } catch (Exception ex) {
            return ResponseEnvelope.json(404, JsonError.of("DIAGRAM_NOT_FOUND",
                    ex.getMessage()));
        }
        if (d == null) {
            throw new NotFoundException("DIAGRAM_NOT_FOUND",
                    "diagram not found: " + diagram);
        }
        if (!(d instanceof org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram)) {
            // we don't care about the concrete class, but the cast
            // is safe because SequenceDiagramGraphModel is on a
            // UMLSequenceDiagram instance — and that's what the user
            // would have created. Sequence is what the service
            // validates elsewhere.
        }
        java.util.Map<String, Object> out = new LinkedHashMap<String, Object>();
        org.tigris.gef.graph.GraphModel gm = d.getGraphModel();
        int nodeCount = 0, edgeCount = 0, figCount = 0;
        if (gm != null) {
            java.util.Collection nodes = gm.getNodes();
            java.util.Collection edges = gm.getEdges();
            if (nodes != null) {
                nodeCount = nodes.size();
            }
            if (edges != null) {
                edgeCount = edges.size();
            }
        }
        int nodeFigCount = 0;
        int edgeFigCount = 0;
        if (d.getLayer() != null) {
            java.util.Collection figs = d.getLayer().getContentsNoEdges();
            if (figs != null) {
                nodeFigCount = figs.size();
            }
            // Edge figs (FigMessage etc.) live in getContents(), not
            // getContentsNoEdges(). We count them separately so the
            // client can verify both nodes (role/lifeline) and edges
            // (messages) have visible Figs.
            // Rather than rely on isAMessage which may not match the
            // concrete FigMessage class in the MDR backend, count
            // edge figs as the difference between getContents() (all
            // figs) and getContentsNoEdges() (figs without edges).
            java.util.Collection allFigs = d.getLayer().getContents();
            int totalFigCount = 0;
            if (allFigs != null) {
                totalFigCount = allFigs.size();
            }
            edgeFigCount = totalFigCount - nodeFigCount;
            figCount = nodeFigCount + edgeFigCount;
        }
        out.put("diagram", diagram);
        out.put("nodes", nodeCount);
        out.put("edges", edgeCount);
        out.put("figs", figCount);
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}
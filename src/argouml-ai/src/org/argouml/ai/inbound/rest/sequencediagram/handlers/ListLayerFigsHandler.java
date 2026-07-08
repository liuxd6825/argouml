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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.common.AbstractDiagramServiceHelper;
import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Diagnostic handler for {@code GET /d/{d}/sequencediagram/figs/dump}
 * that lists every Fig in the diagram's layer along with its
 * Java class name, owner model-element uuid, and owner name.
 * Useful for verifying that API mutations actually created the
 * expected Fig types in the visible layer (role + lifeline + message).
 */
public final class ListLayerFigsHandler implements IRequestHandler {

    private final SequenceDiagramService svc;

    public ListLayerFigsHandler(SequenceDiagramService svc) {
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
        ArgoDiagram d;
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
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (d.getLayer() != null) {
            for (Object fig : d.getLayer().getContents()) {
                Map<String, Object> entry = new LinkedHashMap<String, Object>();
                entry.put("class", fig == null ? "null" : fig.getClass().getName());
                // For FigEdge/FigEdgeModelElement, also show the inner
                // Fig class — that's what FigMessage.translate casts to.
                if (fig instanceof org.argouml.uml.diagram.ui.FigEdgeModelElement) {
                    org.tigris.gef.presentation.Fig inner =
                            ((org.argouml.uml.diagram.ui.FigEdgeModelElement) fig).getFig();
                    entry.put("inner", inner == null ? "null" : inner.getClass().getName());
                }
                // Get the model element via Fig.getOwner() if it's a FigNode
                Object owner = null;
                try {
                    if (fig instanceof org.tigris.gef.presentation.FigNode) {
                        owner = ((org.tigris.gef.presentation.FigNode) fig).getOwner();
                    } else if (fig instanceof org.tigris.gef.presentation.FigEdge) {
                        owner = ((org.tigris.gef.presentation.FigEdge) fig).getOwner();
                    }
                } catch (RuntimeException ignored) { }
                entry.put("owner", owner == null ? null
                        : org.argouml.model.Model.getFacade().getUUID(owner));
                entry.put("name", fig == null ? null
                        : org.argouml.model.Model.getFacade().getName(
                                fig instanceof org.tigris.gef.presentation.FigNode
                                        ? ((org.tigris.gef.presentation.FigNode) fig).getOwner()
                                        : (fig instanceof org.tigris.gef.presentation.FigEdge
                                                ? ((org.tigris.gef.presentation.FigEdge) fig).getOwner()
                                                : null)));
                out.add(entry);
            }
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}
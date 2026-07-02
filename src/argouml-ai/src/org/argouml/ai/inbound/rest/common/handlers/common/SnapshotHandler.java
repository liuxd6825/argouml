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

import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.common.UnsupportedException;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.tools.ProjectSnapshot;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Returns a JSON snapshot of one diagram (the shape produced by
 * {@link ProjectSnapshot#snapshot(ArgoDiagram)}). Only class diagrams
 * are supported by the snapshot tool today; anything else responds
 * 501 with code {@code SNAPSHOT_UNSUPPORTED}.
 *
 * <p>The URL shape is {@code /d/{d}/snapshot}; the diagram name is
 * supplied in the {@code d} path parameter and resolved via
 * {@link DiagramLocator#byName(String)}.</p>
 *
 * <p>The snapshot JSON is re-wrapped in our standard
 * {@code {ok:true, data:...}} envelope by string concatenation, so
 * we do not double-encode the inner object.</p>
 */
public final class SnapshotHandler implements IRequestHandler {

    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String name = pathParams == null ? null : pathParams.get("d");
        ArgoDiagram d;
        try {
            d = DiagramLocator.byName(name);
        } catch (DiagramLocator.DiagramNotFoundException ex) {
            throw new NotFoundException("DIAGRAM_NOT_FOUND", ex.getMessage());
        }
        if (!isClassDiagram(d)) {
            throw new UnsupportedException("SNAPSHOT_UNSUPPORTED",
                    "snapshot only supported for class diagrams (got "
                            + d.getClass().getSimpleName() + ")");
        }
        ProjectSnapshot.Snapshot snap = ProjectSnapshot.snapshot(d);
        if (snap == null) {
            throw new UnsupportedException("SNAPSHOT_UNSUPPORTED",
                    "snapshot returned null for "
                            + d.getClass().getSimpleName());
        }
        return ResponseEnvelope.json(200,
                "{\"ok\":true,\"data\":" + snap.toJson() + "}");
    }

    /**
     * @return true iff the diagram's runtime class is a UML class
     *         diagram. The check is class-name based to avoid an
     *         unnecessary dependency on the GEF presentation layer.
     */
    private static boolean isClassDiagram(ArgoDiagram d) {
        if (d == null) {
            return false;
        }
        String n = d.getClass().getSimpleName();
        return n.equals("UMLClassDiagram") || n.endsWith("ClassDiagram");
    }
}
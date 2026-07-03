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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /project/cleanup-datatypes}. Scans the
 * current project for duplicate {@code DataType} model elements
 * (the same type name created more than once in the same
 * namespace) and removes all but the canonical one.
 *
 * <p>Before the {@code resolveType} dedup fix, every
 * {@code POST /d/{d}/classes/{c}/attributes} call created a fresh
 * {@code DataType} node, so a project with many attributes of
 * primitive types accumulated 30+ duplicates. This endpoint clears
 * that backlog.</p>
 *
 * <p>Response (200):
 * <pre>
 *   {
 *     "ok": true,
 *     "data": {
 *       "scanned": 33,
 *       "removed": 30,
 *       "kept":   ["String", "Date", "int"]
 *     }
 *   }
 * </pre>
 *
 * <p>This is a project-wide operation (not diagram-scoped) so the
 * path is intentionally diagram-free: there is no diagram to pick
 * because the duplicates live in the model layer, not the
 * diagram.</p>
 */
public final class CleanupDatatypesHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public CleanupDatatypesHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        ClassDiagramService.CleanupReport r =
                svc.cleanupDuplicateDataTypes();
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("scanned", Integer.valueOf(r.scanned));
        out.put("removed", Integer.valueOf(r.removed));
        out.put("kept", new ArrayList<String>(r.kept));
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}

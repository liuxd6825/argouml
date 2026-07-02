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

import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;

/**
 * Handler for {@code DELETE /d/{d}/relationships/{id}?type=...}.
 * Returns 204 with an empty body on success.
 *
 * <p>The {@code {id}} path parameter carries the relationship's
 * identifier in the {@code "A|B"} pipe-separated form produced by the
 * Add* handlers ({@code "a|b"} for associations,
 * {@code "child|parent"} for generalisations, and
 * {@code "client|supplier"} for dependencies). The {@code type} is
 * supplied as a query parameter ({@code type=association},
 * {@code type=generalization}, {@code type=dependency}) so the path
 * can stay uniform across the three relationship kinds.</p>
 *
 * <p>Errors:</p>
 * <ul>
 *   <li>{@code INVALID_RELATIONSHIP_TYPE} (400) - the {@code type}
 *       query parameter is missing, empty, or not one of the three
 *       supported values;</li>
 *   <li>{@code INVALID_NAME} (400) - the {@code id} is missing,
 *       empty, not pipe-shaped, or has an empty endpoint;</li>
 *   <li>{@code DIAGRAM_NOT_FOUND} (404);</li>
 *   <li>{@code CLASS_NOT_FOUND} (404) - re-used by the service for
 *       a missing endpoint class, a missing relationship, OR an
 *       unknown endpoint;</li>
 * </ul>
 */
public final class DeleteRelationshipHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public DeleteRelationshipHandler(ClassDiagramService svc) {
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
        String id = pathParams == null ? null : pathParams.get("id");
        String type = queryParams == null ? null : queryParams.get("type");
        svc.deleteRelationship(diagramName, type, id);
        return ResponseEnvelope.json(204, "");
    }
}
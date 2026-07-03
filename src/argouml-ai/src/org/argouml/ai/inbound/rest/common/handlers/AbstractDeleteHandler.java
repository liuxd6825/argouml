/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.common.handlers;

import java.util.Map;

import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;

/**
 * Shared plumbing for {@code DELETE /d/{d}/<kind>s/{name}} endpoints.
 * Subclasses supply the {@code idPathKey} and the service-layer
 * {@link #doDelete(String, String)} call. The base class extracts
 * the path parameters, returns 204 on success, and emits a
 * standard 400 error envelope when the name is missing.
 */
public abstract class AbstractDeleteHandler<S> implements IRequestHandler {

    protected final S service;

    protected AbstractDeleteHandler(S service) {
        if (service == null) {
            throw new IllegalArgumentException("service");
        }
        this.service = service;
    }

    /** Path parameter key for the entity name (e.g. {@code "a"}
     *  for actor, {@code "u"} for use case, {@code "c"} for class). */
    protected abstract String idPathKey();

    /** Subclass-supplied: delete the entity by name. */
    protected abstract void doDelete(String diagram, String name);

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null
                ? null : pathParams.get(idPathKey());
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Element name required in path"));
        }
        doDelete(diagram, name);
        return ResponseEnvelope.json(204, "");
    }
}

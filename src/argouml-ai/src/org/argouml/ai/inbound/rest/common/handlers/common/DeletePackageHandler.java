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

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;

/**
 * Handler for {@code DELETE /project/packages/{name}}. Returns
 * 204 on success; 404 if the package is not found; 409 if the
 * package is non-empty (must move or delete contents first).
 */
public final class DeletePackageHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public DeletePackageHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String name = pathParams == null ? null : pathParams.get("name");
        try {
            svc.deletePackage(name);
            return new ResponseEnvelope(204, "application/json; charset=utf-8", "");
        } catch (org.argouml.ai.application.common.NotFoundException e) {
            return ResponseEnvelope.json(404, JsonError.of(e.code(), e.getMessage()));
        } catch (org.argouml.ai.application.common.DuplicateException e) {
            return ResponseEnvelope.json(409, JsonError.of(e.code(), e.getMessage()));
        } catch (org.argouml.ai.application.common.InvalidArgumentException e) {
            return ResponseEnvelope.json(400, JsonError.of(e.code(), e.getMessage()));
        }
    }
}

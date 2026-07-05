/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.usecasediagram.handlers.actor;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractDeleteHandler;

/**
 * Handler for {@code DELETE /d/{d}/usecasediagram/actors/by-name/{a}}.
 *
 * <p>Path parameter key is {@code "a"} (actor name). Returns 204
 * on success, 404 ACTOR_NOT_FOUND when the actor doesn't exist.</p>
 */
public final class DeleteActorByNameHandler
        extends AbstractDeleteHandler<UseCaseDiagramService> {

    public DeleteActorByNameHandler(UseCaseDiagramService svc) {
        super(svc);
    }

    @Override
    protected String idPathKey() {
        return "a";
    }

    @Override
    protected void doDelete(String diagram, String name) {
        service.deleteActorByName(diagram, name);
    }
}
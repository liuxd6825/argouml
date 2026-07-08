/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractDeleteHandler;

/**
 * Handler for {@code DELETE /d/{d}/sequencediagram/lifelines/by-name/{n}}.
 *
 * <p>Path parameter key is {@code "n"} (lifeline name). Returns
 * 204 on success, 404 LIFELINE_NOT_FOUND when the lifeline
 * doesn't exist.</p>
 */
public final class DeleteLifelineByNameHandler
        extends AbstractDeleteHandler<SequenceDiagramService> {

    public DeleteLifelineByNameHandler(SequenceDiagramService svc) {
        super(svc);
    }

    @Override
    protected String idPathKey() {
        return "n";
    }

    @Override
    protected void doDelete(String diagram, String name) {
        service.deleteLifelineByName(diagram, name);
    }
}
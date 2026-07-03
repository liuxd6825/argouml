/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.classdiagram.handlers;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractDeleteHandler;

/**
 * Handler for {@code DELETE /d/{d}/classes/{c}}. Returns 204 on
 * success.
 */
public final class DeleteClassHandler
        extends AbstractDeleteHandler<ClassDiagramService> {

    public DeleteClassHandler(ClassDiagramService svc) {
        super(svc);
    }

    @Override
    protected String idPathKey() {
        return "c";
    }

    @Override
    protected void doDelete(String diagram, String className) {
        service.deleteClass(diagram, className);
    }
}

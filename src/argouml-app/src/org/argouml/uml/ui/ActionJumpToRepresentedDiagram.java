/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.uml.ui;

import javax.swing.Action;

import org.argouml.uml.diagram.ArgoDiagram;

public final class ActionJumpToRepresentedDiagram extends AbstractActionNavigate {
    private static final long serialVersionUID = 1L;

    private final ArgoDiagram diagram;

    public ActionJumpToRepresentedDiagram(ArgoDiagram diagram) {
        super(diagram.getName(), false);
        this.diagram = diagram;
        putValue(Action.NAME, diagram.getName());
    }

    @Override
    protected Object navigateTo(Object source) {
        return diagram;
    }
}
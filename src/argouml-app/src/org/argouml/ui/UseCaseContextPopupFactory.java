/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ui;

import java.util.Collections;
import java.util.List;

import javax.swing.Action;

import org.argouml.kernel.ActionList;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.ui.ActionJumpToRepresentedDiagram;
import org.argouml.uml.ui.ActionManageRepresentedDiagrams;
import org.argouml.uml.ui.ActionNavigateRepresentedDiagram;

public final class UseCaseContextPopupFactory implements ContextActionFactory {

    @Override
    public List<Action> createContextPopupActions(Object context) {
        if (context == null || !Model.getFacade().isAUseCase(context)) {
            return Collections.emptyList();
        }
        ActionList menu = new ActionList("menu.popup.related-diagrams");
        menu.add(new ActionManageRepresentedDiagrams());
        List<ArgoDiagram> diagrams =
                ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams(context);
        if (!diagrams.isEmpty()) {
            for (ArgoDiagram d : diagrams) {
                menu.add(new ActionJumpToRepresentedDiagram(d));
            }
        }
        return Collections.<Action>singletonList(menu);
    }
}

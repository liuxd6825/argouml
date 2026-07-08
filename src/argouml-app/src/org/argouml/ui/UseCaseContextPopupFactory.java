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

import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.ui.ActionNavigateRepresentedDiagram;

/**
 * {@link ContextActionFactory} that injects the
 * {@code "Jump to Represented Diagram"} right-click menu entry
 * on UseCase elements. Used by both
 * {@link org.argouml.uml.diagram.ui.FigNodeModelElement#getPopUpActions}
 * (figure popup) and
 * {@link org.argouml.ui.explorer.ExplorerPopup} (Navigator tree
 * popup).
 *
 * <p>Returns an empty list when the target is not a UseCase or
 * when the UseCase has no {@code representedDiagram} tagged
 * value, so the menu item only appears when navigation is
 * possible.</p>
 *
 * @author mkl
 */
public final class UseCaseContextPopupFactory
        implements ContextActionFactory {

    @Override
    public List<Action> createContextPopupActions(Object context) {
        if (context == null) {
            return Collections.emptyList();
        }
        if (!Model.getFacade().isAUseCase(context)) {
            return Collections.emptyList();
        }
        ArgoDiagram target =
                ActionNavigateRepresentedDiagram.lookupRepresentedDiagram(
                        context);
        if (target == null) {
            return Collections.emptyList();
        }
        return Collections.<Action>singletonList(
                new ActionNavigateRepresentedDiagram());
    }
}
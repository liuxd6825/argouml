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

import java.awt.BorderLayout;

import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.core.propertypanels.ui.UseCaseRepresentedDiagramField;
import org.argouml.model.Model;
import org.argouml.swingext.UpArrowIcon;
import org.argouml.ui.targetmanager.TargetEvent;
import org.tigris.gef.presentation.Fig;
import org.tigris.swidgets.Horizontal;

/**
 * Right-side details tab showing a Use Case's "represented diagrams" (作为图).
 *
 * <p>This tab is a thin {@link AbstractArgoJPanel} wrapper around the
 * existing {@link UseCaseRepresentedDiagramField} component that already
 * lives inside the UseCase property panel. By wrapping it in a Details-tab
 * host we surface the same data on the right edge of the main window
 * (via the {@code eastPane} in {@link ProjectBrowser}) without disturbing
 * the property-panel instance (per the "两处都保留" decision).</p>
 *
 * <p>The DetailsPane-driven flow calls {@link #shouldBeEnabled(Object)}
 * for every target change and only forwards target events to this tab
 * when a UseCase is selected — so the tab auto-disables for any other
 * model element, matching the convention used by sibling tabs like
 * {@code TabDocumentation}.</p>
 */
public class TabUseCaseRepresentedDiagrams extends AbstractArgoJPanel
        implements TabModelTarget {

    private static final long serialVersionUID = 1L;

    private Object target;
    private final UseCaseRepresentedDiagramField field =
            new UseCaseRepresentedDiagramField();

    public TabUseCaseRepresentedDiagrams() {
        super("tab.represented-diagrams");
        setIcon(new UpArrowIcon());
        setOrientation(Horizontal.getInstance());
        setLayout(new BorderLayout());
        add(field, BorderLayout.CENTER);
    }

    /*
     * @see org.argouml.ui.TabTarget#setTarget(java.lang.Object)
     */
    @Override
    public void setTarget(Object t) {
        target = (t instanceof Fig) ? ((Fig) t).getOwner() : t;
        field.setTarget(target);
    }

    /*
     * @see org.argouml.ui.TabTarget#getTarget()
     */
    @Override
    public Object getTarget() {
        return target;
    }

    /*
     * @see org.argouml.ui.TabTarget#refresh()
     */
    @Override
    public void refresh() {
        setTarget(target);
    }

    /*
     * @see org.argouml.ui.TabTarget#shouldBeEnabled(java.lang.Object)
     */
    @Override
    public boolean shouldBeEnabled(Object t) {
        Object o = (t instanceof Fig) ? ((Fig) t).getOwner() : t;
        return Model.getFacade().isAUseCase(o);
    }

    /*
     * @see org.argouml.ui.targetmanager.TargetListener#targetAdded(
     *         org.argouml.ui.targetmanager.TargetEvent)
     */
    public void targetAdded(TargetEvent e) {
        setTarget(e.getNewTarget());
    }

    /*
     * @see org.argouml.ui.targetmanager.TargetListener#targetRemoved(
     *         org.argouml.ui.targetmanager.TargetEvent)
     */
    public void targetRemoved(TargetEvent e) {
        setTarget(e.getNewTarget());
    }

    /*
     * @see org.argouml.ui.targetmanager.TargetListener#targetSet(
     *         org.argouml.ui.targetmanager.TargetEvent)
     */
    public void targetSet(TargetEvent e) {
        setTarget(e.getNewTarget());
    }

    /**
     * Test-only accessor returning the wrapped
     * {@link UseCaseRepresentedDiagramField}. Package-private so tests
     * can introspect the table contents without breaking encapsulation.
     */
    UseCaseRepresentedDiagramField getFieldForTest() {
        return field;
    }
}
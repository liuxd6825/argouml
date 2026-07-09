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

import java.util.Iterator;
import java.util.List;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.model.RepresentedDiagramLinkCache;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Right-click / popup menu action: jump from a UseCase to the
 * ArgoDiagram referenced by its {@code representedDiagram}
 * tagged value. Visible only when the tag is set; the parent
 * {@code UseCaseContextPopupFactory} filters out null results
 * before they reach the menu.
 *
 * <p>Extends {@link AbstractActionNavigate} so the
 * TargetListener plumbing (auto enable/disable on selection
 * change) is inherited.</p>
 *
 * <p>The UUID lookup consults
 * {@link RepresentedDiagramLinkCache} (shared with the GUI
 * property panel and the AI REST service) before falling
 * back to the model's tagged-value scan. The shared cache
 * is necessary because the MDR backend's
 * {@code ExtensionMechanismsHelper.setType(handle, String)}
 * rejects a String type argument and throws
 * {@code IllegalArgumentException}; only a real
 * {@code TagDefinition} is accepted. Without the cache,
 * any reader that walks the tagged values by
 * {@code facade.getTag(tv)} returns {@code null} on the
 * round-trip path and the link is invisible.</p>
 *
 * @author mkl
 */
public final class ActionNavigateRepresentedDiagram
        extends AbstractActionNavigate {

    private static final long serialVersionUID = 1L;

    private static final String TAG_NAME = "representedDiagram";

    public ActionNavigateRepresentedDiagram() {
        super("menu.popup.jump-to-represented-diagram", true);
    }

    @Override
    protected Object navigateTo(Object source) {
        return lookupRepresentedDiagram(source);
    }

    /**
     * Static helper shared with
     * {@code UseCaseContextPopupFactory}.
     * Returns the matching ArgoDiagram or null when no link is
     * set or its UUID doesn't match any diagram in the current
     * project.
     */
    public static ArgoDiagram lookupRepresentedDiagram(Object useCase) {
        if (useCase == null || !Model.getFacade().isAUseCase(useCase)) {
            return null;
        }
        String uuid = readUuid(useCase);
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) {
                continue;
            }
            ArgoDiagram ad = (ArgoDiagram) d;
            if (uuid.equals(ad.getName())) {
                return ad;
            }
            Object ns = ad.getNamespace();
            if (ns != null && uuid.equals(facade.getUUID(ns))) {
                return ad;
            }
        }
        return null;
    }

    /**
     * Read the stored diagram UUID for a UseCase. Consults
     * the shared {@link RepresentedDiagramLinkCache} first
     * (populated by the GUI panel and the AI REST service on
     * write), then falls back to the model's tagged-value
     * scan. Successful model reads refill the cache so a
     * subsequent call avoids the model walk.
     */
    private static String readUuid(Object useCase) {
        List<String> cached = RepresentedDiagramLinkCache.getAll(useCase);
        if (!cached.isEmpty()) {
            return cached.get(0);
        }
        String fromTag = readTag(useCase);
        if (!fromTag.isEmpty()) {
            RepresentedDiagramLinkCache.put(useCase,
                    java.util.Collections.singletonList(fromTag));
        }
        return fromTag;
    }

    private static String readTag(Object useCase) {
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) {
                return "";
            }
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if (TAG_NAME.equals(facade.getName(tv))) {
                    Object v = facade.getValue(tv);
                    return v == null ? "" : v.toString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }
}

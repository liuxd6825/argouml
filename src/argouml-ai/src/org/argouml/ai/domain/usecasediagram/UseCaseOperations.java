/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.usecasediagram;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.argouml.ai.domain.common.AbstractDiagramElementOperations;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.model.UseCasesFactory;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * UML UseCase creation / mutation. Inherits build / findByName /
 * delete / setPosition from
 * {@link AbstractDiagramElementOperations}; adds the use-case-
 * specific description persistence helpers
 * ({@link #setDescription}, {@link #getDescription}) and the
 * project-wide {@link #list} / {@link #listAllInNamespace} helpers
 * used by the service layer.
 */
public final class UseCaseOperations
        extends AbstractDiagramElementOperations<Object> {

    @Override
    protected Object buildImpl(ArgoDiagram diagram, String name) {
        UseCasesFactory uf = Model.getUseCasesFactory();
        CoreHelper ch = Model.getCoreHelper();
        Object useCase = uf.createUseCase();
        ch.setName(useCase, name);
        ch.addOwnedElement(diagram.getNamespace(), useCase);
        return useCase;
    }

    /**
     * Static convenience: build a UseCase, then attach a
     * description (best-effort). Used by tests that pre-date the
     * abstract refactor; new callers should use the inherited
     * instance {@link #build(ArgoDiagram, String)} plus a
     * separate {@link #setDescription} call.
     */
    public static Object build(ArgoDiagram diagram, String name,
                               String description) {
        UseCaseOperations self = new UseCaseOperations();
        Object useCase = self.build(diagram, name);
        if (description != null && !description.isEmpty()) {
            setDescription(useCase, description);
        }
        return useCase;
    }

    @Override
    protected boolean isTargetType(Object node) {
        return Model.getFacade().isAUseCase(node);
    }

    /**
     * Persist a free-text description on a UseCase via the
     * standard tagged-value mechanism. Uses
     * {@code "documentation"} as the tag name (matches ArgoUML's
     * own convention for use-case docs).
     */
    public static void setDescription(Object useCase, String description) {
        if (useCase == null || description == null) {
            return;
        }
        try {
            org.argouml.model.ExtensionMechanismsFactory emf =
                    Model.getExtensionMechanismsFactory();
            org.argouml.model.ExtensionMechanismsHelper emh =
                    Model.getExtensionMechanismsHelper();
            Object tv = emf.createTaggedValue();
            emh.addTaggedValue(useCase, tv);
            emh.setType(tv, "documentation");
            emh.setDataValues(tv, new String[] {description});
        } catch (RuntimeException ignored) {
            // best-effort; description is optional
        }
    }

    /**
     * Read a UseCase's description. Returns {@code ""} if no
     * tagged value is present or the model layer rejects the
     * lookup.
     */
    public static String getDescription(Object useCase) {
        if (useCase == null) {
            return "";
        }
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) {
                return "";
            }
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if ("documentation".equals(facade.getName(tv))) {
                    Object v = facade.getValue(tv);
                    return v == null ? "" : v.toString();
                }
            }
            return "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /**
     * List every UseCase node currently on the diagram (in graph-
     * model order, which is the same as the user's view order).
     */
    public static Set<Object> list(ArgoDiagram diagram) {
        Set<Object> out = new LinkedHashSet<Object>();
        if (diagram == null) {
            return out;
        }
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (facade.isAUseCase(node)) {
                out.add(node);
            }
        }
        return out;
    }

    /**
     * List every UseCase reachable from the diagram's namespace
     * (includes use cases defined in the model but not yet placed
     * on the diagram). Useful for "import all use cases" style
     * requests; not currently exposed via HTTP.
     */
    public static Collection listAllInNamespace(ArgoDiagram diagram) {
        Object ns = diagram == null ? null : diagram.getNamespace();
        if (ns == null) {
            return new java.util.ArrayList();
        }
        return Model.getUseCasesHelper().getAllUseCases(ns);
    }
}

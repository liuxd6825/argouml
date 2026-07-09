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
import org.argouml.model.RepresentedDiagramLinkCache;
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
     * Persist a link from a UseCase to another ArgoDiagram (single-
     * uuid convenience wrapper). Empty / null uuid clears the
     * link. Delegates to {@link #setRepresentedDiagrams} so cache
     * and model stay in sync with the 1:N path.
     */
    public static void setRepresentedDiagram(Object useCase, String diagramUuid) {
        java.util.List<String> list;
        if (diagramUuid == null || diagramUuid.isEmpty()) {
            list = java.util.Collections.emptyList();
        } else {
            list = java.util.Collections.singletonList(diagramUuid);
        }
        setRepresentedDiagrams(useCase, list);
    }

    /**
     * Read the {@code representedDiagram} UUID stored on a
     * UseCase. Returns the first linked UUID, or {@code ""} if
     * no link is set. Delegates to {@link #getRepresentedDiagrams}.
     */
    public static String getRepresentedDiagram(Object useCase) {
        java.util.List<String> all = getRepresentedDiagrams(useCase);
        return all.isEmpty() ? "" : all.get(0);
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

    /**
     * Replace all links for a UseCase. Authoritative store is
     * {@link RepresentedDiagramLinkCache}; tagged-value write is
     * best-effort (MDR {@code setType(String)} throws).
     */
    public static void setRepresentedDiagrams(Object useCase, java.util.Collection<String> diagramUuids) {
        if (useCase == null) return;
        java.util.List<String> normalized = diagramUuids == null
                ? java.util.Collections.<String>emptyList()
                : sanitizeUuids(diagramUuids);
        RepresentedDiagramLinkCache.put(useCase, normalized);
        writeModelTag(useCase, normalized);
    }

    /** Append a single uuid (idempotent). */
    public static boolean addRepresentedDiagram(Object useCase, String uuid) {
        if (useCase == null || uuid == null || uuid.isEmpty()) return false;
        java.util.List<String> current = new java.util.ArrayList<String>(getRepresentedDiagrams(useCase));
        if (current.contains(uuid)) return false;
        current.add(uuid);
        setRepresentedDiagrams(useCase, current);
        return true;
    }

    /** Remove a single uuid. Returns true if removed. */
    public static boolean removeRepresentedDiagram(Object useCase, String uuid) {
        if (useCase == null || uuid == null || uuid.isEmpty()) return false;
        java.util.List<String> current = new java.util.ArrayList<String>(getRepresentedDiagrams(useCase));
        boolean removed = current.remove(uuid);
        if (removed) setRepresentedDiagrams(useCase, current);
        return removed;
    }

    /**
     * Return immutable list of currently linked ArgoDiagram UUIDs.
     * Cache-first (authoritative); model fallback via
     * {@link org.argouml.model.Facade#getDataValue}.
     */
    public static java.util.List<String> getRepresentedDiagrams(Object useCase) {
        if (useCase == null) return java.util.Collections.emptyList();
        java.util.List<String> cached = RepresentedDiagramLinkCache.getAll(useCase);
        if (!cached.isEmpty()) return cached;
        java.util.List<String> fromTag = readModelTag(useCase);
        if (!fromTag.isEmpty()) {
            RepresentedDiagramLinkCache.put(useCase, fromTag);
        }
        return fromTag;
    }

    private static java.util.List<String> sanitizeUuids(java.util.Collection<String> input) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<String>();
        for (String s : input) {
            if (s != null && !s.isEmpty()) seen.add(s);
        }
        return new java.util.ArrayList<String>(seen);
    }

    private static void writeModelTag(Object useCase, java.util.List<String> uuids) {
        try {
            org.argouml.model.ExtensionMechanismsFactory emf =
                    Model.getExtensionMechanismsFactory();
            org.argouml.model.ExtensionMechanismsHelper emh =
                    Model.getExtensionMechanismsHelper();
            Object tv = emf.createTaggedValue();
            emh.addTaggedValue(useCase, tv);
            emh.setType(tv, "representedDiagram");
            emh.setDataValues(tv, uuids.toArray(new String[0]));
        } catch (RuntimeException ignored) {
            // best-effort; cache is authoritative
        }
    }

    private static java.util.List<String> readModelTag(Object useCase) {
        try {
            org.argouml.model.Facade facade = Model.getFacade();
            java.util.Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) return java.util.Collections.emptyList();
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if ("representedDiagram".equals(facade.getName(tv))) {
                    Object raw = facade.getDataValue(tv);
                    java.util.List<String> result = new java.util.ArrayList<String>();
                    if (raw instanceof java.util.Collection) {
                        for (Object o : (java.util.Collection) raw) {
                            if (o != null) result.add(String.valueOf(o));
                        }
                    }
                    return result;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return java.util.Collections.emptyList();
    }
}

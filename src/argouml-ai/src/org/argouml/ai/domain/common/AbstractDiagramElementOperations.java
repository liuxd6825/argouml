/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.common;

import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;
import org.tigris.gef.presentation.Fig;

/**
 * Shared CRUD plumbing for UML model elements placed on a diagram
 * (Class, Actor, UseCase, Interface, etc.).
 *
 * <p>Each subclass supplies:
 * <ol>
 *   <li>{@link #buildImpl(ArgoDiagram, String)} — model-specific
 *       factory call that yields a freshly-created element of the
 *       right metaclass (already named, already added to the
 *       namespace, but not yet added to the graph model). The
 *       base class handles graph-model insertion + setName
 *       validation.</li>
 *   <li>{@link #isTargetType(Object)} — the discriminator the
 *       find-by-name scan uses to keep only the right element
 *       kind. The base class iterates {@code diagram.getGraphModel()
 *       .getNodes()} and filters with this predicate.</li>
 * </ol>
 *
 * <p>All four CRUD operations (build, findByName, delete,
 * setPosition) are provided with the same semantics that
 * {@code ClassOperations} / {@code ActorOperations} /
 * {@code UseCaseOperations} had before the refactor.</p>
 *
 * <p><b>Architectural boundary.</b> Same as the prior free-standing
 * Operations classes: no HTTP, no AI, no inbound/outbound adapter,
 * no Swing UI. Pure functions on the model + diagram. All methods
 * are safe to call on the Swing EDT.</p>
 *
 * @param <T> the model element metaclass (e.g. {@code Object} for
 *           use case elements — the GEF graph model uses raw
 *           {@code Object}; we keep the generic so subclasses can
 *           pick a more specific type when desired).
 */
public abstract class AbstractDiagramElementOperations<T> {

    /**
     * Build a new model element on the diagram. Calls
     * {@link #buildImpl} to get the raw element, then registers it
     * on the diagram's graph model.
     *
     * @return the new model element
     * @throws IllegalArgumentException if {@code name} is null or
     *         empty, or {@code diagram} has no namespace
     */
    public final T build(ArgoDiagram diagram, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Name must not be empty");
        }
        if (diagram == null) {
            throw new IllegalArgumentException(
                    "diagram must not be null");
        }
        Object ns = diagram.getNamespace();
        if (ns == null) {
            throw new IllegalArgumentException(
                    "diagram must have a namespace "
                    + "(call p.addDiagram(d) first)");
        }
        T elem = buildImpl(diagram, name);
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        gm.addNode(elem);
        return elem;
    }

    /**
     * Find the element on the diagram whose ArgoUML UUID
     * (xmi.id) equals {@code uuid}. A null/empty uuid yields null.
     * Subclasses gate the type via {@link #isTargetType}.
     *
     * <p>UUID lookup is the recommended way to disambiguate
     * elements that share a name (e.g. multiple "User" actors
     * created by the API in a single namespace). It is also stable
     * across .zargo save/load (xmi.id is the canonical XMI
     * identifier).</p>
     */
    public final T findByUuid(ArgoDiagram diagram, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        if (diagram == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (isTargetType(node)
                    && uuid.equals(facade.getUUID(node))) {
                return cast(node);
            }
        }
        return null;
    }

    /**
     * Remove the element from the diagram's graph model and
     * delete the underlying model element. Best-effort — the MDR
     * backend may refuse to delete elements with live inbound
     * references, but the node is already off the graph model by
     * that point so the user-facing effect is consistent.
     */
    public final void delete(ArgoDiagram diagram, T elem) {
        if (elem == null || diagram == null) {
            return;
        }
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm.containsNode(elem)) {
            gm.removeNode(elem);
        }
        try {
            Model.getUmlFactory().delete(elem);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    /**
     * Move the element's fig on the canvas. Model element is
     * unchanged. No-op when the fig has not yet been realized
     * (lazy in use case diagrams).
     */
    public final void setPosition(ArgoDiagram diagram, T elem,
                                  int x, int y) {
        if (elem == null || diagram == null) {
            return;
        }
        Fig f = diagram.presentationFor(elem);
        if (f != null) {
            f.setLocation(x, y);
        }
    }

    /** Model-specific factory. Must create a fresh element of
     *  the right metaclass, set its name, and add it to the
     *  diagram's namespace. The base class inserts the element
     *  into the graph model. */
    protected abstract T buildImpl(ArgoDiagram diagram, String name);

    /** Type discriminator for {@link #findByName}. */
    protected abstract boolean isTargetType(Object node);

    @SuppressWarnings("unchecked")
    private T cast(Object o) {
        return (T) o;
    }
}

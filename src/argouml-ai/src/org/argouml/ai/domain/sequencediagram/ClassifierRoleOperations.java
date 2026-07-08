/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.sequencediagram;

import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * UML Sequence-diagram ClassifierRole creation / mutation.
 *
 * <p><b>Why this class does NOT extend
 * {@link org.argouml.ai.domain.common.AbstractDiagramElementOperations}.</b>
 * The sequence diagram's {@code SequenceDiagramRenderer.getFigNodeFor}
 * fires from the {@code MutableGraphModel.fireNodeAdded} listener
 * triggered by {@code gm.addNode(elem)}. That renderer calls
 * {@code ((UMLSequenceDiagram) diag).drop(node, null)}, which in turn
 * calls {@code createDiagramElement(node, bounds)} which guards
 * with {@code if (!getGraphModel().getNodes().contains(modelElement))}
 * — the guard fires AFTER the listener has already added the node, so
 * it returns {@code null}, the renderer passes {@code null} to
 * {@code lay.add(...)}, and the listener chain NPEs. The class-of-the-art
 * workaround is the one used by
 * {@link org.argouml.sequence2.diagram.UMLSequenceDiagram}: bypass
 * the listener by mutating {@code gm.getNodes()} directly, then
 * realise the Fig via {@code diagram.drop(...)} (which carries its own
 * graph-node insertion). We follow that same exception pattern as
 * {@code AttributeOperations}, {@code IncludeOperations},
 * {@code ExtendOperations}, and {@link MessageOperations} (see
 * AGENTS.md §321).</p>
 *
 * <p><b>Architectural boundary.</b> Pure functions on the model +
 * diagram; no HTTP, no AI, no inbound / outbound adapter, no
 * Swing UI.</p>
 */
public final class ClassifierRoleOperations {

    /**
     * Build a ClassifierRole on the sequence diagram. The factory
     * runs against the diagram's namespace (which the
     * {@link org.argouml.sequence2.diagram.UMLSequenceDiagram}
     * constructor set to the owning {@code MCollaboration}); the
     * resulting element is named and added to the diagram.
     * Idempotent re-add is a no-op.
     *
     * @param diagram the sequence diagram (added to a project so its
     *                namespace resolves)
     * @param name    display name; non-null, non-empty
     * @return the new ClassifierRole
     * @throws IllegalArgumentException if diagram is null or name is
     *                                  null/empty
     */
    public Object build(ArgoDiagram diagram, String name) {
        if (diagram == null) {
            throw new IllegalArgumentException("diagram must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "ClassifierRole name must not be empty");
        }
        if (diagram.getNamespace() == null) {
            throw new IllegalArgumentException(
                    "diagram must have a namespace "
                    + "(call p.addDiagram(d) first)");
        }
        Object role = Model.getCollaborationsFactory()
                .buildClassifierRole(diagram.getNamespace());
        Model.getCoreHelper().setName(role, name);
        addToDiagram(diagram, role);
        return role;
    }

    /**
     * Find a ClassifierRole on the diagram by name. Returns null if
     * none match or on null/empty inputs.
     */
    public Object findByName(ArgoDiagram diagram, String name) {
        if (name == null || name.isEmpty() || diagram == null
                || diagram.getGraphModel() == null) {
            return null;
        }
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (isTargetType(node) && name.equals(Model.getFacade().getName(node))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Find a ClassifierRole on the diagram by ArgoUML UUID
     * ({@code xmi.id}). Returns null if none match or on null/empty
     * inputs.
     */
    public Object findByUuid(ArgoDiagram diagram, String uuid) {
        if (uuid == null || uuid.isEmpty() || diagram == null
                || diagram.getGraphModel() == null) {
            return null;
        }
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (isTargetType(node) && uuid.equals(Model.getFacade().getUUID(node))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Type discriminator for {@link #findByName} / {@link #findByUuid}.
     */
    public boolean isTargetType(Object node) {
        return Model.getFacade().isAClassifierRole(node);
    }

    /**
     * Remove the ClassifierRole from the graph model and delete the
     * underlying model element. Best-effort: the MDR backend may
     * refuse to delete elements with live inbound references, but
     * the node has already been removed from the graph model by
     * that point.
     */
    public void delete(ArgoDiagram diagram, Object role) {
        if (role == null || diagram == null) {
            return;
        }
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm != null && gm.containsNode(role)) {
            gm.getNodes().remove(role);
        }
        try {
            Model.getUmlFactory().delete(role);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    /**
     * Move the element's fig on the canvas. No-op when the fig has
     * not yet been realized (lazy in headless / non-graphical mode).
     */
    public void setPosition(ArgoDiagram diagram, Object role, int x, int y) {
        if (role == null || diagram == null) {
            return;
        }
        org.tigris.gef.presentation.Fig f =
                diagram.presentationFor(role);
        if (f != null) {
            f.setLocation(x, y);
        }
    }

    /**
     * Add the model element to the diagram, bypassing the graph-model
     * listener chain (Phase 1 fix) and ALSO creating the Fig
     * directly. The sequence diagram module's
     * {@code UMLSequenceDiagram} does not override
     * {@code createDiagramElement} (a pre-existing bug in
     * argouml-core-diagrams-sequence2), so {@code diagram.drop}
     * returns null and no Fig is ever created. We work around
     * this by directly instantiating {@code FigClassifierRole}
     * (the same Fig class the production code in
     * {@code ModePlaceClassifierRole} and
     * {@code SequenceDiagramGraphModel.addNode} use).
     */
    private static void addToDiagram(ArgoDiagram diagram, Object element) {
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm == null || gm.getNodes().contains(element)) {
            return;
        }
        gm.getNodes().add(element);
        // Create the Fig directly. This is the only way to get a
        // Fig on a sequence diagram's layer in this codebase —
        // the diagram.drop() path is broken (see Javadoc).
        try {
            if (diagram.getLayer() != null) {
                org.argouml.uml.diagram.DiagramSettings settings =
                        diagram.getDiagramSettings();
                // Use bounds from the element's fig if present, or
                // default to (0, 0). For sequence diagrams there
                // is no real "drop point" — the rendering is purely
                // vertical.
                org.tigris.gef.presentation.FigNode fig = new org.argouml
                        .sequence2.diagram.FigClassifierRole(
                                element,
                                new java.awt.Rectangle(0, 0, 0, 0),
                                settings);
                diagram.getLayer().add(fig);
            }
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger(
                    ClassifierRoleOperations.class.getName())
                    .log(java.util.logging.Level.WARNING,
                            "Fig creation failed for "
                            + element.getClass().getSimpleName()
                            + " (" + element + "): " + ex.getMessage());
        }
    }
}

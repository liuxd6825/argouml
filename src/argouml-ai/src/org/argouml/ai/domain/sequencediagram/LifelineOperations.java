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

import java.util.ArrayList;
import java.util.List;

import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * UML Sequence-diagram Lifeline creation / mutation.
 *
 * <p><b>Why this class does NOT extend
 * {@link org.argouml.ai.domain.common.AbstractDiagramElementOperations}.</b>
 * Same exception as {@link ClassifierRoleOperations}: the sequence
 * diagram renderer NPEs when a node is added via the standard
 * {@code gm.addNode(elem)} path. We follow the same free-standing
 * class pattern as
 * {@code AttributeOperations} / {@code IncludeOperations} /
 * {@code ExtendOperations} / {@link MessageOperations} (see
 * AGENTS.md §321).</p>
 *
 * <p>In UML 1.4 (MDR backend) ArgoUML folds Lifeline into
 * ClassifierRole
 * ({@code FacadeMDRImpl.isALifeline(x) == x instanceof
 * ClassifierRole}); on EUML / UML 2.x the distinction will matter.</p>
 *
 * <p><b>Architectural boundary.</b> Pure functions on the model +
 * diagram; no HTTP, no AI, no inbound / outbound adapter, no
 * Swing UI.</p>
 */
public final class LifelineOperations {

    /**
     * Build a Lifeline on the sequence diagram. Idempotent re-add is
     * a no-op. The factory runs against the diagram's namespace
     * (which the
     * {@link org.argouml.sequence2.diagram.UMLSequenceDiagram}
     * constructor set to the owning {@code MCollaboration}).
     *
     * @param diagram the sequence diagram (added to a project so its
     *                namespace resolves)
     * @param name    display name; non-null, non-empty
     * @return the new Lifeline
     * @throws IllegalArgumentException if diagram is null or name is
     *                                  null/empty
     */
    public Object build(ArgoDiagram diagram, String name) {
        if (diagram == null) {
            throw new IllegalArgumentException("diagram must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lifeline name must not be empty");
        }
        if (diagram.getNamespace() == null) {
            throw new IllegalArgumentException(
                    "diagram must have a namespace "
                    + "(call p.addDiagram(d) first)");
        }
        Object lifeline = Model.getCollaborationsFactory()
                .buildLifeline(diagram.getNamespace());
        Model.getCoreHelper().setName(lifeline, name);
        addToDiagram(diagram, lifeline);
        return lifeline;
    }

    /**
     * Find a Lifeline on the diagram by name. Returns null if none
     * match or on null/empty inputs.
     *
     * <p>Note: in the MDR / UML 1.4 backend, every ClassifierRole is
     * a Lifeline (multiplicity 1,1) — there is no structural marker
     * that distinguishes a /roles-created ClassifierRole from a
     * /lifelines-created one. The disambiguation is done at the
     * service layer (see {@code SequenceDiagramService.listLifelines}),
     * which tracks /lifelines-created uuids in a separate Set and
     * filters the graph-model result by Set membership.</p>
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
     * Find a Lifeline on the diagram by ArgoUML UUID
     * ({@code xmi.id}). Returns null if none match or on null/empty
     * inputs. Same MDR caveat as
     * {@link #findByName(ArgoDiagram, String)} — the service
     * layer is responsible for further filtering.
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
     * In UML 1.4 (MDR) this aliases {@code isAClassifierRole}; the
     * EUML / UML 2.x build will discriminate them properly.
     */
    public boolean isTargetType(Object node) {
        return Model.getFacade().isALifeline(node);
    }

    /**
     * Remove the Lifeline from the graph model and delete the
     * underlying model element. Best-effort.
     */
    public void delete(ArgoDiagram diagram, Object lifeline) {
        if (lifeline == null || diagram == null) {
            return;
        }
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm != null && gm.containsNode(lifeline)) {
            gm.getNodes().remove(lifeline);
        }
        try {
            Model.getUmlFactory().delete(lifeline);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    /**
     * Move the element's fig on the canvas. No-op when the fig has
     * not yet been realized (lazy in headless / non-graphical mode).
     */
    public void setPosition(ArgoDiagram diagram, Object lifeline,
                            int x, int y) {
        if (lifeline == null || diagram == null) {
            return;
        }
        org.tigris.gef.presentation.Fig f =
                diagram.presentationFor(lifeline);
        if (f != null) {
            f.setLocation(x, y);
        }
    }

    /**
     * Add the model element to the diagram, bypassing the graph-model
     * listener chain (see {@link ClassifierRoleOperations#addToDiagram}
     * for the rationale). Since {@code diagram.drop} is broken for
     * sequence diagrams, we create a Fig directly. Note: in the
     * MDR / UML 1.4 backend, a Lifeline IS a ClassifierRole with
     * multiplicity(1,1), so we instantiate the same
     * {@code FigClassifierRole} class — the bottom half of the
     * ClassifierRole Fig is rendered as the lifeline.
     */
    private static void addToDiagram(ArgoDiagram diagram, Object element) {
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm == null || gm.getNodes().contains(element)) {
            return;
        }
        gm.getNodes().add(element);
        try {
            if (diagram.getLayer() != null) {
                org.argouml.uml.diagram.DiagramSettings settings =
                        diagram.getDiagramSettings();
                org.tigris.gef.presentation.FigNode fig = new org.argouml
                        .sequence2.diagram.FigClassifierRole(
                                element,
                                new java.awt.Rectangle(0, 0, 0, 0),
                                settings);
                diagram.getLayer().add(fig);
            }
        } catch (RuntimeException ex) {
            java.util.logging.Logger.getLogger(
                    LifelineOperations.class.getName())
                    .log(java.util.logging.Level.WARNING,
                            "Fig creation failed for "
                            + element.getClass().getSimpleName()
                            + " (" + element + "): " + ex.getMessage());
        }
    }

    /**
     * Convenience for {@link org.argouml.ai.application.sequencediagram.SequenceDiagramService#listLifelines(String)}.
     * Returns every Lifeline on the diagram in graph-node iteration
     * order. Note: per the MDR caveat, every ClassifierRole is a
     * Lifeline here, so the service layer filters by a Set of
     * /lifelines-created uuids.
     */
    public List<Object> findAll(ArgoDiagram diagram) {
        List<Object> out = new ArrayList<Object>();
        if (diagram == null || diagram.getGraphModel() == null) {
            return out;
        }
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (isTargetType(node)) {
                out.add(node);
            }
        }
        return out;
    }
}

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
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * UML Sequence-diagram Message creation / mutation.
 *
 * <p><b>Why this class does NOT extend
 * {@link org.argouml.ai.domain.common.AbstractDiagramElementOperations}.</b>
 * A {@code Message} is an edge between two classifier roles, not a
 * standalone node. The abstract base presumes a
 * {@code build(diagram, name)} factory entry-point with a graph-node
 * discriminator; messages have neither. They also need to wire up an
 * {@code MAssociationRole} on the way in, which the base class
 * cannot express. We follow the same exception pattern as
 * {@code AttributeOperations} / {@code IncludeOperations} /
 * {@code ExtendOperations} (see AGENTS.md §321): free-standing
 * class, plain methods, no inheritance.</p>
 *
 * <p>The {@code gm.addEdge} call is wrapped in try/catch because of
 * a known GEF bug ("source port must be supplied") raised by
 * {@code MutableGraphModel.addEdge} in headless mode; the model is
 * consistent regardless, so we log and continue rather than fail
 * the call.</p>
 *
 * <p><b>Architectural boundary.</b> Pure functions on the model +
 * diagram; no HTTP, no AI, no inbound / outbound adapter, no
 * Swing UI.</p>
 */
public final class MessageOperations {

    private static final Logger LOG =
            Logger.getLogger(MessageOperations.class.getName());

    /**
     * Build a message between two classifier roles on the sequence
     * diagram. Creates / reuses the {@code MAssociationRole} on
     * first call, attaches sender + receiver, sets the message sort
     * (if a known sort name is supplied), sets the name, and adds
     * the message to the diagram's graph-model edges. The graph
     * model is mutated directly via {@code getEdges()} to bypass
     * the listener chain — see {@link ClassifierRoleOperations}
     * Javadoc for the rationale.
     */
    public Object build(ArgoDiagram diagram, Object from, Object to,
                        String name, String actionSignature,
                        String messageType, boolean activation) {
        if (diagram == null) {
            throw new IllegalArgumentException("diagram must not be null");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                    "from and to classifierRoles must not be null");
        }
        Object associationRole =
                Model.getCollaborationsHelper().getAssociationRole(from, to);
        if (associationRole == null) {
            associationRole = Model.getCollaborationsFactory()
                    .buildAssociationRole(from, to);
        }
        Object interaction = resolveInteraction(diagram, from);
        Object msg = Model.getCollaborationsFactory()
                .buildMessage(interaction, associationRole);
        if (name != null && !name.isEmpty()) {
            Model.getCoreHelper().setName(msg, name);
        }
        applyMessageAction(msg, messageType);
        Model.getCollaborationsHelper().setSender(msg, from);
        Model.getCommonBehaviorHelper().setReceiver(msg, to);
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm != null && !gm.getEdges().contains(msg)) {
            try {
                gm.addEdge(msg);
            } catch (RuntimeException | AssertionError ex) {
                // headless mode: the renderer's setPorts() asserts on
                // missing FigNodes; bypass the listener chain by
                // mutating getEdges() directly so the model stays in
                // sync with the graph-model bookkeeping.
                LOG.log(Level.WARNING,
                        "GEF addEdge failed for message " + name
                        + " (model still updated): " + ex.getMessage());
                if (!gm.getEdges().contains(msg)) {
                    gm.getEdges().add(msg);
                }
            }
        }
        // The listener path (SequenceDiagramGraphModel.addEdge →
        // fireEdgeAdded → getFigEdgeFor) creates the FigMessage
        // BEFORE this block runs. That FigMessage has no source/dest
        // FigNodes wired AND its inner FigPoly has zero points —
        // both mean the arrow is invisible on the canvas (zero-length
        // line). Production code (ModeCreateMessage.endAttached) wires
        // source/dest and then sets FigPoly.setComplete(true); for
        // self-messages it additionally calls convertToArc() to use
        // a U-shaped FigMessageSpline. We do the same here so the
        // API-driven path renders correctly.
        //
        // We deliberately do NOT call figEdge.computeRoute() here:
        // that path triggers FigNode.updateEdges which accesses
        // edges[1] on a single-edge list and throws
        // IndexOutOfBoundsException (GEF pre-existing bug).
        // Instead we manually set the endpoints via setEndPoints()
        // on the inner FigPoly and mark it complete. GEF's paint
        // machinery will then render the arrow on the next frame.
        try {
            if (diagram.getLayer() != null) {
                org.argouml.sequence2.diagram.FigMessage figEdge = null;
                for (Object existing : diagram.getLayer().getContents()) {
                    if (existing instanceof org.argouml.sequence2.diagram.FigMessage
                            && Model.getFacade().getUUID(
                                    ((org.argouml.sequence2.diagram.FigMessage) existing).getOwner())
                                    .equals(Model.getFacade().getUUID(msg))) {
                        figEdge = (org.argouml.sequence2.diagram.FigMessage) existing;
                        break;
                    }
                }
                if (figEdge == null) {
                    // Fallback for the rare case where the listener
                    // didn't fire (e.g. addEdge threw before firing).
                    org.argouml.uml.diagram.DiagramSettings settings =
                            diagram.getDiagramSettings();
                    figEdge = new org.argouml.sequence2.diagram.FigMessage(
                            msg, settings);
                    diagram.getLayer().add(figEdge);
                }
                // Look up the source/dest FigNodes by iterating
                // getContentsNoEdges() (avoids seeing the message's
                // own FigEdge).
                org.argouml.sequence2.diagram.FigClassifierRole fromFig = null;
                org.argouml.sequence2.diagram.FigClassifierRole toFig = null;
                for (Object f : diagram.getLayer().getContentsNoEdges()) {
                    if (f instanceof org.argouml.sequence2.diagram.FigClassifierRole) {
                        String fUuid = Model.getFacade().getUUID(
                                ((org.argouml.sequence2.diagram.FigClassifierRole) f).getOwner());
                        if (fromFig == null
                                && fUuid.equals(Model.getFacade().getUUID(from))) {
                            fromFig = (org.argouml.sequence2.diagram.FigClassifierRole) f;
                        }
                        if (toFig == null
                                && fUuid.equals(Model.getFacade().getUUID(to))) {
                            toFig = (org.argouml.sequence2.diagram.FigClassifierRole) f;
                        }
                    }
                }
                boolean isSelf = Model.getFacade().getUUID(from).equals(
                        Model.getFacade().getUUID(to));
                if (fromFig != null) {
                    figEdge.setSourceFigNode(fromFig);
                    figEdge.setSourcePortFig(fromFig.getBigPort());
                } else {
                    LOG.log(Level.WARNING,
                            "  no FigClassifierRole found for source uuid="
                            + Model.getFacade().getUUID(from));
                }
                if (toFig != null) {
                    figEdge.setDestFigNode(toFig);
                    figEdge.setDestPortFig(toFig.getBigPort());
                } else {
                    LOG.log(Level.WARNING,
                            "  no FigClassifierRole found for dest uuid="
                            + Model.getFacade().getUUID(to));
                }
                // Now populate the inner FigPoly's endpoints and mark
                // it complete. FigEdgePoly.setEndPoints(src, dst)
                // gives the arrow a non-zero-length line so paint
                // actually renders it; setComplete(true) is the
                // marker GEF checks before drawing. Self-messages
                // additionally convert the inner FigPoly to a
                // FigMessageSpline (U-shape).
                //
                // CRITICAL: the listener path (FigClassifierRole.addFigEdge)
                // may call convertToArc() on this FigMessage even when
                // the message is NOT a self-message, because
                // FigMessage.isSelfMessage() falls back to a model-level
                // check that returns true when getSource() and
                // getDestination() are not yet wired. We must therefore
                // defensively reset the inner Fig if it ends up as a
                // FigMessageSpline on a non-self message — otherwise the
                // user sees a U-shape where a straight arrow should be.
                Object inner = figEdge.getFig();
                if (fromFig != null && toFig != null) {
                    java.awt.Point srcPt = portCenter(fromFig.getBigPort());
                    java.awt.Point dstPt = portCenter(toFig.getBigPort());
                    if (isSelf) {
                        // Self-message: ensure the inner is a
                        // FigMessageSpline (U-shape). If it's not,
                        // convertToArc — but convertToArc is a no-op
                        // when the FigPoly has 0 points, so we
                        // pre-populate one endpoint first.
                        if (!(inner instanceof org.argouml
                                .sequence2.diagram.FigMessageSpline)) {
                            if (inner instanceof org.tigris.gef
                                    .presentation.FigPoly) {
                                org.tigris.gef.presentation.FigPoly poly =
                                        (org.tigris.gef.presentation.FigPoly) inner;
                                poly.setEndPoints(srcPt, dstPt);
                                poly.setComplete(true);
                            }
                            figEdge.convertToArc();
                        }
                    } else {
                        // Non-self message: the inner must be a
                        // FigPoly with 2 endpoints. If the listener
                        // path already converted it to a FigMessageSpline
                        // (see CRITICAL note above), rebuild a fresh
                        // FigPoly and replace the inner Fig.
                        if (inner instanceof org.argouml.sequence2
                                .diagram.FigMessageSpline) {
                            org.tigris.gef.presentation.FigPoly fresh =
                                    new org.tigris.gef.presentation.FigPoly();
                            fresh.setEndPoints(srcPt, dstPt);
                            fresh.setComplete(true);
                            figEdge.setFig(fresh);
                        } else if (inner instanceof org.tigris.gef
                                .presentation.FigPoly) {
                            org.tigris.gef.presentation.FigPoly poly =
                                    (org.tigris.gef.presentation.FigPoly) inner;
                            poly.setEndPoints(srcPt, dstPt);
                            poly.setComplete(true);
                        } else if (inner == null) {
                            org.tigris.gef.presentation.FigPoly fresh =
                                    new org.tigris.gef.presentation.FigPoly();
                            fresh.setEndPoints(srcPt, dstPt);
                            fresh.setComplete(true);
                            figEdge.setFig(fresh);
                        } else {
                            LOG.log(Level.WARNING,
                                    "  unexpected inner Fig class for message '"
                                    + name + "': "
                                    + inner.getClass().getName());
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING,
                    "MessageOperations fig wiring failed for " + name
                    + ": " + ex.getMessage(), ex);
        }
        return msg;
    }

    /**
     * Find a message on the diagram by ArgoUML UUID ({@code xmi.id}).
     * Null / empty uuid yields null. Scans the graph-model edges
     * (cheaper than walking the namespace) since every message we
     * build via {@link #build} is already registered as an edge.
     */
    public Object findByUuid(ArgoDiagram diagram, String uuid) {
        if (uuid == null || uuid.isEmpty() || diagram == null) {
            return null;
        }
        if (diagram.getGraphModel() == null) {
            return null;
        }
        for (Object edge : diagram.getGraphModel().getEdges()) {
            if (Model.getFacade().isAMessage(edge)
                    && uuid.equals(Model.getFacade().getUUID(edge))) {
                return edge;
            }
        }
        return null;
    }

    /**
     * List every message currently on the diagram (graph-model
     * edge order). Returns an empty list if {@code diagram} is null
     * or has no graph model.
     */
    public List<Object> findAll(ArgoDiagram diagram) {
        List<Object> out = new ArrayList<Object>();
        if (diagram == null || diagram.getGraphModel() == null) {
            return out;
        }
        for (Object edge : diagram.getGraphModel().getEdges()) {
            if (Model.getFacade().isAMessage(edge)) {
                out.add(edge);
            }
        }
        return out;
    }

    /**
     * Remove the message from the graph model and delete the
     * underlying model element. Best-effort: the MDR backend may
     * refuse to delete elements with live inbound references, but
     * the edge has already been removed from the graph model by
     * that point.
     */
    public void delete(ArgoDiagram diagram, Object message) {
        if (diagram == null || message == null) {
            return;
        }
        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();
        if (gm != null && gm.containsEdge(message)) {
            try {
                gm.removeEdge(message);
            } catch (RuntimeException | AssertionError ignored) {
                gm.getEdges().remove(message);
            }
        }
        try {
            Model.getUmlFactory().delete(message);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    /**
     * Resolve the {@code MInteraction} that should own a new
     * message. Looks for an existing interaction on the
     * collaboration; lazy-builds one if none exists. The diagram's
     * namespace (set by the {@code UMLSequenceDiagram} constructor)
     * IS the collaboration.
     */
    private static Object resolveInteraction(ArgoDiagram diagram, Object from) {
        Facade f = Model.getFacade();
        Object collaboration = f.getNamespace(from);
        if (collaboration == null) {
            collaboration = diagram.getNamespace();
        }
        if (collaboration == null) {
            return null;
        }
        Collection interactions = f.getInteractions(collaboration);
        if (interactions != null && !interactions.isEmpty()) {
            return interactions.iterator().next();
        }
        return Model.getCollaborationsFactory()
                .buildInteraction(collaboration);
    }

    /**
     * Apply a {@code MessageSort} (passed as a string name) to a
     * message by creating the corresponding {@code Action} via
     * {@code CommonBehaviorFactory} and setting it via
     * {@code setAction}. The MDR backend's
     * {@code setMessageSort} is hard-coded to delegate to
     * {@code setAction(...)} but the bridge code does not give
     * back-ends a way to advertise their sort vocabulary; the
     * factory-emitted Action is the canonical path used by
     * {@code SequenceDiagramGraphModel.createMessage1}.
     */
    private static void applyMessageAction(Object msg, String messageType) {
        if (msg == null || messageType == null || messageType.isEmpty()) {
            return;
        }
        try {
            Object action = createActionForSort(messageType);
            if (action != null) {
                Model.getCollaborationsHelper().setAction(msg, action);
            }
        } catch (RuntimeException ignored) {
            // best-effort: tolerate backends that can't construct the
            // action (e.g. EUML with no UML 1.4 Action meta-class).
        }
    }

    private static Object createActionForSort(String messageType) {
        org.argouml.model.CommonBehaviorFactory cbf =
                Model.getCommonBehaviorFactory();
        if ("syncCall".equals(messageType)
                || "asyncCall".equals(messageType)) {
            Object action = cbf.createCallAction();
            if ("asyncCall".equals(messageType)) {
                Model.getCommonBehaviorHelper().setAsynchronous(action, true);
            }
            return action;
        }
        if ("asyncSignal".equals(messageType)) {
            Object action = cbf.createSendAction();
            Model.getCommonBehaviorHelper().setAsynchronous(action, true);
            return action;
        }
        if ("reply".equals(messageType)) {
            return cbf.createReturnAction();
        }
        if ("create".equals(messageType)) {
            return cbf.createCreateAction();
        }
        if ("delete".equals(messageType)) {
            return cbf.createDestroyAction();
        }
        return null;
    }

    /**
     * Compute the centre of a port Fig in its parent's coordinate
     * space. For {@code FigClassifierRole.getBigPort()} (a
     * {@code FigRect}) the centre is exactly the connection point
     * used by the message arrow. GEF's
     * {@code Fig.connectionPoint(java.awt.Point)} exists on the
     * base {@code Fig} class but takes a {@link java.awt.Point},
     * not a port Fig, so we resolve directly from the port's
     * bounds.
     *
     * <p>Returns {@code (0, 0)} if the port is null or has
     * zero-size bounds.</p>
     */
    private static java.awt.Point portCenter(
            org.tigris.gef.presentation.Fig port) {
        if (port == null) {
            return new java.awt.Point(0, 0);
        }
        java.awt.Rectangle bounds = port.getBounds();
        return new java.awt.Point(
                bounds.x + bounds.width / 2,
                bounds.y + bounds.height / 2);
    }
}
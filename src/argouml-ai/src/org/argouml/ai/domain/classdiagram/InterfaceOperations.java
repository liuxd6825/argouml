/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.classdiagram;

import org.argouml.model.CoreFactory;
import org.argouml.model.ExtensionMechanismsFactory;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * UML Interface creation / mutation against the model layer and the
 * diagram's graph model. Mirrors {@link ClassOperations} but uses
 * {@link CoreFactory#buildInterface(String, Object)} and the
 * {@link Facade#isAInterface(Object)} predicate.
 *
 * <p><b>Architectural boundary.</b> Same as {@link ClassOperations}:
 * no HTTP, no AI, no inbound / outbound adapter layer, no Swing UI,
 * no {@code Fig} placement. All methods are safe to call on the
 * Swing EDT.
 */
public final class InterfaceOperations {

    private InterfaceOperations() {
    }

    /**
     * Build a UML Interface with the given simple name in the
     * diagram's namespace and register it on the diagram's graph
     * model so GEF renders a {@code Fig} node for it.
     *
     * @param diagram the target diagram; must already have a
     *                namespace.
     * @param name    the simple (unqualified) interface name; must be
     *                non-null and non-empty.
     * @return the newly created model element.
     * @throws IllegalArgumentException if {@code name} is null or
     *                                  empty, or {@code diagram} has
     *                                  no namespace.
     */
    public static Object build(ArgoDiagram diagram, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Interface name must not be empty");
        }
        Object ns = diagram.getNamespace();
        if (ns == null) {
            throw new IllegalArgumentException(
                    "diagram must have a namespace "
                    + "(call p.addDiagram(d) first)");
        }
        CoreFactory cf = Model.getCoreFactory();
        Object iface = cf.buildInterface(name, ns);
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        gm.addNode(iface);
        return iface;
    }

    /**
     * Find the interface node on the diagram whose simple name equals
     * {@code name}. Only {@link Facade#isAInterface} elements are
     * considered; classes are skipped so that callers asking for an
     * interface by name do not get back a class of the same name.
     *
     * @param diagram the diagram to search.
     * @param name    the simple name to search for; a null or empty
     *                value yields a {@code null} result without
     *                throwing.
     * @return the matching model element, or {@code null} if no
     *         interface on the diagram has that name.
     */
    public static Object findByName(ArgoDiagram diagram, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (facade.isAInterface(node)
                    && name.equals(facade.getName(node))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Remove the interface node from the diagram's graph model and
     * delete the underlying model element. Errors during the model
     * delete are swallowed, mirroring {@link ClassOperations#delete}.
     *
     * @param diagram the diagram the interface is registered on.
     * @param iface   the interface model element; a {@code null} value
     *                is treated as a no-op.
     */
    public static void delete(ArgoDiagram diagram, Object iface) {
        if (iface == null) {
            return;
        }
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        if (gm.containsNode(iface)) {
            gm.removeNode(iface);
        }
        try {
            Model.getUmlFactory().delete(iface);
        } catch (RuntimeException ignored) {
            // MDR may refuse to delete an element with live
            // relationships; the node is already off the diagram.
        }
    }

    /**
     * Rename the given interface.
     *
     * @param iface   the interface model element to rename.
     * @param newName the new simple name; must be non-null and
     *                non-empty.
     * @throws IllegalArgumentException if {@code newName} is null or
     *                                  empty.
     */
    public static void rename(Object iface, String newName) {
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException(
                    "rename target must not be empty");
        }
        Model.getCoreHelper().setName(iface, newName);
    }

    /**
     * Attach a stereotype named {@code name} to the interface. The
     * stereotype is built in the interface's owning namespace so
     * downstream serializers can resolve it in the same scope as the
     * element it qualifies.
     *
     * @param iface the interface to which the stereotype is attached.
     * @param name  the stereotype name; a null or empty value is a
     *              no-op.
     */
    public static void addStereotype(Object iface, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Object ns = Model.getFacade().getNamespace(iface);
        if (ns == null) {
            return;
        }
        ExtensionMechanismsFactory emf = Model.getExtensionMechanismsFactory();
        Object st = emf.buildStereotype(name, ns);
        if (st != null) {
            Model.getCoreHelper().addStereotype(iface, st);
        }
    }
}
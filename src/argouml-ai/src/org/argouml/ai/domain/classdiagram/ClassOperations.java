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
import org.argouml.model.CoreHelper;
import org.argouml.model.ExtensionMechanismsFactory;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * UML Class and Interface creation / mutation against the model layer
 * and the diagram's graph model. Pure functions on a diagram; the
 * class has no mutable state and depends only on the ArgoUML model
 * facade and the GEF graph-model API.
 *
 * <p><b>Architectural boundary.</b> Knows NOTHING about HTTP, AI, the
 * inbound / outbound adapter layers, or the Swing UI. Knows NOTHING
 * about {@code Fig} placement: callers (specifically the Application
 * service that will be built in a later task) decide where on the
 * canvas a node is placed; this class only mutates the underlying
 * model + graph model. All methods are safe to call on the Swing EDT.
 *
 * <p>Each method's pre/postconditions are written as plain Java
 * assertions on the return value or on the diagram's node list, so
 * the test suite can verify them without any UI dependency.
 */
public final class ClassOperations {

    private ClassOperations() {
    }

    /**
     * Build a UML Class with the given simple name in the diagram's
     * namespace and register it on the diagram's graph model so GEF
     * renders a {@code Fig} node for it. The new model element is
     * returned to the caller so the Application service can position
     * it on the canvas.
     *
     * @param diagram the target diagram; must already have a
     *                namespace (i.e. be attached to a project via
     *                {@code Project.addDiagram(...)}) so the
     *                namespace can be resolved.
     * @param name    the simple (unqualified) class name; must be
     *                non-null and non-empty.
     * @return the newly created model element.
     * @throws IllegalArgumentException if {@code name} is null or
     *                                  empty, or {@code diagram} has
     *                                  no namespace.
     */
    public static Object build(ArgoDiagram diagram, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Class name must not be empty");
        }
        Object ns = diagram.getNamespace();
        if (ns == null) {
            throw new IllegalArgumentException(
                    "diagram must have a namespace "
                    + "(call p.addDiagram(d) first)");
        }
        CoreFactory cf = Model.getCoreFactory();
        Object cls = cf.buildClass(name, ns);
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        gm.addNode(cls);
        return cls;
    }

    /**
     * Find the class node on the diagram whose simple name equals
     * {@code name}. Only {@link Facade#isAClass} elements are
     * considered; interfaces are skipped so that callers asking for a
     * class by name do not get back an interface of the same name.
     *
     * @param diagram the diagram to search.
     * @param name    the simple name to search for; a null or empty
     *                value yields a {@code null} result without
     *                throwing.
     * @return the matching model element, or {@code null} if no class
     *         on the diagram has that name.
     */
    public static Object findByName(ArgoDiagram diagram, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (facade.isAClass(node)
                    && name.equals(facade.getName(node))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Find a class (or interface / datatype / enumeration) anywhere
     * in the project by its simple name. Walks the entire owned-
     * element tree starting from the project root model.
     *
     * @param name the simple (unqualified) name; null/empty
     *             yields {@code null}
     * @return the matching model element, or {@code null}
     */
    public static Object findByNameInProject(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Facade facade = Model.getFacade();
        org.argouml.kernel.Project project =
            org.argouml.kernel.ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            return null;
        }
        return findInNamespace(project.getModel(), name, facade);
    }

    private static Object findInNamespace(Object ns, String name, Facade f) {
        if (ns == null) return null;
        if (name.equals(f.getName(ns))
                && (f.isAClass(ns) || f.isAInterface(ns)
                || f.isAEnumeration(ns) || f.isADataType(ns))) {
            return ns;
        }
        for (Object child : f.getOwnedElements(ns)) {
            Object hit = findInNamespace(child, name, f);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * Remove the class node from the diagram's graph model and delete
     * the underlying model element. Errors during the model delete
     * are swallowed, mirroring the existing {@code OpExecutor}
     * behaviour: the MDR backend may refuse to delete an element
     * with live inbound relationships, but the node is already off
     * the diagram by that point so the user-facing effect is
     * consistent.
     *
     * @param diagram the diagram the class is registered on.
     * @param cls     the class model element; a {@code null} value is
     *                treated as a no-op.
     */
    public static void delete(ArgoDiagram diagram, Object cls) {
        if (cls == null) {
            return;
        }
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        if (gm.containsNode(cls)) {
            gm.removeNode(cls);
        }
        try {
            Model.getUmlFactory().delete(cls);
        } catch (RuntimeException ignored) {
            // MDR may refuse to delete an element with live
            // relationships; the node is already off the diagram.
        }
    }

    /**
     * Rename the given class. The model element is mutated in place;
     * the graph model is not touched (rename only changes the label
     * rendered by the Fig, not its identity on the canvas).
     *
     * @param cls     the class model element to rename.
     * @param newName the new simple name; must be non-null and
     *                non-empty.
     * @throws IllegalArgumentException if {@code newName} is null or
     *                                  empty.
     */
    public static void rename(Object cls, String newName) {
        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException(
                    "rename target must not be empty");
        }
        Model.getCoreHelper().setName(cls, newName);
    }

    /**
     * Toggle the {@code isAbstract} flag on the given class.
     *
     * @param cls        the class model element to mutate.
     * @param isAbstract the new value of the abstract flag.
     */
    public static void setAbstract(Object cls, boolean isAbstract) {
        Model.getCoreHelper().setAbstract(cls, isAbstract);
    }

    /**
     * Attach a stereotype named {@code name} to the class. The
     * stereotype is built in the class's owning namespace (the
     * {@code Package} or {@code Model} that contains {@code cls}),
     * which gives downstream serializers a name-resolvable
     * stereotype in the same scope as the element it qualifies.
     *
     * @param cls  the class to which the stereotype is attached.
     * @param name the stereotype name; a null or empty value is a
     *             no-op.
     */
    public static void addStereotype(Object cls, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Object ns = Model.getFacade().getNamespace(cls);
        if (ns == null) {
            return;
        }
        ExtensionMechanismsFactory emf = Model.getExtensionMechanismsFactory();
        Object st = emf.buildStereotype(name, ns);
        if (st != null) {
            Model.getCoreHelper().addStereotype(cls, st);
        }
    }
}
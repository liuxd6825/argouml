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

import org.argouml.ai.domain.common.AbstractDiagramElementOperations;
import org.argouml.model.CoreFactory;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * UML Class and Interface creation / mutation. Inherits build /
 * findByName / delete / setPosition from
 * {@link AbstractDiagramElementOperations}; supplies the class-
 * specific factory plus the rename / abstract / stereotype
 * helpers that don't fit the generic CRUD template.
 */
public final class ClassOperations
        extends AbstractDiagramElementOperations<Object> {

    @Override
    protected Object buildImpl(ArgoDiagram diagram, String name) {
        Object cls = Model.getCoreFactory().buildClass(name,
                diagram.getNamespace());
        return cls;
    }

    @Override
    protected boolean isTargetType(Object node) {
        return Model.getFacade().isAClass(node);
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
     */
    public static void setAbstract(Object cls, boolean isAbstract) {
        Model.getCoreHelper().setAbstract(cls, isAbstract);
    }

    /**
     * Attach a stereotype named {@code name} to the class. The
     * stereotype is built in the class's owning namespace.
     */
    public static void addStereotype(Object cls, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        Object ns = Model.getFacade().getNamespace(cls);
        if (ns == null) {
            return;
        }
        org.argouml.model.ExtensionMechanismsFactory emf =
                Model.getExtensionMechanismsFactory();
        Object st = emf.buildStereotype(name, ns);
        if (st != null) {
            Model.getCoreHelper().addStereotype(cls, st);
        }
    }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;

/**
 * Package-level domain operations: create / list / get / delete /
 * moveClass. UML Packages are model elements that own other
 * model elements (classes, interfaces, nested packages, diagrams,
 * etc.); they form a tree rooted at the project's model element.
 *
 * <p>Lives in {@code domain.common} because package management is
 * shared across all diagram kinds.</p>
 *
 * <p><b>Architectural boundary.</b> Pure functions on the model +
 * project; no HTTP, no AI, no inbound / outbound adapter layer,
 * no Swing UI, no {@code Fig} placement. Safe to call on the
 * Swing EDT.</p>
 */
public final class PackageOperations {

    private PackageOperations() {}

    /**
     * Create a new package in the current project.
     *
     * @param name the simple package name; must be non-null, non-empty
     * @param parentName optional name of the parent package; null
     *                  means attach directly under the project root
     * @return the newly created package
     * @throws IllegalArgumentException if name is null/empty, no
     *                                  project is open, parent is
     *                                  missing, a sibling package
     *                                  with the same name exists,
     *                                  etc.
     */
    public static Object create(String name, String parentName) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Package name must not be empty");
        }
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            throw new IllegalArgumentException(
                    "No current project; open or create a project first");
        }
        Object parent = resolveParent(project, parentName);
        // Duplicate-name pre-check: the namespace's owned elements
        // must not already contain a package with the same name.
        for (Object child : Model.getFacade().getOwnedElements(parent)) {
            if (Model.getFacade().isAPackage(child)
                    && name.equals(Model.getFacade().getName(child))) {
                throw new IllegalArgumentException(
                        "Package '" + name + "' already exists in '"
                        + (parentName == null ? "model root" : parentName)
                        + "'");
            }
        }
        Object pkg = Model.getModelManagementFactory().createPackage();
        Model.getCoreHelper().setName(pkg, name);
        Model.getCoreHelper().setNamespace(pkg, parent);
        return pkg;
    }

    /**
     * Return every package in the project, sorted by qualified name
     * (deterministic for the REST layer). The project root is
     * itself a package (the model) and is included in the result
     * with name = project.getModel().getName() (typically "untitledModel").
     */
    public static List<Object> list() {
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            throw new IllegalArgumentException("No current project");
        }
        List<Object> all = new ArrayList<Object>();
        walk(project.getModel(), all);
        Collections.sort(all, (a, b) -> {
            String na = qualifiedName(a);
            String nb = qualifiedName(b);
            return na.compareTo(nb);
        });
        return Collections.unmodifiableList(all);
    }

    /**
     * Find a package by name in the current project. Returns null
     * if not found. Searches depth-first across the full package
     * tree; the first match wins.
     */
    public static Object findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            return null;
        }
        return findByNameRecursive(project.getModel(), name);
    }

    /**
     * Count of owned classes under a package (recursively includes
     * classes in nested sub-packages).
     */
    public static int countClasses(Object pkg) {
        if (pkg == null) return 0;
        int n = 0;
        Facade f = Model.getFacade();
        for (Object child : f.getOwnedElements(pkg)) {
            if (f.isAClass(child) || f.isAInterface(child)
                    || f.isAEnumeration(child) || f.isADataType(child)) {
                n++;
            } else if (f.isAPackage(child)) {
                n += countClasses(child);
            }
        }
        return n;
    }

    /**
     * Delete a package by name. Refuses to delete a non-empty
     * package; caller must move or delete its contents first.
     */
    public static void delete(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Package name must not be empty");
        }
        Object pkg = findByName(name);
        if (pkg == null) {
            throw new IllegalArgumentException(
                    "Package '" + name + "' not found");
        }
        if (countClasses(pkg) > 0) {
            throw new IllegalArgumentException(
                    "Package '" + name + "' is not empty ("
                    + countClasses(pkg) + " classes); move or delete them first");
        }
        if (countNestedPackages(pkg) > 0) {
            throw new IllegalArgumentException(
                    "Package '" + name + "' has "
                    + countNestedPackages(pkg) + " nested package(s); "
                    + "delete or move them first");
        }
        Model.getUmlFactory().delete(pkg);
    }

    /**
     * Move a class from its current namespace to a target package.
     * Updates the class's namespace in the model; the GUI will
     * reflect the move on next render.
     */
    public static void moveClassToPackage(Object cls, Object targetPkg) {
        if (cls == null || targetPkg == null) {
            throw new IllegalArgumentException(
                    "Class and target package must both be non-null");
        }
        if (!Model.getFacade().isAPackage(targetPkg)) {
            throw new IllegalArgumentException(
                    "Target is not a package");
        }
        // Class must be a Class/Interface; otherwise setNamespace
        // would put it in the wrong metamodel slot.
        Facade f = Model.getFacade();
        if (!f.isAClass(cls) && !f.isAInterface(cls)
                && !f.isAEnumeration(cls) && !f.isADataType(cls)) {
            throw new IllegalArgumentException(
                    "Object is not a classifier");
        }
        Model.getCoreHelper().setNamespace(cls, targetPkg);
    }

    /**
     * List the owned classes (recursively) under a package.
     * Returned list is unmodifiable.
     */
    public static List<Object> listClasses(Object pkg) {
        if (pkg == null) {
            throw new IllegalArgumentException("Package must not be null");
        }
        List<Object> out = new ArrayList<Object>();
        walkClasses(pkg, out);
        return Collections.unmodifiableList(out);
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private static Object resolveParent(Project project, String parentName) {
        if (parentName == null || parentName.isEmpty()) {
            return project.getModel();
        }
        Object parent = findByName(parentName);
        if (parent == null) {
            throw new IllegalArgumentException(
                    "Parent package '" + parentName + "' not found");
        }
        if (!Model.getFacade().isAPackage(parent)) {
            throw new IllegalArgumentException(
                    "Parent '" + parentName + "' is not a package");
        }
        return parent;
    }

    private static void walk(Object pkg, List<Object> into) {
        into.add(pkg);
        for (Object child : Model.getFacade().getOwnedElements(pkg)) {
            if (Model.getFacade().isAPackage(child)) {
                walk(child, into);
            }
        }
    }

    private static void walkClasses(Object pkg, List<Object> into) {
        Facade f = Model.getFacade();
        for (Object child : f.getOwnedElements(pkg)) {
            if (f.isAClass(child) || f.isAInterface(child)
                    || f.isAEnumeration(child) || f.isADataType(child)) {
                into.add(child);
            } else if (f.isAPackage(child)) {
                walkClasses(child, into);
            }
        }
    }

    private static int countNestedPackages(Object pkg) {
        int n = 0;
        for (Object child : Model.getFacade().getOwnedElements(pkg)) {
            if (Model.getFacade().isAPackage(child)) n++;
        }
        return n;
    }

    private static Object findByNameRecursive(Object pkg, String name) {
        Facade f = Model.getFacade();
        if (name.equals(f.getName(pkg))) {
            return pkg;
        }
        for (Object child : f.getOwnedElements(pkg)) {
            if (f.isAPackage(child)) {
                Object hit = findByNameRecursive(child, name);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /**
     * Compute a slash-separated qualified name for a package
     * by walking up its namespace chain.
     */
    public static String qualifiedName(Object pkg) {
        if (pkg == null) return "";
        List<String> parts = new ArrayList<String>();
        Object cur = pkg;
        Facade f = Model.getFacade();
        while (cur != null) {
            String n = f.getName(cur);
            if (n != null && !n.isEmpty()) parts.add(0, n);
            Object ns = f.getNamespace(cur);
            if (ns == cur) break;       // safety
            cur = ns;
        }
        return parts.isEmpty() ? "" : String.join("/", parts);
    }
}

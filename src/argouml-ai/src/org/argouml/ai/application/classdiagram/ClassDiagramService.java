/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.application.classdiagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.argouml.ai.application.common.DiagramService;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.common.UndoScope;
import org.argouml.ai.domain.classdiagram.AttributeOperations;
import org.argouml.ai.domain.classdiagram.ClassOperations;
import org.argouml.ai.domain.classdiagram.InterfaceOperations;
import org.argouml.ai.domain.classdiagram.OperationOperations;
import org.argouml.ai.domain.classdiagram.RelationshipOperations;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.domain.common.ModelKind;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.presentation.Fig;

/**
 * Application-layer orchestration for UML class-diagram CRUD.
 *
 * <p>This is the single integration point that BOTH the REST handlers
 * (Batch D) and the AI {@code OpExecutor} (Task 19) call into. It
 * owns no state of its own; the current project comes from the
 * {@link org.argouml.kernel.ProjectManager} singleton via the
 * locator and the UndoManager.</p>
 *
 * <p>Shape of every method:
 * <ol>
 *   <li>validate input strings; throw
 *       {@link InvalidArgumentException} on null/empty where the
 *       contract requires a value;</li>
 *   <li>resolve the diagram through {@link DiagramLocator#byName};
 *       throw {@link NotFoundException} with code
 *       {@code DIAGRAM_NOT_FOUND} on miss;</li>
 *   <li>resolve any referenced class names via
 *       {@link ClassOperations#findByName}; throw
 *       {@link NotFoundException} with code
 *       {@code CLASS_NOT_FOUND} on miss;</li>
 *   <li>for {@code create} operations that may collide, do a
 *       pre-check and throw {@link DuplicateException};</li>
 *   <li>open an {@link UndoScope}, perform the mutation through the
 *       {@code *Operations} domain classes, register a compensating
 *       {@link Runnable} on the scope, and return a small handle
 *       ({@link ClassElement} or the raw model element);</li>
 *   <li>read methods ({@link #listClasses}, {@link #getClass})
 *       return {@link ClassView} POJOs and do not open an
 *       {@code UndoScope}.</li>
 * </ol>
 *
 * <p>Exception codes are the UPPER_SNAKE strings the REST layer
 * maps to JSON {@code error.code} fields:</p>
 * <ul>
 *   <li>{@code INVALID_NAME} (400) — required name missing;</li>
 *   <li>{@code INVALID_RELATIONSHIP_TYPE} (400) — relationship
 *       type string not one of the three supported kinds;</li>
 *   <li>{@code DIAGRAM_NOT_FOUND} (404);</li>
 *   <li>{@code CLASS_NOT_FOUND} (404);</li>
 *   <li>{@code DUPLICATE_CLASS} (409).</li>
 * </ul>
 */
public final class ClassDiagramService implements DiagramService {

    /**
     * Shared per-service instance of {@link ClassOperations} so
     * service code can call instance methods inherited from
     * {@code AbstractDiagramElementOperations} without allocating
     * a new object on every operation. Thread-safe via
     * statelessness (the operations are pure functions on the
     * model).
     */
    private static final ClassOperations CLASS_OPS = new ClassOperations();

    private static final String CODE_INVALID_NAME = "INVALID_NAME";
    private static final String CODE_INVALID_REL_TYPE =
            "INVALID_RELATIONSHIP_TYPE";
    private static final String CODE_DIAGRAM_NOT_FOUND = "DIAGRAM_NOT_FOUND";
    private static final String CODE_CLASS_NOT_FOUND = "CLASS_NOT_FOUND";
    private static final String CODE_DUPLICATE_CLASS = "DUPLICATE_CLASS";
    private static final String CODE_DUPLICATE_INTERFACE = "DUPLICATE_INTERFACE";
    private static final String CODE_DUPLICATE_ATTRIBUTE = "DUPLICATE_ATTRIBUTE";
    private static final String CODE_DUPLICATE_OPERATION = "DUPLICATE_OPERATION";

    private static final String REL_ASSOC = "association";
    private static final String REL_GEN = "generalization";
    private static final String REL_DEP = "dependency";

    public ClassDiagramService() {
    }

    @Override
    public ModelKind kind() {
        return ModelKind.CLASS;
    }

    // -----------------------------------------------------------------
    // Diagram management (project-level, not per-element)
    // -----------------------------------------------------------------

    /**
     * Small DTO returned by {@link #createDiagram} so the REST
     * layer can echo the new diagram's name and kind without
     * leaking the ArgoDiagram object.
     */
    public static final class DiagramHandle {
        public final String name;
        public final String kind;
        public DiagramHandle(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    // -----------------------------------------------------------------
    // Package management (project-level)
    // -----------------------------------------------------------------

    /**
     * DTO for a package. {@code qualifiedName} is the full slash-
     * separated path; {@code classCount} is the recursive count of
     * classifiers (classes, interfaces, enums, datatypes) under
     * this package.
     */
    public static final class PackageView {
        public final String name;
        public final String qualifiedName;
        public final String parent;
        public final int classCount;
        public PackageView(String name, String qualifiedName,
                            String parent, int classCount) {
            this.name = name;
            this.qualifiedName = qualifiedName;
            this.parent = parent;
            this.classCount = classCount;
        }
    }

    /**
     * Create a new package in the current project.
     *
     * @param name the simple package name; must be non-empty
     * @param parentPackageName optional parent package name; null
     *                          attaches directly under the project
     *                          model root
     * @return a {@link PackageView} of the new package
     * @throws InvalidArgumentException if name is null/empty or
     *                                  parent is missing
     * @throws DuplicateException      if a sibling package with
     *                                  that name already exists
     */
    public PackageView createPackage(String name, String parentPackageName) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Package name must not be empty");
        }
        try {
            org.argouml.ai.domain.common.PackageOperations.create(
                    name, parentPackageName);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("already exists")) {
                throw new DuplicateException("DUPLICATE_PACKAGE", msg);
            }
            throw new InvalidArgumentException("INVALID_NAME", msg);
        }
        return getPackage(name);
    }

    /**
     * List every package in the project, sorted by qualified name.
     * Includes the project root (which is itself a package).
     */
    public List<PackageView> listPackages() {
        List<PackageView> out = new java.util.ArrayList<PackageView>();
        for (Object pkg : org.argouml.ai.domain.common.PackageOperations.list()) {
            String qn = org.argouml.ai.domain.common.PackageOperations
                    .qualifiedName(pkg);
            String name = Model.getFacade().getName(pkg);
            // parent: the immediate parent's name (or empty for root)
            Object parent = Model.getFacade().getNamespace(pkg);
            String parentName = parent == null ? ""
                    : Model.getFacade().getName(parent);
            int cnt = org.argouml.ai.domain.common.PackageOperations
                    .countClasses(pkg);
            out.add(new PackageView(name, qn, parentName, cnt));
        }
        return out;
    }

    /**
     * Get a single package by name.
     */
    public PackageView getPackage(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Package name must not be empty");
        }
        Object pkg = org.argouml.ai.domain.common.PackageOperations
                .findByName(name);
        if (pkg == null) {
            throw new NotFoundException("PACKAGE_NOT_FOUND",
                    "Package '" + name + "' not found");
        }
        Object parent = Model.getFacade().getNamespace(pkg);
        String parentName = parent == null ? ""
                : Model.getFacade().getName(parent);
        int cnt = org.argouml.ai.domain.common.PackageOperations
                .countClasses(pkg);
        return new PackageView(
                Model.getFacade().getName(pkg),
                org.argouml.ai.domain.common.PackageOperations
                        .qualifiedName(pkg),
                parentName, cnt);
    }

    /**
     * Delete a package by name. Refuses to delete a non-empty
     * package.
     */
    public void deletePackage(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Package name must not be empty");
        }
        try {
            org.argouml.ai.domain.common.PackageOperations.delete(name);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("not found")) {
                throw new NotFoundException("PACKAGE_NOT_FOUND", msg);
            }
            if (msg.contains("not empty")) {
                throw new DuplicateException("PACKAGE_NOT_EMPTY", msg);
            }
            throw new InvalidArgumentException("INVALID_NAME", msg);
        }
    }

    /**
     * Move a class from its current namespace to a target package.
     * Operates on the class identified by simple name (searches
     * all packages); moves to the named target package.
     */
    public void moveClassToPackage(String className, String targetPackageName) {
        if (className == null || className.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Class name must not be empty");
        }
        if (targetPackageName == null || targetPackageName.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Target package name must not be empty");
        }
        Object cls = org.argouml.ai.domain.classdiagram.ClassOperations
                .findByNameInProject(className);
        if (cls == null) {
            throw new NotFoundException("CLASS_NOT_FOUND",
                    "Class '" + className + "' not found in any package");
        }
        Object target = org.argouml.ai.domain.common.PackageOperations
                .findByName(targetPackageName);
        if (target == null) {
            throw new NotFoundException("PACKAGE_NOT_FOUND",
                    "Target package '" + targetPackageName
                    + "' not found");
        }
        try {
            org.argouml.ai.domain.common.PackageOperations
                    .moveClassToPackage(cls, target);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            throw new InvalidArgumentException("INVALID_NAME", msg);
        }
    }

    /**
     * Create a new class diagram in the current project. The
     * diagram is added to the project immediately and returned to
     * the caller. The diagram's namespace is the project root
     * model element.
     *
     * @param name the simple diagram name; must be non-empty
     * @return a handle with name and kind
     * @throws InvalidArgumentException if name is null/empty
     * @throws DuplicateException      if a diagram with that name
     *                                  already exists
     */
    public DiagramHandle createDiagram(String name) {
        // Back-compat: defaults to this service's own kind (CLASS).
        // New callers should pass a ModelKind explicitly.
        return createDiagram(name, kind());
    }

    public DiagramHandle createDiagram(String name,
                                        org.argouml.ai.domain.common.ModelKind mk) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Diagram name must not be empty");
        }
        if (mk == null) {
            mk = kind();
        }
        try {
            org.argouml.uml.diagram.ArgoDiagram d =
                org.argouml.ai.domain.common.DiagramOperations.create(name, mk);
            return new DiagramHandle(d.getName(),
                    kindOfWire(d));
        } catch (RuntimeException e) {
            // Re-throw with a project-level code for the REST layer.
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("already exists")) {
                throw new DuplicateException("DUPLICATE_DIAGRAM", msg);
            }
            throw new InvalidArgumentException("INVALID_NAME", msg);
        }
    }

    /**
     * List every diagram in the current project. Returns a small
     * view DTO per diagram (name, kind) — no ArgoDiagram leak.
     */
    public List<DiagramView> listDiagrams() {
        List<DiagramView> out = new java.util.ArrayList<DiagramView>();
        for (org.argouml.uml.diagram.ArgoDiagram d :
                org.argouml.ai.domain.common.DiagramOperations.list()) {
            out.add(new DiagramView(d.getName(), kindOfWire(d)));
        }
        return out;
    }

    /**
     * Delete a diagram by name. Idempotent: a missing diagram
     * throws {@link NotFoundException} (DIAGRAM_NOT_FOUND).
     */
    public void deleteDiagram(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Diagram name must not be empty");
        }
        try {
            org.argouml.ai.domain.common.DiagramOperations.delete(name);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("not found")) {
                throw new NotFoundException("DIAGRAM_NOT_FOUND", msg);
            }
            throw new InvalidArgumentException("INVALID_NAME", msg);
        }
    }

    private static String kindOfWire(org.argouml.uml.diagram.ArgoDiagram d) {
        // Match ListDiagramsHandler's convention: derive from the
        // ArgoDiagram's concrete class via DiagramOperations.kindOf
        // (which now understands all ModelKind values), then return
        // the short form ("class" / "usecase") used in API responses.
        if (d == null) {
            return "unknown";
        }
        org.argouml.ai.domain.common.ModelKind k =
                org.argouml.ai.domain.common.DiagramOperations.kindOf(d);
        if (k == null) {
            return "class";
        }
        return k.shortKind();
    }

    /**
     * Small DTO for list / get responses.
     */
    public static final class DiagramView {
        public final String name;
        public final String kind;
        public DiagramView(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }
    }

    // -----------------------------------------------------------------
    // Classes
    // -----------------------------------------------------------------

    public ClassElement createClass(String diagramName, String name,
                                    int x, int y, String stereotype,
                                    boolean isAbstract) {
        requireNonEmptyName(name);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return createClassImpl(d, name, x, y, stereotype, isAbstract);
    }

    /**
     * Diagram-handle variant of {@link #createClass(String, String,
     * int, int, String, boolean)} for callers that already have an
     * {@link ArgoDiagram} reference (notably the AI
     * {@code OpExecutor}, which captures the diagram at construction
     * time). Skips the {@link DiagramLocator#byName} round-trip; the
     * resulting state on the diagram is identical.
     */
    public ClassElement createClass(ArgoDiagram d, String name,
                                    int x, int y, String stereotype,
                                    boolean isAbstract) {
        requireDiagram(d);
        requireNonEmptyName(name);
        return createClassImpl(d, name, x, y, stereotype, isAbstract);
    }

    private ClassElement createClassImpl(ArgoDiagram d, String name,
                                         int x, int y, String stereotype,
                                         boolean isAbstract) {
        if (((ClassOperations) CLASS_OPS).findByName(d, name) != null) {
            throw new DuplicateException(CODE_DUPLICATE_CLASS,
                    "Class '" + name + "' already exists on diagram '"
                    + (d == null ? null : d.getName()) + "'");
        }
        try (UndoScope s = UndoScope.open("CreateClass:" + name)) {
            Object cls = ((ClassOperations) CLASS_OPS).build(d, name);
            if (isAbstract) {
                ClassOperations.setAbstract(cls, true);
            }
            if (stereotype != null && !stereotype.isEmpty()) {
                ClassOperations.addStereotype(cls, stereotype);
            }
            placeFig(d, cls, x, y);
            return new ClassElement(name, cls);
        }
    }

    public ClassElement updateClass(String diagramName, String className,
                                    String newName,
                                    Integer x, Integer y,
                                    String stereotype, Boolean isAbstract) {
        requireNonEmptyName(className);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return updateClassImpl(d, className, newName, x, y, stereotype,
                isAbstract);
    }

    /**
     * Diagram-handle variant of {@link #updateClass(String, String,
     * String, Integer, Integer, String, Boolean)}. See
     * {@link #createClass(ArgoDiagram, String, int, int, String,
     * boolean)} for the rationale.
     */
    public ClassElement updateClass(ArgoDiagram d, String className,
                                    String newName,
                                    Integer x, Integer y,
                                    String stereotype, Boolean isAbstract) {
        requireDiagram(d);
        requireNonEmptyName(className);
        return updateClassImpl(d, className, newName, x, y, stereotype,
                isAbstract);
    }

    private ClassElement updateClassImpl(ArgoDiagram d, String className,
                                         String newName,
                                         Integer x, Integer y,
                                         String stereotype,
                                         Boolean isAbstract) {
        Object cls = mustFindClass(d, className);
        try (UndoScope s = UndoScope.open("UpdateClass:" + className)) {
            if (newName != null && !newName.isEmpty()
                    && !newName.equals(Model.getFacade().getName(cls))) {
                if (((ClassOperations) CLASS_OPS).findByName(d, newName) != null) {
                    throw new DuplicateException(CODE_DUPLICATE_CLASS,
                            "Class '" + newName
                            + "' already exists on diagram '"
                            + d.getName() + "'");
                }
                ClassOperations.rename(cls, newName);
            }
            if (stereotype != null && !stereotype.isEmpty()) {
                ClassOperations.addStereotype(cls, stereotype);
            }
            if (isAbstract != null) {
                ClassOperations.setAbstract(cls, isAbstract.booleanValue());
            }
            if (x != null && y != null) {
                placeFig(d, cls, x.intValue(), y.intValue());
            }
            return new ClassElement(
                    (String) Model.getFacade().getName(cls), cls);
        }
    }

    public void deleteClass(String diagramName, String name) {
        requireNonEmptyName(name);
        ArgoDiagram d = mustFindDiagram(diagramName);
        deleteClassImpl(d, name);
    }

    /**
     * Diagram-handle variant of {@link #deleteClass(String, String)}.
     * See {@link #createClass(ArgoDiagram, String, int, int, String,
     * boolean)} for the rationale.
     */
    public void deleteClass(ArgoDiagram d, String name) {
        requireDiagram(d);
        requireNonEmptyName(name);
        deleteClassImpl(d, name);
    }

    private void deleteClassImpl(ArgoDiagram d, String name) {
        Object cls = mustFindClass(d, name);
        try (UndoScope s = UndoScope.open("DeleteClass:" + name)) {
            ((ClassOperations) CLASS_OPS).delete(d, cls);
        }
    }

    public List<ClassView> listClasses(String diagramName) {
        ArgoDiagram d = mustFindDiagram(diagramName);
        List<ClassView> out = new ArrayList<ClassView>();
        Facade facade = Model.getFacade();
        List nodes = d.getGraphModel().getNodes();
        if (nodes != null) {
            for (Iterator it = nodes.iterator(); it.hasNext();) {
                Object node = it.next();
                if (facade.isAClass(node)) {
                    out.add(buildView(d, node));
                }
            }
        }
        return out;
    }

    public ClassView getClass(String diagramName, String name) {
        requireNonEmptyName(name);
        ArgoDiagram d = mustFindDiagram(diagramName);
        Object cls = mustFindClass(d, name);
        return buildView(d, cls);
    }

    // -----------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------

    /**
     * Snapshot every class's current (x, y) on the given diagram.
     * Returns a defensive list ordered by class name for stable
     * client rendering.
     */
    public List<ClassView> getLayout(String diagramName) {
        ArgoDiagram d = mustFindDiagram(diagramName);
        List<ClassView> all = listClasses(diagramName);
        java.util.Collections.sort(all, new java.util.Comparator<ClassView>() {
            @Override public int compare(ClassView a, ClassView b) {
                return a.name.compareTo(b.name);
            }
        });
        return all;
    }

    /**
     * Auto-arrange the given diagram using ArgoUML's built-in
     * {@link org.argouml.uml.diagram.static_structure.layout.ClassdiagramLayouter}
     * (row/column rank layout). Returns the new positions so the
     * client can update its view without a follow-up GET.
     *
     * <p>Only class diagrams are supported. Calling on any other
     * diagram kind yields {@code UNSUPPORTED_DIAGRAM_TYPE} (400).</p>
     *
     * <p>Wrapped in an {@link UndoScope} so the user can revert with
     * a single undo in the GUI.</p>
     */
    public List<ClassView> reLayout(String diagramName) {
        ArgoDiagram d = mustFindDiagram(diagramName);
        if (!(d instanceof org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram)) {
            throw new InvalidArgumentException("UNSUPPORTED_DIAGRAM_TYPE",
                    "Auto-layout is supported only for class diagrams; '"
                    + diagramName + "' is "
                    + d.getClass().getSimpleName());
        }
        try (UndoScope s = UndoScope.open("ReLayout:" + diagramName)) {
            org.argouml.uml.diagram.static_structure.layout.ClassdiagramLayouter
                    layouter = new org.argouml.uml.diagram.static_structure.layout
                            .ClassdiagramLayouter(d);
            layouter.layout();
            try {
                d.damage();
            } catch (RuntimeException ignored) {
                // damage() may touch editor pane listeners that are
                // absent in headless mode; the actual Fig positions
                // were already moved by the layouter.
            }
        }
        return getLayout(diagramName);
    }

    /**
     * Small DTO describing the outcome of a duplicate-DataType
     * cleanup pass.
     */
    public static final class CleanupReport {
        public final int scanned;
        public final int removed;
        public final java.util.List<String> kept;
        public CleanupReport(int scanned, int removed,
                             java.util.List<String> kept) {
            this.scanned = scanned;
            this.removed = removed;
            this.kept = kept;
        }
    }

    /**
     * Find and remove duplicate {@code DataType} model elements
     * accumulated by older attribute-add paths (before
     * {@code resolveType} deduplicated on insert).
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Walk every {@code DataType} reachable from the project's
     *       root model namespace;</li>
     *   <li>Group them by {@code (name, namespaceIdentity)};</li>
     *   <li>For each group with size &gt; 1, keep the first
     *       occurrence (so existing attribute references are
     *       preserved) and delete the rest;</li>
     *   <li>Return a {@link CleanupReport} with the count and the
     *       list of names that were kept.</li>
     * </ol>
     *
     * <p>This pass does not re-attribute existing attributes - the
     * duplicates were always created as orphan model elements, so
     * removing them is safe. The kept one is the canonical
     * reference for all attributes of that type.</p>
     */
    public CleanupReport cleanupDuplicateDataTypes() {
        Facade facade = Model.getFacade();
        @SuppressWarnings("deprecation")
        org.argouml.kernel.Project project =
                org.argouml.kernel.ProjectManager.getManager()
                        .getCurrentProject();
        if (project == null) {
            return new CleanupReport(0, 0,
                    java.util.Collections.<String>emptyList());
        }
        @SuppressWarnings("deprecation")
        Object model = project.getModel();
        if (model == null) {
            return new CleanupReport(0, 0,
                    java.util.Collections.<String>emptyList());
        }
        // BFS over the namespace tree, collecting every DataType
        java.util.Map<String, java.util.List<Object>> byKey =
                new java.util.LinkedHashMap<String, java.util.List<Object>>();
        java.util.Deque<Object> queue = new java.util.ArrayDeque<Object>();
        queue.add(model);
        int scanned = 0;
        while (!queue.isEmpty()) {
            Object ns = queue.removeFirst();
            java.util.Collection owned = facade.getOwnedElements(ns);
            if (owned == null) {
                continue;
            }
            for (Object e : owned) {
                if (facade.isADataType(e)) {
                    scanned++;
                    String name = facade.getName(e);
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    String key = name + "@" + System.identityHashCode(ns);
                    java.util.List<Object> bucket = byKey.get(key);
                    if (bucket == null) {
                        bucket = new java.util.ArrayList<Object>();
                        byKey.put(key, bucket);
                    }
                    bucket.add(e);
                } else if (facade.isANamespace(e)) {
                    queue.addLast(e);
                }
            }
        }
        // Delete duplicates (keep the first)
        int removed = 0;
        java.util.List<String> kept = new java.util.ArrayList<String>();
        for (java.util.Map.Entry<String, java.util.List<Object>> e
                : byKey.entrySet()) {
            java.util.List<Object> bucket = e.getValue();
            if (bucket.isEmpty()) {
                continue;
            }
            Object canonical = bucket.get(0);
            kept.add(facade.getName(canonical));
            for (int i = 1; i < bucket.size(); i++) {
                try {
                    Model.getUmlFactory().delete(bucket.get(i));
                    removed++;
                } catch (RuntimeException ignored) {
                    // best-effort; the model may refuse to delete a
                    // node that still has inbound references
                }
            }
        }
        java.util.Collections.sort(kept);
        return new CleanupReport(scanned, removed, kept);
    }

    // -----------------------------------------------------------------
    // Interfaces
    // -----------------------------------------------------------------

    public Object createInterface(String diagramName, String name,
                                   int x, int y, String stereotype) {
        requireNonEmptyName(name);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return createInterfaceImpl(d, name, x, y, stereotype);
    }

    /**
     * Diagram-handle variant of {@link #createInterface(String,
     * String, int, int, String)}. See {@link #createClass(ArgoDiagram,
     * String, int, int, String, boolean)} for the rationale.
     */
    public Object createInterface(ArgoDiagram d, String name,
                                   int x, int y, String stereotype) {
        requireDiagram(d);
        requireNonEmptyName(name);
        return createInterfaceImpl(d, name, x, y, stereotype);
    }

    private Object createInterfaceImpl(ArgoDiagram d, String name,
                                       int x, int y, String stereotype) {
        if (InterfaceOperations.findByName(d, name) != null) {
            throw new DuplicateException(CODE_DUPLICATE_INTERFACE,
                    "Interface '" + name + "' already exists on diagram '"
                    + d.getName() + "'");
        }
        try (UndoScope s = UndoScope.open("CreateInterface:" + name)) {
            Object iface = InterfaceOperations.build(d, name);
            if (stereotype != null && !stereotype.isEmpty()) {
                InterfaceOperations.addStereotype(iface, stereotype);
            }
            placeFig(d, iface, x, y);
            return iface;
        }
    }

    // -----------------------------------------------------------------
    // Attributes
    // -----------------------------------------------------------------

    public Object addAttribute(String diagramName, String className,
                               String attrName, String typeName,
                               String visibility) {
        requireNonEmptyName(className);
        requireNonEmptyName(attrName);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return addAttributeImpl(d, className, attrName, typeName, visibility);
    }

    /**
     * Diagram-handle variant of {@link #addAttribute(String, String,
     * String, String, String)}. See {@link #createClass(ArgoDiagram,
     * String, int, int, String, boolean)} for the rationale.
     */
    public Object addAttribute(ArgoDiagram d, String className,
                               String attrName, String typeName,
                               String visibility) {
        requireDiagram(d);
        requireNonEmptyName(className);
        requireNonEmptyName(attrName);
        return addAttributeImpl(d, className, attrName, typeName, visibility);
    }

    private Object addAttributeImpl(ArgoDiagram d, String className,
                                    String attrName, String typeName,
                                    String visibility) {
        Object cls = mustFindClass(d, className);
        if (AttributeOperations.findByName(cls, attrName) != null) {
            throw new DuplicateException(CODE_DUPLICATE_ATTRIBUTE,
                    "Attribute '" + attrName + "' already exists on class '"
                    + className + "'");
        }
        try (UndoScope s = UndoScope.open(
                "AddAttribute:" + className + "." + attrName)) {
            return AttributeOperations.build(
                    cls, attrName, typeName, visibility);
        }
    }

    public void deleteAttribute(String diagramName, String className,
                                String attrName) {
        requireNonEmptyName(className);
        requireNonEmptyName(attrName);
        ArgoDiagram d = mustFindDiagram(diagramName);
        deleteAttributeImpl(d, className, attrName);
    }

    /**
     * Diagram-handle variant of {@link #deleteAttribute(String,
     * String, String)}. See {@link #createClass(ArgoDiagram, String,
     * int, int, String, boolean)} for the rationale.
     */
    public void deleteAttribute(ArgoDiagram d, String className,
                                String attrName) {
        requireDiagram(d);
        requireNonEmptyName(className);
        requireNonEmptyName(attrName);
        deleteAttributeImpl(d, className, attrName);
    }

    private void deleteAttributeImpl(ArgoDiagram d, String className,
                                     String attrName) {
        Object cls = mustFindClass(d, className);
        Object attr = AttributeOperations.findByName(cls, attrName);
        if (attr == null) {
            throw new NotFoundException(CODE_CLASS_NOT_FOUND,
                    "Attribute '" + attrName
                    + "' not found on class '" + className + "'");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteAttribute:" + className + "." + attrName)) {
            AttributeOperations.delete(cls, attr);
        }
    }

    // -----------------------------------------------------------------
    // Operations
    // -----------------------------------------------------------------

    public Object addOperation(String diagramName, String className,
                               String opName, String returnType,
                               String visibility) {
        requireNonEmptyName(className);
        requireNonEmptyName(opName);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return addOperationImpl(d, className, opName, returnType, visibility);
    }

    /**
     * Diagram-handle variant of {@link #addOperation(String, String,
     * String, String, String)}. See {@link #createClass(ArgoDiagram,
     * String, int, int, String, boolean)} for the rationale.
     */
    public Object addOperation(ArgoDiagram d, String className,
                               String opName, String returnType,
                               String visibility) {
        requireDiagram(d);
        requireNonEmptyName(className);
        requireNonEmptyName(opName);
        return addOperationImpl(d, className, opName, returnType, visibility);
    }

    private Object addOperationImpl(ArgoDiagram d, String className,
                                    String opName, String returnType,
                                    String visibility) {
        Object cls = mustFindClass(d, className);
        if (OperationOperations.findByName(cls, opName) != null) {
            throw new DuplicateException(CODE_DUPLICATE_OPERATION,
                    "Operation '" + opName + "' already exists on class '"
                    + className + "'");
        }
        try (UndoScope s = UndoScope.open(
                "AddOperation:" + className + "." + opName)) {
            return OperationOperations.build(
                    cls, opName, returnType, visibility);
        }
    }

    public void deleteOperation(String diagramName, String className,
                                String opName) {
        requireNonEmptyName(className);
        requireNonEmptyName(opName);
        ArgoDiagram d = mustFindDiagram(diagramName);
        deleteOperationImpl(d, className, opName);
    }

    /**
     * Diagram-handle variant of {@link #deleteOperation(String,
     * String, String)}. See {@link #createClass(ArgoDiagram, String,
     * int, int, String, boolean)} for the rationale.
     */
    public void deleteOperation(ArgoDiagram d, String className,
                                String opName) {
        requireDiagram(d);
        requireNonEmptyName(className);
        requireNonEmptyName(opName);
        deleteOperationImpl(d, className, opName);
    }

    private void deleteOperationImpl(ArgoDiagram d, String className,
                                     String opName) {
        Object cls = mustFindClass(d, className);
        Object op = OperationOperations.findByName(cls, opName);
        if (op == null) {
            throw new NotFoundException(CODE_CLASS_NOT_FOUND,
                    "Operation '" + opName
                    + "' not found on class '" + className + "'");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteOperation:" + className + "." + opName)) {
            OperationOperations.delete(cls, op);
        }
    }

    // -----------------------------------------------------------------
    // Relationships
    // -----------------------------------------------------------------

    public Object addAssociation(String diagramName, String a, String b,
                                 String multA, String multB,
                                 String labelA, String labelB) {
        requireNonEmptyName(a);
        requireNonEmptyName(b);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return addAssociationImpl(d, a, b, multA, multB, labelA, labelB);
    }

    /**
     * Diagram-handle variant of {@link #addAssociation(String, String,
     * String, String, String, String, String)}. See
     * {@link #createClass(ArgoDiagram, String, int, int, String,
     * boolean)} for the rationale.
     */
    public Object addAssociation(ArgoDiagram d, String a, String b,
                                 String multA, String multB,
                                 String labelA, String labelB) {
        requireDiagram(d);
        requireNonEmptyName(a);
        requireNonEmptyName(b);
        return addAssociationImpl(d, a, b, multA, multB, labelA, labelB);
    }

    private Object addAssociationImpl(ArgoDiagram d, String a, String b,
                                      String multA, String multB,
                                      String labelA, String labelB) {
        Object ca = mustFindClass(d, a);
        Object cb = mustFindClass(d, b);
        try (UndoScope s = UndoScope.open(
                "AddAssociation:" + a + "->" + b)) {
            return RelationshipOperations.buildAssociation(
                    d, ca, cb, multA, multB, labelA, labelB);
        }
    }

    public Object addGeneralization(String diagramName,
                                    String child, String parent) {
        requireNonEmptyName(child);
        requireNonEmptyName(parent);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return addGeneralizationImpl(d, child, parent);
    }

    /**
     * Diagram-handle variant of {@link #addGeneralization(String,
     * String, String)}. See {@link #createClass(ArgoDiagram, String,
     * int, int, String, boolean)} for the rationale.
     */
    public Object addGeneralization(ArgoDiagram d,
                                    String child, String parent) {
        requireDiagram(d);
        requireNonEmptyName(child);
        requireNonEmptyName(parent);
        return addGeneralizationImpl(d, child, parent);
    }

    private Object addGeneralizationImpl(ArgoDiagram d,
                                         String child, String parent) {
        Object cc = mustFindClass(d, child);
        Object cp = mustFindClass(d, parent);
        try (UndoScope s = UndoScope.open(
                "AddGeneralization:" + child + "->" + parent)) {
            return RelationshipOperations.buildGeneralization(d, cc, cp);
        }
    }

    public Object addDependency(String diagramName,
                                String client, String supplier) {
        requireNonEmptyName(client);
        requireNonEmptyName(supplier);
        ArgoDiagram d = mustFindDiagram(diagramName);
        return addDependencyImpl(d, client, supplier);
    }

    /**
     * Diagram-handle variant of {@link #addDependency(String, String,
     * String)}. See {@link #createClass(ArgoDiagram, String, int,
     * int, String, boolean)} for the rationale.
     */
    public Object addDependency(ArgoDiagram d,
                                String client, String supplier) {
        requireDiagram(d);
        requireNonEmptyName(client);
        requireNonEmptyName(supplier);
        return addDependencyImpl(d, client, supplier);
    }

    private Object addDependencyImpl(ArgoDiagram d,
                                     String client, String supplier) {
        Object cc = mustFindClass(d, client);
        Object cs = mustFindClass(d, supplier);
        try (UndoScope s = UndoScope.open(
                "AddDependency:" + client + "->" + supplier)) {
            return RelationshipOperations.buildDependency(d, cc, cs);
        }
    }

    public void deleteRelationship(String diagramName,
                                   String type, String id) {
        ArgoDiagram d = mustFindDiagram(diagramName);
        deleteRelationshipImpl(d, type, id);
    }

    /**
     * Diagram-handle variant of {@link #deleteRelationship(String,
     * String, String)}. See {@link #createClass(ArgoDiagram, String,
     * int, int, String, boolean)} for the rationale.
     */
    public void deleteRelationship(ArgoDiagram d,
                                   String type, String id) {
        requireDiagram(d);
        deleteRelationshipImpl(d, type, id);
    }

    private void deleteRelationshipImpl(ArgoDiagram d,
                                        String type, String id) {
        if (type == null || type.isEmpty()) {
            throw new InvalidArgumentException(CODE_INVALID_REL_TYPE,
                    "Relationship type must not be empty");
        }
        if (id == null || id.isEmpty()) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Relationship id must not be empty");
        }
        String[] endpoints = id.split("\\|", -1);
        if (endpoints.length != 2) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Relationship id must be of the form 'A|B'");
        }
        String left = endpoints[0];
        String right = endpoints[1];
        if (left.isEmpty() || right.isEmpty()) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Relationship id endpoints must be non-empty");
        }
        Object edge = null;
        if (REL_ASSOC.equalsIgnoreCase(type)) {
            Object ca = mustFindClass(d, left);
            Object cb = mustFindClass(d, right);
            edge = RelationshipOperations.findAssociationBetween(
                    d, ca, cb);
        } else if (REL_GEN.equalsIgnoreCase(type)) {
            Object cc = mustFindClass(d, left);
            Object cp = mustFindClass(d, right);
            edge = RelationshipOperations.findGeneralizationBetween(
                    d, cc, cp);
        } else if (REL_DEP.equalsIgnoreCase(type)) {
            Object cc = mustFindClass(d, left);
            Object cs = mustFindClass(d, right);
            edge = RelationshipOperations.findDependencyBetween(
                    d, cc, cs);
        } else {
            throw new InvalidArgumentException(CODE_INVALID_REL_TYPE,
                    "Unknown relationship type: '" + type
                    + "' (use association|generalization|dependency)");
        }
        if (edge == null) {
            throw new NotFoundException(CODE_CLASS_NOT_FOUND,
                    "Relationship '" + type + "' between '"
                    + left + "' and '" + right
                    + "' not found on diagram '" + d.getName() + "'");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteRelationship:" + type + ":" + id)) {
            RelationshipOperations.delete(d, edge);
        }
    }

    // -----------------------------------------------------------------
    // POJOs
    // -----------------------------------------------------------------

    /**
     * Result of a successful create / update on a UML Class. Carries
     * the resolved simple name (post-rename for updates) plus a
     * handle to the underlying model element so the caller can
     * follow up with positioning, sub-element creation, etc.
     */
    public static final class ClassElement {
        public final String name;
        public final Object element;

        public ClassElement(String name, Object element) {
            this.name = name;
            this.element = element;
        }
    }

    /**
     * Read shape of a UML Class. Attributes are encoded as
     * {@code "name:type"} and operations as {@code "name(params):returnType"},
     * mirroring {@link org.argouml.ai.tools.ProjectSnapshot} so the
     * JSON serialisation stays consistent across the AI and REST
     * read paths.
     */
    public static final class ClassView {
        public final String name;
        public final boolean isAbstract;
        public final List<String> stereotypeNames;
        public final List<String> attributes;
        public final List<String> operations;
        public final int x;
        public final int y;

        public ClassView(String name, boolean isAbstract,
                         List<String> stereotypeNames,
                         List<String> attributes,
                         List<String> operations) {
            this(name, isAbstract, stereotypeNames, attributes, operations, 0, 0);
        }

        public ClassView(String name, boolean isAbstract,
                         List<String> stereotypeNames,
                         List<String> attributes,
                         List<String> operations,
                         int x, int y) {
            this.name = name;
            this.isAbstract = isAbstract;
            this.stereotypeNames = stereotypeNames;
            this.attributes = attributes;
            this.operations = operations;
            this.x = x;
            this.y = y;
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static void requireNonEmptyName(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Name must not be empty");
        }
    }

    private static void requireDiagram(ArgoDiagram d) {
        if (d == null) {
            throw new IllegalArgumentException("diagram must not be null");
        }
    }

    private static ArgoDiagram mustFindDiagram(String name) {
        return org.argouml.ai.application.common.AbstractDiagramServiceHelper
                .requireDiagram(name);
    }

    private static Object mustFindClass(ArgoDiagram d, String name) {
        Object cls = ((ClassOperations) CLASS_OPS).findByName(d, name);
        if (cls == null) {
            throw new NotFoundException(CODE_CLASS_NOT_FOUND,
                    "Class '" + name + "' not found");
        }
        return cls;
    }

    private static void placeFig(ArgoDiagram d, Object element, int x, int y) {
        Fig f = d.presentationFor(element);
        if (f != null) {
            f.setLocation(x, y);
        }
    }

    private static ClassView buildView(Object cls) {
        Facade facade = Model.getFacade();
        String name = stringOrEmpty(facade.getName(cls));
        boolean isAbstract = facade.isAbstract(cls);

        List<String> stereotypeNames = new ArrayList<String>();
        Collection stereos = facade.getStereotypes(cls);
        if (stereos != null) {
            for (Iterator it = stereos.iterator(); it.hasNext();) {
                Object st = it.next();
                stereotypeNames.add(stringOrEmpty(facade.getName(st)));
            }
        }

        List<String> attributes = new ArrayList<String>();
        Collection clsAttrs = facade.getAttributes(cls);
        if (clsAttrs != null) {
            for (Iterator it = clsAttrs.iterator(); it.hasNext();) {
                attributes.add(formatAttribute(it.next()));
            }
        }

        List<String> operations = new ArrayList<String>();
        Collection clsOps = facade.getOperations(cls);
        if (clsOps != null) {
            for (Iterator it = clsOps.iterator(); it.hasNext();) {
                operations.add(formatOperation(it.next()));
            }
        }

        return new ClassView(name, isAbstract,
                Collections.unmodifiableList(stereotypeNames),
                Collections.unmodifiableList(attributes),
                Collections.unmodifiableList(operations),
                0, 0);
    }

    /**
     * Diagram-aware variant of {@link #buildView(Object)} that also
     * pulls the on-diagram position (x, y) from the Fig associated
     * with the model element. Returns (0, 0) when no presentation
     * exists yet.
     */
    private static ClassView buildView(ArgoDiagram d, Object cls) {
        ClassView v = buildView(cls);
        if (d == null) {
            return v;
        }
        Fig f = d.presentationFor(cls);
        if (f == null) {
            return v;
        }
        return new ClassView(v.name, v.isAbstract,
                v.stereotypeNames, v.attributes, v.operations,
                f.getX(), f.getY());
    }

    private static String formatAttribute(Object attr) {
        Facade facade = Model.getFacade();
        return stringOrEmpty(facade.getName(attr)) + ":"
                + typeName(facade.getType(attr));
    }

    private static String formatOperation(Object op) {
        Facade facade = Model.getFacade();
        StringBuilder sb = new StringBuilder();
        sb.append(stringOrEmpty(facade.getName(op)));
        sb.append('(');
        Collection params = facade.getParameters(op);
        if (params != null) {
            boolean first = true;
            for (Iterator it = params.iterator(); it.hasNext();) {
                Object p = it.next();
                if (facade.isReturn(p)) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(stringOrEmpty(facade.getName(p)));
                sb.append(':');
                sb.append(typeName(facade.getType(p)));
            }
        }
        sb.append(')').append(':');
        sb.append(returnTypeName(op));
        return sb.toString();
    }

    private static String returnTypeName(Object op) {
        Collection rets = Model.getCoreHelper().getReturnParameters(op);
        if (rets == null || rets.isEmpty()) {
            return "void";
        }
        Object first = rets.iterator().next();
        Object type = Model.getFacade().getType(first);
        return type == null ? "void" : typeName(type);
    }

    private static String typeName(Object type) {
        if (type == null) {
            return "";
        }
        return stringOrEmpty(Model.getFacade().getName(type));
    }

    private static String stringOrEmpty(String s) {
        return s == null ? "" : s;
    }
}
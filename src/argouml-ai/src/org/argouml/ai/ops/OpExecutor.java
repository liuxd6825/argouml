/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.DiagramServices;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.undo.UndoManager;

/**
 * Applies a list of {@link PlannedOp}s emitted by the AI planner to a
 * target {@link ArgoDiagram}.
 *
 * <p>This executor understands all 10 op kinds declared in
 * {@link PlannedOp.Type}. For each call to {@link #apply} it walks the
 * supplied list in order, dispatching each op to a private
 * {@code applyXxx} method:
 *
 * <ul>
 *   <li>{@link PlannedOp.Type#ADD_CLASS ADD_CLASS} /
 *       {@link PlannedOp.Type#ADD_INTERFACE ADD_INTERFACE} - delegate
 *       to {@link ClassDiagramService#createClass} /
 *       {@link ClassDiagramService#createInterface} which creates the
 *       element, registers it on the diagram's graph model, applies
 *       stereotype/abstract flags, and positions the Fig;</li>
 *   <li>{@link PlannedOp.Type#ADD_ATTRIBUTE ADD_ATTRIBUTE} /
 *       {@link PlannedOp.Type#ADD_OPERATION ADD_OPERATION} - delegate
 *       to {@link ClassDiagramService#addAttribute} /
 *       {@link ClassDiagramService#addOperation} which look up the
 *       owner class by name and attach a typed feature;</li>
 *   <li>{@link PlannedOp.Type#ADD_ASSOCIATION ADD_ASSOCIATION} /
 *       {@link PlannedOp.Type#ADD_GENERALIZATION ADD_GENERALIZATION} /
 *       {@link PlannedOp.Type#ADD_DEPENDENCY ADD_DEPENDENCY} -
 *       delegate to {@link ClassDiagramService#addAssociation} /
 *       {@link ClassDiagramService#addGeneralization} /
 *       {@link ClassDiagramService#addDependency};</li>
 *   <li>{@link PlannedOp.Type#RENAME_CLASS RENAME_CLASS} -
 *       delegate to {@link ClassDiagramService#updateClass};</li>
 *   <li>{@link PlannedOp.Type#DELETE_CLASS DELETE_CLASS} - delegate
 *       to {@link ClassDiagramService#deleteClass};</li>
 *   <li>{@link PlannedOp.Type#LIST_CLASSES LIST_CLASSES} - diagnostic
 *       no-op: the executor logs the existing class names so a developer
 *       running the AI in a console can see what the planner saw.</li>
 * </ul>
 *
 * <p><b>UndoManager:</b> every call to {@link #apply} wraps its ops in
 * a single {@link AiBatchMemento} that is pushed to the global GEF
 * {@link UndoManager#getInstance()} via {@code startChain +
 * addMemento}. Each {@code applyXxx} still pushes a compensating
 * {@link Runnable} onto the {@code undos}/{@code redos} lists even
 * though the underlying service already opens its own
 * {@link org.argouml.ai.application.common.UndoScope}; this is
 * intentional, because the batch-level memento must always be
 * registered even when every per-op undo is a no-op (e.g. for the
 * delete-class {@code @todo} noted below). The service's internal
 * {@code UndoScope} handles project-level grouping via
 * {@code startInteraction(label)}.</p>
 *
 * <p>Thread safety: not thread-safe. ArgoUML's model layer is single
 * threaded; callers must invoke {@code apply} on the Swing EDT or the
 * thread that owns the current {@code Project}.
 */
public class OpExecutor {

    private final ArgoDiagram diagram;

    /**
     * Create a new executor bound to the given diagram. The diagram
     * is captured by reference; the executor does not own its
     * lifecycle.
     *
     * @param diagram the diagram the executor will mutate; must not
     *                be {@code null} and must have a non-null
     *                namespace (i.e. be attached to a project).
     * @throws IllegalArgumentException if {@code diagram} is null or
     *                                  has no namespace.
     */
    public OpExecutor(ArgoDiagram diagram) {
        if (diagram == null) {
            throw new IllegalArgumentException("diagram must not be null");
        }
        if (diagram.getNamespace() == null) {
            throw new IllegalArgumentException(
                    "diagram must have a namespace (attach it to a project "
                    + "via Project.addMemberDiagram() before constructing an "
                    + "OpExecutor)");
        }
        this.diagram = diagram;
    }

    /**
     * Apply each op in order, accumulating the inverse (undo) and
     * forward (redo) mutations into two {@link Runnable} lists. If
     * any op succeeds, a single {@link AiBatchMemento} wrapping both
     * lists is pushed to the global GEF {@link UndoManager}; the
     * {@code DiagramUndoManager} then forwards that memento to the
     * current project's {@code org.argouml.kernel.UndoManager}, so
     * the whole batch becomes one undoable entry. If the op list is
     * empty, or every op is a no-op diagnostic (LIST_CLASSES), no
     * memento is pushed and the undo stack is left untouched.
     *
     * <p>If an op throws partway through, the {@code finally} block
     * still pushes a memento for whatever ops already succeeded;
     * those ops remain on the model and on the undo stack (Ctrl+Z
     * still works to undo them). The exception itself propagates
     * out of this method unchanged so existing callers can rely on
     * the throw-on-first-error contract. Callers that want per-row
     * outcome reporting should use
     * {@link #apply(List, IntConsumer, BiConsumer)} instead.
     *
     * @param ops the ops to apply; {@code null} or an empty list is a
     *            no-op.
     * @throws IllegalArgumentException if an op is missing a required
     *                                  field (e.g. {@code name} for
     *                                  ADD_CLASS) or refers to a
     *                                  class that does not exist on
     *                                  the diagram.
     */
    @SuppressWarnings("deprecation")
    public void apply(List<PlannedOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        org.argouml.kernel.Project project =
            org.argouml.kernel.ProjectManager.getManager().getCurrentProject();
        if (project != null && project.getUndoManager() != null) {
            project.getUndoManager().startInteraction(
                    "AI: apply " + ops.size() + " ops");
        }
        UndoManager.getInstance().startChain();
        List<Runnable> undos = new ArrayList<Runnable>(ops.size());
        List<Runnable> redos = new ArrayList<Runnable>(ops.size());
        try {
            for (int i = 0; i < ops.size(); i++) {
                applyOne(ops.get(i), undos, redos);
            }
        } finally {
            if (!undos.isEmpty()) {
                UndoManager.getInstance().addMemento(
                        new AiBatchMemento(undos, redos));
            }
        }
    }

    /**
     * Apply each op in order, invoking {@code onSuccess} with the
     * 0-based index of each op that completed without throwing, and
     * {@code onFailure} with the failing op's index and the
     * {@link Throwable} it threw for each op that did throw.
     *
     * <p>Unlike {@link #apply(List)}, per-op {@link RuntimeException}s
     * are <em>caught</em> and reported through {@code onFailure}; the
     * loop continues with the next op. Successful ops before a
     * failure remain applied (and are still registered on the undo
     * stack by the enclosing {@code finally} block). This method
     * itself only throws if a callback itself throws - in that case,
     * the offending callback propagates and the batch is left in
     * whatever state the callback was reached at.
     *
     * @param ops        the ops to apply; {@code null} or an empty
     *                   list is a no-op.
     * @param onSuccess  invoked once per successfully applied op with
     *                   the op's index in {@code ops}. Must not be
     *                   {@code null}.
     * @param onFailure  invoked once per failing op with the op's
     *                   index and the thrown {@link Throwable}. Must
     *                   not be {@code null}.
     * @throws IllegalArgumentException if {@code onSuccess} or
     *                                  {@code onFailure} is null.
     */
    @SuppressWarnings("deprecation")
    public void apply(List<PlannedOp> ops,
                      IntConsumer onSuccess,
                      BiConsumer<Integer, Throwable> onFailure) {
        if (onSuccess == null) {
            throw new IllegalArgumentException(
                    "onSuccess callback must not be null");
        }
        if (onFailure == null) {
            throw new IllegalArgumentException(
                    "onFailure callback must not be null");
        }
        if (ops == null || ops.isEmpty()) {
            return;
        }
        org.argouml.kernel.Project project =
            org.argouml.kernel.ProjectManager.getManager().getCurrentProject();
        if (project != null && project.getUndoManager() != null) {
            project.getUndoManager().startInteraction(
                    "AI: apply " + ops.size() + " ops");
        }
        UndoManager.getInstance().startChain();
        List<Runnable> undos = new ArrayList<Runnable>(ops.size());
        List<Runnable> redos = new ArrayList<Runnable>(ops.size());
        try {
            for (int i = 0; i < ops.size(); i++) {
                try {
                    applyOne(ops.get(i), undos, redos);
                    onSuccess.accept(i);
                } catch (RuntimeException ex) {
                    onFailure.accept(Integer.valueOf(i), ex);
                }
            }
        } finally {
            if (!undos.isEmpty()) {
                UndoManager.getInstance().addMemento(
                        new AiBatchMemento(undos, redos));
            }
        }
    }

    /**
     * Dispatch one op to its handler. Package-private so tests can
     * call it directly when they want to bypass the undo plumbing
     * (the public {@link #apply} is the production entry point).
     */
    void applyOne(PlannedOp op, List<Runnable> undos, List<Runnable> redos) {
        switch (op.getType()) {
        case ADD_CLASS:
            applyAddClass(op, undos, redos);
            break;
        case ADD_INTERFACE:
            applyAddInterface(op, undos, redos);
            break;
        case ADD_ATTRIBUTE:
            applyAddAttribute(op, undos, redos);
            break;
        case ADD_OPERATION:
            applyAddOperation(op, undos, redos);
            break;
        case ADD_ASSOCIATION:
            applyAddAssociation(op, undos, redos);
            break;
        case ADD_GENERALIZATION:
            applyAddGeneralization(op, undos, redos);
            break;
        case ADD_DEPENDENCY:
            applyAddDependency(op, undos, redos);
            break;
        case RENAME_CLASS:
            applyRenameClass(op, undos, redos);
            break;
        case DELETE_CLASS:
            applyDeleteClass(op, undos, redos);
            break;
        case LIST_CLASSES:
            applyListClasses(op);
            break;
        default:
            throw new UnsupportedOperationException(
                    "OpExecutor does not yet support " + op.getType()
                    + " (added in a later task)");
        }
    }

    private void applyAddClass(PlannedOp op,
                               List<Runnable> undos, List<Runnable> redos) {
        final String name = requireName(op);
        final String stereotype = op.getString("stereotype");
        final boolean isAbstract = op.getBoolean("isAbstract", false);
        final int x = op.getInt("x");
        final int y = op.getInt("y");
        DiagramServices.classSvc().createClass(
                diagram, name, x, y, stereotype, isAbstract);
        // The service already wraps the model mutation in an
        // UndoScope; we still register a compensating Runnable here
        // so the AiBatchMemento accounting matches the pre-refactor
        // behaviour (one undo/redo pair per op, no skipped batch).
        undos.add(new Runnable() {
            public void run() {
                try {
                    DiagramServices.classSvc().deleteClass(diagram, name);
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().createClass(
                        diagram, name, x, y, stereotype, isAbstract);
            }
        });
    }

    private void applyAddInterface(PlannedOp op,
                                   List<Runnable> undos, List<Runnable> redos) {
        final String name = requireName(op);
        final String stereotype = op.getString("stereotype");
        final int x = op.getInt("x");
        final int y = op.getInt("y");
        DiagramServices.classSvc().createInterface(
                diagram, name, x, y, stereotype);
        undos.add(new Runnable() {
            public void run() {
                // No first-class "deleteInterface" on the MVP
                // service; we only have deleteClass for now, and
                // interfaces are out of scope of the explicit
                // inverse. Keep the undo a best-effort no-op so the
                // batch memento still registers.
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().createInterface(
                        diagram, name, x, y, stereotype);
            }
        });
    }

    private void applyAddAttribute(PlannedOp op,
                                  List<Runnable> undos, List<Runnable> redos) {
        String className = requireField(op, "className");
        String name = requireName(op);
        String typeName = op.getString("type");
        String visibility = op.getString("visibility");
        if (findClassByName(className) == null) {
            throw new IllegalArgumentException(
                    "ADD_ATTRIBUTE: class '" + className
                    + "' not found on diagram");
        }
        DiagramServices.classSvc().addAttribute(
                diagram, className, name, typeName, visibility);
        final String fClassName = className;
        final String fAttrName = name;
        final String fTypeName = typeName;
        final String fVis = visibility;
        undos.add(new Runnable() {
            public void run() {
                try {
                    DiagramServices.classSvc().deleteAttribute(
                            diagram, fClassName, fAttrName);
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().addAttribute(
                        diagram, fClassName, fAttrName, fTypeName, fVis);
            }
        });
    }

    private void applyAddOperation(PlannedOp op,
                                  List<Runnable> undos, List<Runnable> redos) {
        String className = requireField(op, "className");
        String name = requireName(op);
        String returnType = op.getString("returnType");
        String visibility = op.getString("visibility");
        if (findClassByName(className) == null) {
            throw new IllegalArgumentException(
                    "ADD_OPERATION: class '" + className
                    + "' not found on diagram");
        }
        DiagramServices.classSvc().addOperation(
                diagram, className, name, returnType, visibility);
        final String fClassName = className;
        final String fOpName = name;
        final String fReturnType = returnType;
        final String fVis = visibility;
        undos.add(new Runnable() {
            public void run() {
                try {
                    DiagramServices.classSvc().deleteOperation(
                            diagram, fClassName, fOpName);
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().addOperation(
                        diagram, fClassName, fOpName, fReturnType, fVis);
            }
        });
    }

    private void applyAddAssociation(PlannedOp op,
                                     List<Runnable> undos,
                                     List<Runnable> redos) {
        String aName = requireField(op, "classA");
        String bName = requireField(op, "classB");
        if (findClassByName(aName) == null || findClassByName(bName) == null) {
            throw new IllegalArgumentException(
                    "ADD_ASSOCIATION: both '" + aName + "' and '"
                    + bName + "' must exist on the diagram");
        }
        String multA = op.getString("multA");
        String multB = op.getString("multB");
        final String labelA = op.getString("labelA");
        final String labelB = op.getString("labelB");
        DiagramServices.classSvc().addAssociation(
                diagram, aName, bName, multA, multB, labelA, labelB);
        final String fA = aName;
        final String fB = bName;
        final String fId = fA + "|" + fB;
        final String fMultA = multA;
        final String fMultB = multB;
        undos.add(new Runnable() {
            public void run() {
                try {
                    DiagramServices.classSvc().deleteRelationship(
                            diagram, "association", fId);
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().addAssociation(
                        diagram, fA, fB, fMultA, fMultB, labelA, labelB);
            }
        });
    }

    private void applyAddGeneralization(PlannedOp op,
                                        List<Runnable> undos,
                                        List<Runnable> redos) {
        String subName = requireField(op, "subclass");
        String supName = requireField(op, "superclass");
        if (findClassByName(subName) == null
                || findClassByName(supName) == null) {
            throw new IllegalArgumentException(
                    "ADD_GENERALIZATION: both '" + subName + "' and '"
                    + supName + "' must exist on the diagram");
        }
        DiagramServices.classSvc().addGeneralization(
                diagram, subName, supName);
        final String fSub = subName;
        final String fSup = supName;
        final String fId = fSub + "|" + fSup;
        undos.add(new Runnable() {
            public void run() {
                try {
                    DiagramServices.classSvc().deleteRelationship(
                            diagram, "generalization", fId);
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().addGeneralization(
                        diagram, fSub, fSup);
            }
        });
    }

    private void applyAddDependency(PlannedOp op,
                                    List<Runnable> undos,
                                    List<Runnable> redos) {
        String clientName = requireField(op, "client");
        String supplierName = requireField(op, "supplier");
        if (findClassByName(clientName) == null
                || findClassByName(supplierName) == null) {
            throw new IllegalArgumentException(
                    "ADD_DEPENDENCY: both '" + clientName + "' and '"
                    + supplierName + "' must exist on the diagram");
        }
        DiagramServices.classSvc().addDependency(
                diagram, clientName, supplierName);
        final String fClient = clientName;
        final String fSupplier = supplierName;
        final String fId = fClient + "|" + fSupplier;
        undos.add(new Runnable() {
            public void run() {
                try {
                    DiagramServices.classSvc().deleteRelationship(
                            diagram, "dependency", fId);
                } catch (RuntimeException ignored) {
                    // best-effort
                }
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().addDependency(
                        diagram, fClient, fSupplier);
            }
        });
    }

    private void applyRenameClass(PlannedOp op,
                                  List<Runnable> undos,
                                  List<Runnable> redos) {
        String oldName = requireField(op, "oldName");
        final String newName = requireField(op, "newName");
        final Object cls = findClassByName(oldName);
        if (cls == null) {
            throw new IllegalArgumentException(
                    "RENAME_CLASS: class '" + oldName
                    + "' not found on diagram");
        }
        final String prevName = (String) Model.getFacade().getName(cls);
        DiagramServices.classSvc().updateClass(
                diagram, oldName, newName, null, null, null, null);
        undos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().updateClass(
                        diagram, newName, prevName, null, null, null, null);
            }
        });
        redos.add(new Runnable() {
            public void run() {
                DiagramServices.classSvc().updateClass(
                        diagram, prevName, newName, null, null, null, null);
            }
        });
    }

    private void applyDeleteClass(PlannedOp op,
                                  List<Runnable> undos,
                                  List<Runnable> redos) {
        String name = requireName(op);
        if (findClassByName(name) == null) {
            throw new IllegalArgumentException(
                    "DELETE_CLASS: class '" + name
                    + "' not found on diagram");
        }
        DiagramServices.classSvc().deleteClass(diagram, name);
        // @todo: full undo of delete requires snapshotting class
        // state (attributes, operations, relationships) before the
        // delete. The MVP intentionally leaves deleteClass as a
        // diagram-only undo: undo re-adds the class to the diagram
        // but the model element remains gone. We push no undo
        // Runnable for this op; a re-do would re-apply the model
        // delete which is what we want to avoid double-deleting.
        // To prevent the AiBatchMemento from being skipped when the
        // only op is a delete, we still record a no-op undo so the
        // memento is registered.
        undos.add(new Runnable() {
            public void run() {
                // see @todo above
            }
        });
        redos.add(new Runnable() {
            public void run() {
                // see @todo above
            }
        });
    }

    private void applyListClasses(PlannedOp op) {
        Facade facade = Model.getFacade();
        StringBuilder sb = new StringBuilder("[argouml.ai] classes: ");
        boolean first = true;
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (facade.isAClass(node) || facade.isAInterface(node)) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(facade.getName(node));
            }
        }
        System.out.println(sb.toString());
    }

    /**
     * Validate that {@code op} has a non-empty {@code name} field and
     * return it.
     */
    private static String requireName(PlannedOp op) {
        String name = op.getString("name");
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException(
                    op.getType() + " requires a non-empty 'name' field");
        }
        return name;
    }

    /**
     * Variant of {@link #requireName(PlannedOp)} for arbitrary named
     * fields used by the multi-field ops (e.g. {@code className},
     * {@code subclass}, {@code oldName}). The error message names the
     * missing key so the LLM can diagnose the bug in its own
     * arguments.
     */
    private static String requireField(PlannedOp op, String key) {
        String value = op.getString(key);
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(
                    op.getType() + " requires a non-empty '" + key
                    + "' field");
        }
        return value;
    }

    /**
     * Find the class node on the diagram whose name equals
     * {@code name}, or {@code null} if there is no such class.
     *
     * <p>Only {@link Facade#isAClass(Object) isAClass} elements are
     * considered; interfaces are skipped so that callers asking for a
     * class by name do not get back an interface of the same name.
     *
     * @param name the unqualified name to search for; must not be
     *             {@code null}.
     * @return the matching model element, or {@code null}.
     */
    public Object findClassByName(String name) {
        if (name == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (facade.isAClass(node) && name.equals(facade.getName(node))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Package-private accessor retained so subclasses or future
     * extensions can re-enter the diagram; not used by the batch
     * memento (which captures the diagram by closure inside its
     * Runnables).
     */
    ArgoDiagram getDiagram() {
        return diagram;
    }
}
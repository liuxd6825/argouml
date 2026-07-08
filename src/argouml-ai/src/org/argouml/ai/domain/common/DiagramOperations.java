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
import java.util.List;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Diagram-level domain operations: create / list / delete.
 *
 * <p>Lives in {@code domain.common} because diagram management is
 * shared across all diagram kinds (class, use case, etc.). The
 * kind-specific factory is encapsulated inside
 * {@link #create(String, ModelKind)}: future kinds will add a
 * branch there.</p>
 *
 * <p><b>Architectural boundary.</b> Pure functions on the model +
 * project; no HTTP, no AI, no inbound / outbound adapter layer,
 * no Swing UI, no {@code Fig} placement. Safe to call on the
 * Swing EDT.</p>
 */
public final class DiagramOperations {

    private DiagramOperations() {}

    /**
     * Create a new diagram in the current project.
     *
     * <p>Supports {@link ModelKind#CLASS}, {@link ModelKind#USECASE},
     * and {@link ModelKind#SEQUENCE}. ACTIVITY, STATE, and DEPLOYMENT
     * throw {@link IllegalArgumentException} until the corresponding
     * {@code domain.<kind>} sub-package and factory are added.</p>
     *
     * @param name the simple diagram name; must be non-null, non-empty
     * @param kind the diagram kind; one of CLASS / USECASE / SEQUENCE
     * @return the newly created diagram (already added to the project)
     * @throws IllegalArgumentException if name is null/empty, the
     *                                  current project is missing, the
     *                                  kind is unsupported, or a
     *                                  diagram with the same name
     *                                  already exists in the project.
     */
    public static ArgoDiagram create(String name, ModelKind kind) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Diagram name must not be empty");
        }
        if (kind == null) {
            kind = ModelKind.CLASS;
        }
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            throw new IllegalArgumentException(
                    "No current project; open or create a project first");
        }
        // Duplicate-name pre-check (mirrors ClassOperations pre-check
        // in the application layer).
        for (Object d : project.getDiagramList()) {
            ArgoDiagram ad = (ArgoDiagram) d;
            if (name.equals(ad.getName())) {
                throw new IllegalArgumentException(
                        "Diagram '" + name + "' already exists in the project");
            }
        }
        Object ns = project.getModel();
        ArgoDiagram d;
        switch (kind) {
        case CLASS:
            d = new org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram(name, ns);
            break;
        case USECASE:
            d = new org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram(name, ns);
            break;
        case SEQUENCE:
            // UMLSequenceDiagram(Object) expects an MCollaboration, not
            // the project's MModel. The sequence module's
            // SequenceDiagramFactory.createDiagram(owner, ...) first
            // builds a Collaboration via Model.getCollaborationsFactory().
            // buildCollaboration(modelRoot) then passes it to the
            // constructor. Follow the same pattern.
            Object modelRoot = project.getUserDefinedModelList().iterator().next();
            Object collaboration = org.argouml.model.Model.getCollaborationsFactory()
                    .buildCollaboration(modelRoot);
            d = new org.argouml.sequence2.diagram.UMLSequenceDiagram(collaboration);
            try {
                d.setName(name);
            } catch (java.beans.PropertyVetoException e) {
                throw new IllegalArgumentException(
                        "Cannot set name '" + name + "': " + e.getMessage(), e);
            }
            break;
        default:
            // unreachable: enforced above
            throw new IllegalArgumentException("kind=" + kind);
        }
        project.addDiagram(d);
        return d;
    }

    /**
     * Return every diagram in the current project, sorted by name
     * (deterministic for the REST layer).
     *
     * @return an unmodifiable view; empty if the project has none
     * @throws IllegalArgumentException if no project is open
     */
    public static List<ArgoDiagram> list() {
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            throw new IllegalArgumentException("No current project");
        }
        List<ArgoDiagram> all = new ArrayList<ArgoDiagram>();
        for (Object d : project.getDiagramList()) {
            all.add((ArgoDiagram) d);
        }
        java.util.Collections.sort(all, (a, b) -> {
            String na = a.getName() == null ? "" : a.getName();
            String nb = b.getName() == null ? "" : b.getName();
            return na.compareTo(nb);
        });
        return java.util.Collections.unmodifiableList(all);
    }

    /**
     * Remove a diagram from the project by name. Idempotent: a
     * missing diagram throws {@link IllegalArgumentException} with
     * a clear message so the REST layer can return 404.
     *
     * @param name the diagram's display name
     * @throws IllegalArgumentException if no project is open or no
     *                                  diagram with that name exists
     */
    public static void delete(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Diagram name must not be empty");
        }
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            throw new IllegalArgumentException("No current project");
        }
        ArgoDiagram target = null;
        for (Object d : project.getDiagramList()) {
            ArgoDiagram ad = (ArgoDiagram) d;
            if (name.equals(ad.getName())) {
                target = ad;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException(
                    "Diagram '" + name + "' not found");
        }
        // DiagramFactory.removeDiagram is the only public entry
        // point to remove a diagram from a project. (ProjectImpl's
        // own removeDiagram is protected.)
        org.argouml.uml.diagram.DiagramFactory.getInstance()
                .removeDiagram(target);
    }

    /**
     * Map an {@link ArgoDiagram} to its {@link ModelKind} by
     * matching its concrete Java type. Returns {@code null} if the
     * diagram's metaclass is not recognized (should not happen for
     * UML diagrams created through this layer).
     */
    public static ModelKind kindOf(ArgoDiagram d) {
        if (d == null) {
            return null;
        }
        if (d instanceof org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram) {
            return ModelKind.CLASS;
        }
        if (d instanceof org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram) {
            return ModelKind.USECASE;
        }
        if (d instanceof org.argouml.sequence2.diagram.UMLSequenceDiagram) {
            return ModelKind.SEQUENCE;
        }
        return null;
    }
}

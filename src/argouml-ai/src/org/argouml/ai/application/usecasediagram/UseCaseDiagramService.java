/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.application.usecasediagram;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.common.DiagramService;
import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.common.UndoScope;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.domain.common.ModelKind;
import org.argouml.ai.domain.usecasediagram.ActorOperations;
import org.argouml.ai.domain.usecasediagram.ExtendOperations;
import org.argouml.ai.domain.usecasediagram.IncludeOperations;
import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * Application-layer service facade for UML use-case diagrams.
 * Implements {@link DiagramService} with {@link ModelKind#USECASE}.
 *
 * <p>This service is the single integration point REST handlers
 * (Phase 4) will call. It does the same thing
 * {@code ClassDiagramService} does for class diagrams: validates
 * inputs, opens an {@code UndoScope}, delegates to the pure-function
 * domain operations, and exposes small DTOs.</p>
 *
 * <p>The Phase 2 scope intentionally does NOT include HTTP
 * handlers — only the service + domain layer lands. Handlers are
 * queued for Phase 4 in
 * {@code docs/api/SPEC.md}.</p>
 */
public final class UseCaseDiagramService implements DiagramService {

    /**
     * Shared per-service instance of {@link ActorOperations} so
     * service code can call instance methods inherited from
     * {@code AbstractDiagramElementOperations} without allocating
     * a new object on every operation. Thread-safe via
     * statelessness (the operations are pure functions on the
     * model).
     */
    private static final ActorOperations ACTOR_OPS = new ActorOperations();
    private static final UseCaseOperations USE_CASE_OPS = new UseCaseOperations();

    @Override
    public ModelKind kind() {
        return ModelKind.USECASE;
    }

    // ---- Diagram lookup helpers ----

    private ArgoDiagram requireDiagram(String name) {
        return org.argouml.ai.application.common.AbstractDiagramServiceHelper
                .requireDiagram(name);
    }

    private void requireUseCaseDiagram(ArgoDiagram d) {
        if (!(d instanceof org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram)) {
            throw new InvalidArgumentException("UNSUPPORTED_DIAGRAM_TYPE",
                    "Use-case API only operates on use-case diagrams; '"
                    + d.getName() + "' is "
                    + d.getClass().getSimpleName());
        }
    }

    // ---- Actor ----

    public ActorView createActor(String diagramName, String name,
                                 int x, int y) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Actor name must not be empty");
        }
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        if (((ActorOperations) ACTOR_OPS).findByName(d, name) != null) {
            throw new DuplicateException("DUPLICATE_ACTOR",
                    "Actor '" + name + "' already exists on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("CreateActor:" + name)) {
            Object actor = ((ActorOperations) ACTOR_OPS).build(d, name);
            ((ActorOperations) ACTOR_OPS).setPosition(d, actor, x, y);
            String uuid = Model.getFacade().getUUID(actor);
            return new ActorView(name, uuid, x, y);
        }
    }

    public List<ActorView> listActors(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        List<ActorView> out = new ArrayList<ActorView>();
        Facade facade = Model.getFacade();
        for (Object node : d.getGraphModel().getNodes()) {
            if (facade.isAActor(node)) {
                String name = facade.getName(node);
                org.tigris.gef.presentation.Fig f = d.presentationFor(node);
                int x = f == null ? 0 : f.getX();
                int y = f == null ? 0 : f.getY();
                String uuid = Model.getFacade().getUUID(node);
                out.add(new ActorView(name, uuid, x, y));
            }
        }
        return out;
    }

    public ActorView getActor(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ((ActorOperations) ACTOR_OPS).findByName(d, name);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        org.tigris.gef.presentation.Fig f = d.presentationFor(actor);
        String uuid = Model.getFacade().getUUID(actor);
        return new ActorView(name, uuid,
                f == null ? 0 : f.getX(),
                f == null ? 0 : f.getY());
    }

    public void deleteActor(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ((ActorOperations) ACTOR_OPS).findByName(d, name);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteActor:" + name)) {
            ((ActorOperations) ACTOR_OPS).delete(d, actor);
        }
    }

    public void setActorPosition(String diagramName, String name,
                                  int x, int y) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ((ActorOperations) ACTOR_OPS).findByName(d, name);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + name + "' not found");
        }
        try (UndoScope s = UndoScope.open("MoveActor:" + name)) {
            ((ActorOperations) ACTOR_OPS).setPosition(d, actor, x, y);
        }
    }

    // ---- UseCase ----

    public UseCaseView createUseCase(String diagramName, String name,
                                     String description, int x, int y) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "UseCase name must not be empty");
        }
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        if (((UseCaseOperations) USE_CASE_OPS).findByName(d, name) != null) {
            throw new DuplicateException("DUPLICATE_USECASE",
                    "UseCase '" + name + "' already exists on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("CreateUseCase:" + name)) {
            Object useCase = ((UseCaseOperations) USE_CASE_OPS).build(d, name, description);
            ((UseCaseOperations) USE_CASE_OPS).setPosition(d, useCase, x, y);
            String uuid = Model.getFacade().getUUID(useCase);
            return new UseCaseView(name, uuid,
                    description == null ? "" : description, x, y);
        }
    }

    public List<UseCaseView> listUseCases(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        List<UseCaseView> out = new ArrayList<UseCaseView>();
        Facade facade = Model.getFacade();
        for (Object node : d.getGraphModel().getNodes()) {
            if (facade.isAUseCase(node)) {
                String name = facade.getName(node);
                String desc = readDescription(node);
                org.tigris.gef.presentation.Fig f = d.presentationFor(node);
                int x = f == null ? 0 : f.getX();
                int y = f == null ? 0 : f.getY();
                String uuid = Model.getFacade().getUUID(node);
                out.add(new UseCaseView(name, uuid, desc, x, y));
            }
        }
        return out;
    }

    public UseCaseView getUseCase(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = ((UseCaseOperations) USE_CASE_OPS).findByName(d, name);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        org.tigris.gef.presentation.Fig f = d.presentationFor(useCase);
        String uuid = Model.getFacade().getUUID(useCase);
        return new UseCaseView(name, uuid,
                readDescription(useCase),
                f == null ? 0 : f.getX(),
                f == null ? 0 : f.getY());
    }

    public void deleteUseCase(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = ((UseCaseOperations) USE_CASE_OPS).findByName(d, name);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteUseCase:" + name)) {
            ((UseCaseOperations) USE_CASE_OPS).delete(d, useCase);
        }
    }

    public void setUseCasePosition(String diagramName, String name,
                                    int x, int y) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = ((UseCaseOperations) USE_CASE_OPS).findByName(d, name);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + name + "' not found");
        }
        try (UndoScope s = UndoScope.open("MoveUseCase:" + name)) {
            ((UseCaseOperations) USE_CASE_OPS).setPosition(d, useCase, x, y);
        }
    }

    // ---- Relationships ----

    public Map<String, Object> createAssociation(String diagramName,
                                                String actorName,
                                                String useCaseName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ((ActorOperations) ACTOR_OPS).findByName(d, actorName);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + actorName + "' not found");
        }
        Object useCase = ((UseCaseOperations) USE_CASE_OPS).findByName(d, useCaseName);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + useCaseName + "' not found");
        }
        try (UndoScope s = UndoScope.open(
                "CreateAssoc:" + actorName + "->" + useCaseName)) {
            org.argouml.model.CoreFactory cf = Model.getCoreFactory();
            Object assoc = cf.buildAssociation(actor, useCase);
            if (assoc == null) {
                throw new InvalidArgumentException("INVALID_RELATIONSHIP",
                        "CoreFactory refused to build association "
                        + "between '" + actorName + "' and '" + useCaseName
                        + "'");
            }
            // Add the edge to the diagram's graph model so ArgoUML
            // actually draws the line on the canvas. Without this,
            // the MAssociation exists in the model but is invisible
            // on the use case diagram (matches the user's report:
            // "在包中可以看到关系线，但在用例图中没有看到关系线").
            MutableGraphModel gm =
                    (MutableGraphModel) d.getGraphModel();
            if (gm != null && !gm.getEdges().contains(assoc)) {
                gm.addEdge(assoc);
            }
            String id = actorName + "|" + useCaseName;
            String uuid = Model.getFacade().getUUID(assoc);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("id", id);
            out.put("uuid", uuid == null ? "" : uuid);
            out.put("actor", actorName);
            out.put("usecase", useCaseName);
            return out;
        }
    }

    public List<Map<String, Object>> listAssociations(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        // Scan the namespace tree for MAssociation elements; the
        // use-case diagram's graph model does not automatically
        // surface MAssociation as graph edges.
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        Facade facade = Model.getFacade();
        java.util.Collection assocs = findAllAssociationsIn(d);
        for (Object assoc : assocs) {
            Object[] pair = actorUseCasePair(facade, assoc);
            if (pair == null) {
                continue;
            }
            String actorName = facade.getName(pair[0]);
            String ucName = facade.getName(pair[1]);
            if (actorName == null || ucName == null) {
                continue;
            }
            Map<String, Object> e = new LinkedHashMap<String, Object>();
            e.put("id", actorName + "|" + ucName);
            e.put("uuid", facade.getUUID(assoc));
            e.put("actor", actorName);
            e.put("usecase", ucName);
            out.add(e);
        }
        return out;
    }

    public void deleteAssociation(String diagramName, String id) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        int pipe = id.indexOf('|');
        if (pipe < 1 || pipe == id.length() - 1) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Association id must be 'actor|usecase'");
        }
        String actorName = id.substring(0, pipe);
        String ucName = id.substring(pipe + 1);
        Object actor = ((ActorOperations) ACTOR_OPS).findByName(d, actorName);
        Object useCase = ((UseCaseOperations) USE_CASE_OPS).findByName(d, ucName);
        if (actor == null || useCase == null) {
            throw new NotFoundException("ASSOCIATION_NOT_FOUND",
                    "Association " + id + " not found");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteAssoc:" + id)) {
            Facade facade = Model.getFacade();
            for (Object assoc : findAllAssociationsIn(d)) {
                Object[] pair = actorUseCasePair(facade, assoc);
                if (pair == null) {
                    continue;
                }
                if (pair[0] == actor && pair[1] == useCase) {
                    try {
                        Model.getUmlFactory().delete(assoc);
                    } catch (RuntimeException ignored) {
                    }
                    // Also remove from graph model if present
                    try {
                        MutableGraphModel gm =
                                (MutableGraphModel) d.getGraphModel();
                        if (gm.containsEdge(assoc)) {
                            gm.removeEdge(assoc);
                        }
                    } catch (RuntimeException ignored) {
                    }
                    return;
                }
            }
            throw new NotFoundException("ASSOCIATION_NOT_FOUND",
                    "Association edge " + id + " not found on diagram");
        }
    }

    /**
     * Recursively collect every MAssociation reachable from the
     * diagram's namespace. Skips non-association model elements.
     */
    private static java.util.Collection<Object> findAllAssociationsIn(
            ArgoDiagram d) {
        java.util.List<Object> out = new java.util.ArrayList<Object>();
        Object ns = d.getNamespace();
        if (ns == null) {
            return out;
        }
        walkForAssociations(ns, out);
        return out;
    }

    private static void walkForAssociations(Object ns,
                                           java.util.List<Object> out) {
        Facade facade = Model.getFacade();
        java.util.Collection owned = facade.getOwnedElements(ns);
        if (owned == null) {
            return;
        }
        for (Object e : owned) {
            if (facade.isAAssociation(e)) {
                out.add(e);
            }
            if (facade.isANamespace(e)) {
                walkForAssociations(e, out);
            }
        }
    }

    /**
     * Given an MAssociation, return {@code [actor, usecase]} or
     * {@code null} if the ends are not exactly one actor and one
     * use case.
     */
    private static Object[] actorUseCasePair(Facade facade, Object assoc) {
        java.util.Collection ends = facade.getConnections(assoc);
        if (ends == null || ends.size() != 2) {
            return null;
        }
        Object[] arr = ends.toArray();
        Object t0 = facade.getType(arr[0]);
        Object t1 = facade.getType(arr[1]);
        if (t0 == null || t1 == null) {
            return null;
        }
        boolean t0IsActor = facade.isAActor(t0);
        boolean t1IsActor = facade.isAActor(t1);
        boolean t0IsUseCase = facade.isAUseCase(t0);
        boolean t1IsUseCase = facade.isAUseCase(t1);
        if (t0IsActor && t1IsUseCase) {
            return new Object[] {t0, t1};
        }
        if (t0IsUseCase && t1IsActor) {
            return new Object[] {t1, t0};
        }
        return null;
    }

    public Map<String, Object> createInclude(String diagramName,
                                            String baseName,
                                            String inclusionName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object base = ((UseCaseOperations) USE_CASE_OPS).findByName(d, baseName);
        if (base == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + baseName + "' not found");
        }
        Object inclusion = ((UseCaseOperations) USE_CASE_OPS).findByName(d, inclusionName);
        if (inclusion == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + inclusionName + "' not found");
        }
        try (UndoScope s = UndoScope.open(
                "CreateInclude:" + baseName + "->" + inclusionName)) {
            Object inc = IncludeOperations.build(base, inclusion);
            if (inc == null) {
                throw new InvalidArgumentException("INVALID_RELATIONSHIP",
                        "UseCasesFactory refused to build include");
            }
            // Add the include edge to the diagram's graph model
            // (MInclude is a model-level relationship; without
            // gm.addEdge the ArgoUML canvas won't render the line).
            MutableGraphModel gm =
                    (MutableGraphModel) d.getGraphModel();
            if (gm != null && !gm.getEdges().contains(inc)) {
                gm.addEdge(inc);
            }
            String id = baseName + "|" + inclusionName;
            String uuid = Model.getFacade().getUUID(inc);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("id", id);
            out.put("uuid", uuid == null ? "" : uuid);
            out.put("base", baseName);
            out.put("inclusion", inclusionName);
            return out;
        }
    }

    public void deleteInclude(String diagramName, String id) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        int pipe = id.indexOf('|');
        if (pipe < 1 || pipe == id.length() - 1) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Include id must be 'base|inclusion'");
        }
        String baseName = id.substring(0, pipe);
        String inclusionName = id.substring(pipe + 1);
        Object base = ((UseCaseOperations) USE_CASE_OPS).findByName(d, baseName);
        Object inclusion = ((UseCaseOperations) USE_CASE_OPS).findByName(d, inclusionName);
        if (base == null || inclusion == null) {
            throw new NotFoundException("INCLUDE_NOT_FOUND",
                    "Include " + id + " not found");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteInclude:" + id)) {
            // Scan the namespace for MInclude elements that
            // connect this base to this inclusion.
            Facade facade = Model.getFacade();
            for (Object inc : findAllElements(d)) {
                if (!isMInclude(inc)) {
                    continue;
                }
                Object incBase = facade.getBase(inc);
                Object incInclusion = facade.getAddition(inc);
                if (incBase == base && incInclusion == inclusion) {
                    try {
                        Model.getUmlFactory().delete(inc);
                    } catch (RuntimeException ignored) {
                    }
                    return;
                }
            }
            throw new NotFoundException("INCLUDE_NOT_FOUND",
                    "Include " + id + " not found on diagram");
        }
    }

    /**
     * Walk the namespace tree and collect every model element
     * reachable from the diagram's namespace. Caller is responsible
     * for the discriminator (isA test).
     */
    private static java.util.List<Object> findAllElements(ArgoDiagram d) {
        java.util.List<Object> out = new java.util.ArrayList<Object>();
        Object ns = d == null ? null : d.getNamespace();
        if (ns == null) {
            return out;
        }
        walkAll(ns, out);
        return out;
    }

    private static void walkAll(Object ns, java.util.List<Object> out) {
        Facade facade = Model.getFacade();
        java.util.Collection owned = facade.getOwnedElements(ns);
        if (owned == null) {
            return;
        }
        for (Object e : owned) {
            out.add(e);
            if (facade.isANamespace(e)) {
                walkAll(e, out);
            }
        }
    }

    private static boolean isMInclude(Object e) {
        // Use the metadata class name as a portable discriminator;
        // ArgoUML MDR exposes MIncludeImpl instances.
        String cn = e.getClass().getSimpleName();
        return cn.contains("Include");
    }

    public Map<String, Object> createExtend(String diagramName,
                                           String baseName,
                                           String extensionName,
                                           String extensionPoint) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object base = ((UseCaseOperations) USE_CASE_OPS).findByName(d, baseName);
        if (base == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + baseName + "' not found");
        }
        Object extension = ((UseCaseOperations) USE_CASE_OPS).findByName(d, extensionName);
        if (extension == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + extensionName + "' not found");
        }
        try (UndoScope s = UndoScope.open(
                "CreateExtend:" + baseName + "<-" + extensionName)) {
            Object ext = ExtendOperations.build(base, extension, extensionPoint);
            if (ext == null) {
                throw new InvalidArgumentException("INVALID_RELATIONSHIP",
                        "UseCasesFactory refused to build extend");
            }
            // Add the extend edge to the diagram's graph model
            // (MExtend is a model-level relationship; without
            // gm.addEdge the ArgoUML canvas won't render the line).
            MutableGraphModel gm =
                    (MutableGraphModel) d.getGraphModel();
            if (gm != null && !gm.getEdges().contains(ext)) {
                gm.addEdge(ext);
            }
            String id = baseName + "|" + extensionName;
            String uuid = Model.getFacade().getUUID(ext);
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("id", id);
            out.put("uuid", uuid == null ? "" : uuid);
            out.put("base", baseName);
            out.put("extension", extensionName);
            out.put("extensionPoint", extensionPoint == null
                    ? "" : extensionPoint);
            return out;
        }
    }

    public void deleteExtend(String diagramName, String id) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        int pipe = id.indexOf('|');
        if (pipe < 1 || pipe == id.length() - 1) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Extend id must be 'base|extension'");
        }
        String baseName = id.substring(0, pipe);
        String extensionName = id.substring(pipe + 1);
        Object base = ((UseCaseOperations) USE_CASE_OPS).findByName(d, baseName);
        Object extension = ((UseCaseOperations) USE_CASE_OPS).findByName(d, extensionName);
        if (base == null || extension == null) {
            throw new NotFoundException("EXTEND_NOT_FOUND",
                    "Extend " + id + " not found");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteExtend:" + id)) {
            Object ext = ExtendOperations.find(d, base, extension);
            if (ext == null) {
                throw new NotFoundException("EXTEND_NOT_FOUND",
                        "Extend " + id + " not found");
            }
            ExtendOperations.delete(ext);
        }
    }

    // ---- DTOs ----

    public static final class ActorView {
        public final String name;
        public final String uuid;
        public final int x;
        public final int y;
        public ActorView(String name, String uuid, int x, int y) {
            this.name = name;
            this.uuid = uuid == null ? "" : uuid;
            this.x = x;
            this.y = y;
        }
    }

    public static final class UseCaseView {
        public final String name;
        public final String uuid;
        public final String description;
        public final int x;
        public final int y;
        public UseCaseView(String name, String uuid, String description,
                           int x, int y) {
            this.name = name;
            this.uuid = uuid == null ? "" : uuid;
            this.description = description == null ? "" : description;
            this.x = x;
            this.y = y;
        }
    }

    // ---- helpers ----

    private static String readDescription(Object useCase) {
        return UseCaseOperations.getDescription(useCase);
    }
}

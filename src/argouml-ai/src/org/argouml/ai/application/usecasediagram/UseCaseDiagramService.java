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
import java.util.List;

import org.argouml.ai.application.common.AbstractDiagramServiceHelper;
import org.argouml.ai.application.common.DiagramService;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.common.UndoScope;
import org.argouml.ai.domain.common.ModelKind;
import org.argouml.ai.domain.entity.UsecaseActorEntity;
import org.argouml.ai.domain.entity.UsecaseAssociationEntity;
import org.argouml.ai.domain.entity.UsecaseExtendEntity;
import org.argouml.ai.domain.entity.UsecaseIncludeEntity;
import org.argouml.ai.domain.entity.UsecaseUseCaseEntity;
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
 *
 * <p>All public methods return immutable {@link org.argouml.ai.domain.entity.Identified}
 * entity objects (actors → {@link UsecaseActorEntity}, use cases →
 * {@link UsecaseUseCaseEntity}, edges → {@link UsecaseAssociationEntity} /
 * {@link UsecaseIncludeEntity} / {@link UsecaseExtendEntity}). Every entity
 * carries an ArgoUML UUID (xmi.id) so callers can disambiguate
 * elements that share a name within a namespace.</p>
 *
 * <p>Lookup primitives: each kind has a {@code findByName} method
 * (used by the human-facing API endpoints) and a
 * {@code findByUuid} method (used by the by-uuid endpoints and
 * the move / delete paths that already hold an entity).</p>
 *
 * <p>This service is the single integration point REST handlers
 * call. It validates inputs, opens an {@code UndoScope}, delegates
 * to the pure-function domain operations, and exposes small
 * immutable entities.</p>
 */
public final class UseCaseDiagramService implements DiagramService {

    private static final ActorOperations ACTOR_OPS = new ActorOperations();
    private static final UseCaseOperations USE_CASE_OPS = new UseCaseOperations();

    @Override
    public ModelKind kind() {
        return ModelKind.USECASE;
    }

    // ---- Diagram lookup helpers ----

    private ArgoDiagram requireDiagram(String name) {
        return AbstractDiagramServiceHelper.requireDiagram(name);
    }

    private void requireUseCaseDiagram(ArgoDiagram d) {
        if (!(d instanceof org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram)) {
            throw new InvalidArgumentException("UNSUPPORTED_DIAGRAM_TYPE",
                    "Use-case API only operates on use-case diagrams; '"
                    + d.getName() + "' is "
                    + d.getClass().getSimpleName());
        }
    }

    private static String diagramUuidOf(ArgoDiagram d) {
        if (d == null) {
            return "";
        }
        // ArgoDiagram is not a UML model element, so
        // Model.getFacade().getUUID(d) throws IllegalArgumentException.
        // Use the diagram's namespace (an MNamespace, a real model
        // element) as the stable identity proxy.
        Object ns = d.getNamespace();
        if (ns == null) {
            return "";
        }
        try {
            String u = Model.getFacade().getUUID(ns);
            return u == null ? "" : u;
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int xOf(ArgoDiagram d, Object node) {
        if (d == null || node == null) {
            return 0;
        }
        org.tigris.gef.presentation.Fig f = d.presentationFor(node);
        return f == null ? 0 : f.getX();
    }

    private static int yOf(ArgoDiagram d, Object node) {
        if (d == null || node == null) {
            return 0;
        }
        org.tigris.gef.presentation.Fig f = d.presentationFor(node);
        return f == null ? 0 : f.getY();
    }

    private static String uuidOf(Object node) {
        if (node == null) {
            return "";
        }
        String u = Model.getFacade().getUUID(node);
        return u == null ? "" : u;
    }

    // ---- Actor ----

    public UsecaseActorEntity createActor(String diagramName, String name,
                                   int x, int y) {
        AbstractDiagramServiceHelper.requireNonEmptyName(name);
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        if (ACTOR_OPS.findByName(d, name) != null) {
            throw new DuplicateException("DUPLICATE_ACTOR",
                    "Actor '" + name + "' already exists on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("CreateActor:" + name)) {
            Object actor = ACTOR_OPS.build(d, name);
            ACTOR_OPS.setPosition(d, actor, x, y);
            return new UsecaseActorEntity(uuidOf(actor), name,
                    diagramUuidOf(d), x, y);
        }
    }

    public List<UsecaseActorEntity> listActors(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        List<UsecaseActorEntity> out = new ArrayList<UsecaseActorEntity>();
        Facade facade = Model.getFacade();
        String dUuid = diagramUuidOf(d);
        for (Object node : d.getGraphModel().getNodes()) {
            if (facade.isAActor(node)) {
                String name = facade.getName(node);
                out.add(new UsecaseActorEntity(uuidOf(node), name, dUuid,
                        xOf(d, node), yOf(d, node)));
            }
        }
        return out;
    }

    public UsecaseActorEntity getActorByName(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ACTOR_OPS.findByName(d, name);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        return new UsecaseActorEntity(uuidOf(actor), name, diagramUuidOf(d),
                xOf(d, actor), yOf(d, actor));
    }

    public UsecaseActorEntity findActorByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ACTOR_OPS.findByUuid(d, uuid);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Facade facade = Model.getFacade();
        return new UsecaseActorEntity(uuidOf(actor), facade.getName(actor),
                diagramUuidOf(d),
                xOf(d, actor), yOf(d, actor));
    }

    public void deleteActorByName(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ACTOR_OPS.findByName(d, name);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteActor:" + name)) {
            ACTOR_OPS.delete(d, actor);
        }
    }

    public void deleteActorByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ACTOR_OPS.findByUuid(d, uuid);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteActor:uuid=" + uuid)) {
            ACTOR_OPS.delete(d, actor);
        }
    }

    public UsecaseActorEntity setActorPosition(String diagramName, String name,
                                        int x, int y) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ACTOR_OPS.findByName(d, name);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + name + "' not found");
        }
        try (UndoScope s = UndoScope.open("MoveActor:" + name)) {
            ACTOR_OPS.setPosition(d, actor, x, y);
        }
        return new UsecaseActorEntity(uuidOf(actor), name, diagramUuidOf(d), x, y);
    }

    // ---- UseCase ----

    public UsecaseUseCaseEntity createUseCase(String diagramName, String name,
                                       String description, int x, int y) {
        AbstractDiagramServiceHelper.requireNonEmptyName(name);
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        if (USE_CASE_OPS.findByName(d, name) != null) {
            throw new DuplicateException("DUPLICATE_USECASE",
                    "UseCase '" + name + "' already exists on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("CreateUseCase:" + name)) {
            Object useCase = USE_CASE_OPS.build(d, name, description);
            USE_CASE_OPS.setPosition(d, useCase, x, y);
            return new UsecaseUseCaseEntity(uuidOf(useCase), name,
                    description, diagramUuidOf(d), x, y);
        }
    }

    public List<UsecaseUseCaseEntity> listUseCases(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        List<UsecaseUseCaseEntity> out = new ArrayList<UsecaseUseCaseEntity>();
        Facade facade = Model.getFacade();
        String dUuid = diagramUuidOf(d);
        for (Object node : d.getGraphModel().getNodes()) {
            if (facade.isAUseCase(node)) {
                String name = facade.getName(node);
                String desc = readDescription(node);
                out.add(new UsecaseUseCaseEntity(uuidOf(node), name, desc,
                        dUuid, xOf(d, node), yOf(d, node)));
            }
        }
        return out;
    }

    public UsecaseUseCaseEntity getUseCaseByName(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByName(d, name);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        return new UsecaseUseCaseEntity(uuidOf(useCase), name,
                readDescription(useCase), diagramUuidOf(d),
                xOf(d, useCase), yOf(d, useCase));
    }

    public UsecaseUseCaseEntity findUseCaseByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByUuid(d, uuid);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Facade facade = Model.getFacade();
        return new UsecaseUseCaseEntity(uuidOf(useCase), facade.getName(useCase),
                readDescription(useCase), diagramUuidOf(d),
                xOf(d, useCase), yOf(d, useCase));
    }

    public void deleteUseCaseByName(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByName(d, name);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteUseCase:" + name)) {
            USE_CASE_OPS.delete(d, useCase);
        }
    }

    public void deleteUseCaseByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByUuid(d, uuid);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteUseCase:uuid=" + uuid)) {
            USE_CASE_OPS.delete(d, useCase);
        }
    }

    public UsecaseUseCaseEntity setUseCasePosition(String diagramName, String name,
                                            int x, int y) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByName(d, name);
        if (useCase == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + name + "' not found");
        }
        try (UndoScope s = UndoScope.open("MoveUseCase:" + name)) {
            USE_CASE_OPS.setPosition(d, useCase, x, y);
        }
        return new UsecaseUseCaseEntity(uuidOf(useCase), name,
                readDescription(useCase), diagramUuidOf(d), x, y);
    }

    // ---- Relationships ----

    public UsecaseAssociationEntity createAssociation(String diagramName,
                                               String actorName,
                                               String useCaseName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object actor = ACTOR_OPS.findByName(d, actorName);
        if (actor == null) {
            throw new NotFoundException("ACTOR_NOT_FOUND",
                    "Actor '" + actorName + "' not found");
        }
        Object useCase = USE_CASE_OPS.findByName(d, useCaseName);
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
            MutableGraphModel gm =
                    (MutableGraphModel) d.getGraphModel();
            if (gm != null && !gm.getEdges().contains(assoc)) {
                gm.addEdge(assoc);
            }
            return new UsecaseAssociationEntity(
                    uuidOf(assoc), actorName + "|" + useCaseName,
                    uuidOf(actor), actorName,
                    uuidOf(useCase), useCaseName,
                    diagramUuidOf(d));
        }
    }

    public List<UsecaseAssociationEntity> listAssociations(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        List<UsecaseAssociationEntity> out = new ArrayList<UsecaseAssociationEntity>();
        Facade facade = Model.getFacade();
        String dUuid = diagramUuidOf(d);
        for (Object assoc : findAllAssociationsIn(d)) {
            Object[] pair = actorUseCasePair(facade, assoc);
            if (pair == null) {
                continue;
            }
            String actorName = facade.getName(pair[0]);
            String ucName = facade.getName(pair[1]);
            if (actorName == null || ucName == null) {
                continue;
            }
            out.add(new UsecaseAssociationEntity(
                    uuidOf(assoc), actorName + "|" + ucName,
                    uuidOf(pair[0]), actorName,
                    uuidOf(pair[1]), ucName,
                    dUuid));
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
        Object actor = ACTOR_OPS.findByName(d, actorName);
        Object useCase = USE_CASE_OPS.findByName(d, ucName);
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

    public UsecaseIncludeEntity createInclude(String diagramName,
                                       String baseName,
                                       String inclusionName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object base = USE_CASE_OPS.findByName(d, baseName);
        if (base == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + baseName + "' not found");
        }
        Object inclusion = USE_CASE_OPS.findByName(d, inclusionName);
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
            MutableGraphModel gm =
                    (MutableGraphModel) d.getGraphModel();
            if (gm != null && !gm.getEdges().contains(inc)) {
                gm.addEdge(inc);
            }
            return new UsecaseIncludeEntity(
                    uuidOf(inc), baseName + "|" + inclusionName,
                    uuidOf(base), baseName,
                    uuidOf(inclusion), inclusionName,
                    diagramUuidOf(d));
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
        Object base = USE_CASE_OPS.findByName(d, baseName);
        Object inclusion = USE_CASE_OPS.findByName(d, inclusionName);
        if (base == null || inclusion == null) {
            throw new NotFoundException("INCLUDE_NOT_FOUND",
                    "Include " + id + " not found");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteInclude:" + id)) {
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
        String cn = e.getClass().getSimpleName();
        return cn.contains("Include");
    }

    public UsecaseExtendEntity createExtend(String diagramName,
                                    String baseName,
                                    String extensionName,
                                    String extensionPoint) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object base = USE_CASE_OPS.findByName(d, baseName);
        if (base == null) {
            throw new NotFoundException("USECASE_NOT_FOUND",
                    "UseCase '" + baseName + "' not found");
        }
        Object extension = USE_CASE_OPS.findByName(d, extensionName);
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
            MutableGraphModel gm =
                    (MutableGraphModel) d.getGraphModel();
            if (gm != null && !gm.getEdges().contains(ext)) {
                gm.addEdge(ext);
            }
            return new UsecaseExtendEntity(
                    uuidOf(ext), baseName + "|" + extensionName,
                    uuidOf(base), baseName,
                    uuidOf(extension), extensionName,
                    extensionPoint, diagramUuidOf(d));
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
        Object base = USE_CASE_OPS.findByName(d, baseName);
        Object extension = USE_CASE_OPS.findByName(d, extensionName);
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

    // ---- Diagram lookup (entity) ----

    /**
     * Find the diagram by name and return it as a
     * {@code DiagramEntity}. Useful for clients that need the
     * diagram uuid before issuing child queries.
     */
    public org.argouml.ai.domain.entity.DiagramEntity findDiagramByName(
            String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        String nsUuid = "";
        if (d.getNamespace() != null) {
            try {
                nsUuid = Model.getFacade().getUUID(d.getNamespace());
            } catch (RuntimeException ignored) {
                // some diagrams live under raw MModelElements; ignore
            }
        }
        return new org.argouml.ai.domain.entity.UsecaseDiagramEntity(
                diagramUuidOf(d), d.getName(),
                nsUuid == null ? "" : nsUuid);
    }

    // ---- helpers ----

    private static String readDescription(Object useCase) {
        return UseCaseOperations.getDescription(useCase);
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.application.sequencediagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.argouml.ai.application.common.AbstractDiagramServiceHelper;
import org.argouml.ai.application.common.DiagramService;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.common.UndoScope;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.domain.common.DiagramOperations;
import org.argouml.ai.domain.common.ModelKind;
import org.argouml.ai.domain.entity.SequenceClassifierRoleEntity;
import org.argouml.ai.domain.entity.SequenceDiagramEntity;
import org.argouml.ai.domain.entity.SequenceLifelineEntity;
import org.argouml.ai.domain.entity.SequenceMessageEntity;
import org.argouml.ai.domain.sequencediagram.ClassifierRoleOperations;
import org.argouml.ai.domain.sequencediagram.LifelineOperations;
import org.argouml.ai.domain.sequencediagram.MessageOperations;
import org.argouml.ai.domain.sequencediagram.MethodOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Application-layer service facade for UML sequence diagrams.
 *
 * <p>All public methods return immutable {@link
 * org.argouml.ai.domain.entity.Identified} entity objects
 * (ClassifierRole → {@link SequenceClassifierRoleEntity},
 * Lifeline → {@link SequenceLifelineEntity}, Message →
 * {@link SequenceMessageEntity}, diagram → {@link
 * SequenceDiagramEntity}). Every entity carries an ArgoUML UUID
 * ({@code xmi.id}) so callers can disambiguate elements that share
 * a name within the diagram's namespace.</p>
 *
 * <p><b>MDR / UML 1.4 caveat (locked design decision, see
 * 2026-07-05-sequence-diagram-api-design §1.3).</b> In the MDR
 * backend a "Lifeline" is not a separate metaclass; it is a
 * {@code ClassifierRole} with {@code multiplicity(1,1)}. The
 * factory {@link
 * org.argouml.model.CollaborationsFactory#buildLifeline(Object)}
 * returns a {@code ClassifierRole} and sets its multiplicity.
 * Therefore {@code POST /roles} and {@code POST /lifelines} with
 * the same name return distinct uuids because each call creates a
 * fresh model element, but no automatic pairing is performed —
 * {@code /lifelines POST} accepts the {@code classifierRoleUuid}
 * input verbatim as the back-reference field.</p>
 *
 * <p>Lookup primitives: each kind has a {@code findByName} method
 * (used by the human-facing API endpoints) and a
 * {@code findByUuid} method (used by the by-uuid endpoints and
 * the move / delete paths that already hold an entity). Messages
 * only have {@code getMessageByUuid} / {@code deleteMessageByUuid}
 * because message names are not unique within a diagram.</p>
 *
 * <p><b>MDR base binding.</b> The MDR backend exposes
 * {@code CollaborationsHelper.addBase(role, classifier)} (and
 * {@code setBases(role, collection)}) for binding a
 * {@code ClassifierRole} to a {@code Classifier}. The singular
 * {@code setBase(role, classifier)} method is intentionally
 * restricted to {@code AssociationRole} / {@code AssociationEndRole}
 * and throws {@code IllegalArgumentException} for
 * {@code ClassifierRole}. {@link #createRole} uses
 * {@code addBase} for the binding path and reports the bound
 * classifier's uuid via {@code SequenceClassifierRoleEntity.baseUuid}.
 * (EUML backend: {@code addBase} is a TODO stub and silently
 * no-ops — {@code baseUuid} stays empty.)</p>
 *
 * <p>This service is the single integration point REST handlers
 * call. It validates inputs, opens an {@code UndoScope}, delegates
 * to the pure-function domain operations, and exposes small
 * immutable entities.</p>
 */
public final class SequenceDiagramService implements DiagramService {

    private static final ClassifierRoleOperations ROLE_OPS =
            new ClassifierRoleOperations();
    private static final LifelineOperations LIFELINE_OPS =
            new LifelineOperations();
    private static final MessageOperations MSG_OPS = new MessageOperations();
    private static final MethodOperations METHOD_OPS = new MethodOperations();

    /**
     * Per-diagram set of uuids of elements created via
     * {@link #createLifeline(String, String, String)}. In the MDR
     * backend there is no structural marker distinguishing a
     * /roles-created ClassifierRole from a /lifelines-created one
     * (both are {@code ClassifierRole} with multiplicity(1,1)).
     * This set is the only authoritative discriminator — every
     * /lifelines-list/get/delete path filters graph-model results
     * through it.
     *
     * <p><b>Not persistent.</b> After a JVM restart the set is
     * empty. /lifelines POST still works (adds to set), but a
     * previously-created lifeline will not appear in list/get until
     * it is re-created. Acceptable per design doc §12.</p>
     *
     * <p><b>Non-undoable.</b> The Set has no ArgoUML model
     * representation and is intentionally NOT under the surrounding
     * {@link UndoScope} on delete (see
     * {@link #unregisterLifelineUuid}). The trade-off: a deleted
     * lifeline stays out of the Set even if the user undoes the
     * delete via {@code UndoManager}. Acceptable because
     * /lifelines is a derived view, not a first-class identity.</p>
     */
    private static final Map<String, Set<String>> LIFELINE_UUIDS_BY_DIAGRAM =
            new ConcurrentHashMap<String, Set<String>>();

    /**
     * Per-diagram set of uuids of elements created via
     * {@link #createRole(String, String, String, int, int)}. Symmetric
     * to {@link #LIFELINE_UUIDS_BY_DIAGRAM}: every /roles-list/get/delete
     * path filters graph-model results through this set so a
     * /lifelines-created element does not appear under /roles.
     *
     * <p><b>Non-undoable</b> for the same reason as
     * {@link #LIFELINE_UUIDS_BY_DIAGRAM} — see
     * {@link #unregisterRoleUuid}.</p>
     */
    private static final Map<String, Set<String>> ROLE_UUIDS_BY_DIAGRAM =
            new ConcurrentHashMap<String, Set<String>>();

    /** y-coordinate of the first message (server-computed). */
    private static final int BASE_Y = 80;
    /** Vertical spacing between sequential messages on the same
     *  diagram (server-computed). */
    private static final int DELTA_Y = 50;
    /** Default x-coordinate of the first ClassifierRole on a
     *  sequence diagram, used when the caller does not pass an
     *  explicit x/y. Mirrors ArgoUML's own UI default of 100px from
     *  the canvas origin (see {@code ModePlaceClassifierRole}). */
    private static final int ROLE_BASE_X = 100;
    /** Default horizontal spacing between ClassifierRoles on a
     *  sequence diagram. Mirrors the typical ArgoUML canvas
     *  spacing — 250px leaves room for a 150-wide head + 100px
     *  gap. */
    private static final int ROLE_SPACING_X = 250;
    /** Default y-coordinate of the first ClassifierRole (head
     *  top). The lifeline body extends below. A small value
     *  (20px) keeps the diagram compact — ArgoUML's own
     *  {@code ModePlaceClassifierRole} places new roles at
     *  roughly the same offset from the canvas top. */
    private static final int ROLE_DEFAULT_Y = 20;
    /** Default y-coordinate of a lifeline (head bottom + small
     *  gap). {@code ROLE_DEFAULT_Y + FigHead.DEFAULT_HEIGHT + 30}
     *  gives a comfortable 30px gap between the role head and
     *  the lifeline head. */
    private static final int LIFELINE_DEFAULT_Y = 100;

    @Override
    public ModelKind kind() {
        return ModelKind.SEQUENCE;
    }

    // ---- Diagram lookup helpers ----

    private ArgoDiagram requireDiagram(String name) {
        return AbstractDiagramServiceHelper.requireDiagram(name);
    }

    private void requireSequenceDiagram(ArgoDiagram d) {
        if (!(d instanceof org.argouml.sequence2.diagram.UMLSequenceDiagram)) {
            throw new InvalidArgumentException("UNSUPPORTED_DIAGRAM_TYPE",
                    "Sequence API only operates on sequence diagrams; '"
                    + d.getName() + "' is "
                    + d.getClass().getSimpleName());
        }
    }

    private static String diagramUuidOf(ArgoDiagram d) {
        if (d == null) {
            return "";
        }
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

    private static String uuidOf(Object node) {
        if (node == null) {
            return "";
        }
        String u = Model.getFacade().getUUID(node);
        return u == null ? "" : u;
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

    // ---- Diagram (entity) ----

    /**
     * Find a sequence diagram by display name and return it as a
     * {@link SequenceDiagramEntity}.
     */
    public SequenceDiagramEntity findDiagramByName(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        String nsUuid = "";
        if (d.getNamespace() != null) {
            try {
                nsUuid = Model.getFacade().getUUID(d.getNamespace());
            } catch (RuntimeException ignored) {
                // diagram namespace may live under a raw MModelElement;
                // ignore.
            }
        }
        return new SequenceDiagramEntity(diagramUuidOf(d), d.getName(),
                nsUuid == null ? "" : nsUuid);
    }

    /**
     * List every sequence diagram in the current project.
     */
    public List<SequenceDiagramEntity> listDiagrams() {
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p == null) {
            return new ArrayList<SequenceDiagramEntity>();
        }
        List<SequenceDiagramEntity> out = new ArrayList<SequenceDiagramEntity>();
        for (Object o : p.getDiagramList()) {
            ArgoDiagram d = (ArgoDiagram) o;
            if (!(d instanceof org.argouml.sequence2.diagram.UMLSequenceDiagram)) {
                continue;
            }
            String nsUuid = "";
            if (d.getNamespace() != null) {
                try {
                    nsUuid = Model.getFacade().getUUID(d.getNamespace());
                } catch (RuntimeException ignored) {
                }
            }
            out.add(new SequenceDiagramEntity(diagramUuidOf(d), d.getName(),
                    nsUuid == null ? "" : nsUuid));
        }
        // sort by name for determinism (matches DiagramOperations.list())
        java.util.Collections.sort(out, (a, b) -> {
            String na = a.name() == null ? "" : a.name();
            String nb = b.name() == null ? "" : b.name();
            return na.compareTo(nb);
        });
        return out;
    }

    // ---- ClassifierRole ----

    public SequenceClassifierRoleEntity createRole(String diagramName,
                                                   String name,
                                                   String baseUuid,
                                                   int x, int y) {
        AbstractDiagramServiceHelper.requireNonEmptyName(name);
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        if (ROLE_OPS.findByName(d, name) != null) {
            throw new DuplicateException("DUPLICATE_ROLE",
                    "ClassifierRole '" + name + "' already exists on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("CreateRole:" + name)) {
            Object role = ROLE_OPS.build(d, name);
            // Auto-layout: when the caller does not specify x and y
            // (both 0), place this role to the right of the existing
            // roles at the same y. Mirrors ArgoUML's own behaviour in
            // UMLSequenceDiagram.makeNewFigCR which Y-snaps new
            // ClassifierRoles to the first existing one. This
            // eliminates the "every role at (0, 0)" failure mode
            // where message arrows have zero length and are
            // invisible on the canvas.
            int effectiveX = x;
            int effectiveY = y;
            if (x == 0 && y == 0) {
                int roleCount = countRoles(d);
                effectiveX = ROLE_BASE_X + roleCount * ROLE_SPACING_X;
                effectiveY = ROLE_DEFAULT_Y;
            }
            try {
                ROLE_OPS.setPosition(d, role, effectiveX, effectiveY);
            } catch (RuntimeException ignored) {
                // figs may not be realized in headless mode;
                // best-effort.
            }
            // Bind this role to a Classifier (MClass) via
            // CollaborationsHelper.addBase — this is the canonical
            // ClassifierRole → Classifier binding path (the
            // singular setBase(...) method is restricted to
            // AssociationRole/AssociationEndRole and throws
            // IllegalArgumentException for ClassifierRole in the
            // MDR backend). Two paths:
            //   (a) explicit baseUuid — caller provided the
            //       Classifier uuid to bind to;
            //   (b) auto-bind — findOrCreateClassForRole resolves
            //       a same-named MClass in the diagram's
            //       namespace and binds to it; idempotent.
            // addBase also repopulates the ClassifierRole's
            // availableContents/availableFeature so the sequence
            // notation renderer (":ClassName") continues to work.
            Object resolvedClass = null;
            if (baseUuid != null && !baseUuid.isEmpty()) {
                Object explicit = findBaseClassifierByUuid(baseUuid);
                if (explicit != null) {
                    try {
                        Model.getCollaborationsHelper()
                                .addBase(role, explicit);
                        resolvedClass = explicit;
                    } catch (RuntimeException ignored) {
                        // wellformedness rule may reject; fall
                        // through to auto-bind below.
                    }
                }
            }
            if (resolvedClass == null) {
                resolvedClass = findOrCreateClassForRole(
                        role, diagramName);
                if (resolvedClass != null) {
                    try {
                        Model.getCollaborationsHelper()
                                .addBase(role, resolvedClass);
                    } catch (RuntimeException ignored) {
                        // best-effort: auto-bind is opportunistic.
                        resolvedClass = null;
                    }
                }
            }
            String finalBaseUuid = (resolvedClass != null)
                    ? uuidOf(resolvedClass) : "";
            String roleUuid = uuidOf(role);
            registerRoleUuid(diagramName, roleUuid);
            return new SequenceClassifierRoleEntity(
                    roleUuid, name, finalBaseUuid,
                    "", diagramUuidOf(d), effectiveX, effectiveY);
        }
    }

    public List<SequenceClassifierRoleEntity> listRoles(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        List<SequenceClassifierRoleEntity> out =
                new ArrayList<SequenceClassifierRoleEntity>();
        Facade facade = Model.getFacade();
        String dUuid = diagramUuidOf(d);
        // Filter by the /roles-created uuid Set so /lifelines-created
        // elements do not leak into the /roles list (MDR has no
        // structural distinction between the two).
        for (Object node : d.getGraphModel().getNodes()) {
            if (facade.isAClassifierRole(node)
                    && isRoleUuid(diagramName, uuidOf(node))) {
                String nodeName = facade.getName(node);
                out.add(new SequenceClassifierRoleEntity(
                        uuidOf(node), nodeName, baseUuidOf(node), "",
                        dUuid, xOf(d, node), yOf(d, node)));
            }
        }
        return out;
    }

    /**
     * Read the bound ClassifierRole → Classifier base uuid from the
     * model, if any. Returns "" (matching
     * {@link SequenceClassifierRoleEntity#baseUuid()}) when the
     * role has no base. Defensive against the MDR
     * {@code ClassifierRole.getBase()} returning a Collection (the
     * accessor is multi-valued in UML 1.4).
     */
    private static String baseUuidOf(Object role) {
        if (role == null) {
            return "";
        }
        try {
            java.util.Collection bases = Model.getFacade().getBases(role);
            if (bases != null && !bases.isEmpty()) {
                return uuidOf(bases.iterator().next());
            }
        } catch (RuntimeException ignored) {
            // best-effort probe.
        }
        return "";
    }

    public SequenceClassifierRoleEntity getRoleByName(String diagramName,
                                                      String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object role = null;
        for (Object node : d.getGraphModel().getNodes()) {
            if (Model.getFacade().isAClassifierRole(node)
                    && name.equals(Model.getFacade().getName(node))
                    && isRoleUuid(diagramName, uuidOf(node))) {
                role = node;
                break;
            }
        }
        if (role == null) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        return new SequenceClassifierRoleEntity(
                uuidOf(role), name, baseUuidOf(role), "",
                diagramUuidOf(d), xOf(d, role), yOf(d, role));
    }

    public SequenceClassifierRoleEntity findRoleByUuid(String diagramName,
                                                       String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        if (!isRoleUuid(diagramName, uuid)) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Object role = ROLE_OPS.findByUuid(d, uuid);
        if (role == null) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Facade facade = Model.getFacade();
        return new SequenceClassifierRoleEntity(
                uuidOf(role), facade.getName(role), baseUuidOf(role), "",
                diagramUuidOf(d), xOf(d, role), yOf(d, role));
    }

    public void deleteRoleByName(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object role = null;
        for (Object node : d.getGraphModel().getNodes()) {
            if (Model.getFacade().isAClassifierRole(node)
                    && name.equals(Model.getFacade().getName(node))
                    && isRoleUuid(diagramName, uuidOf(node))) {
                role = node;
                break;
            }
        }
        if (role == null) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        String roleUuid = uuidOf(role);
        try (UndoScope s = UndoScope.open("DeleteRole:" + name)) {
            cascadeDeleteRoleMessages(d, role);
            ROLE_OPS.delete(d, role);
        }
        unregisterRoleUuid(diagramName, roleUuid);
    }

    public void deleteRoleByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        if (!isRoleUuid(diagramName, uuid)) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Object role = ROLE_OPS.findByUuid(d, uuid);
        if (role == null) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteRole:uuid=" + uuid)) {
            cascadeDeleteRoleMessages(d, role);
            ROLE_OPS.delete(d, role);
        }
        unregisterRoleUuid(diagramName, uuid);
    }

    public SequenceClassifierRoleEntity setRolePosition(String diagramName,
                                                        String name,
                                                        int x, int y) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object role = null;
        for (Object node : d.getGraphModel().getNodes()) {
            if (Model.getFacade().isAClassifierRole(node)
                    && name.equals(Model.getFacade().getName(node))
                    && isRoleUuid(diagramName, uuidOf(node))) {
                role = node;
                break;
            }
        }
        if (role == null) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("MoveRole:" + name)) {
            try {
                ROLE_OPS.setPosition(d, role, x, y);
            } catch (RuntimeException ignored) {
                // best-effort.
            }
        }
        return new SequenceClassifierRoleEntity(
                uuidOf(role), name, baseUuidOf(role), "",
                diagramUuidOf(d), x, y);
    }

    // ---- Lifeline ----

    public SequenceLifelineEntity createLifeline(String diagramName,
                                                 String classifierRoleUuid,
                                                 String name) {
        if (classifierRoleUuid == null || classifierRoleUuid.isEmpty()) {
            throw new InvalidArgumentException("MISSING_CLASSIFIER_ROLE",
                    "Lifeline creation requires a classifierRoleUuid");
        }
        AbstractDiagramServiceHelper.requireNonEmptyName(name);
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        // Disambiguate via the /lifelines-created uuid Set: only
        // elements registered via createLifeline count as lifelines.
        // (In the MDR backend every ClassifierRole is also a Lifeline,
        // so a name-only check would match /roles-created elements.)
        for (Object node : LIFELINE_OPS.findAll(d)) {
            if (name.equals(Model.getFacade().getName(node))
                    && isLifelineUuid(diagramName, uuidOf(node))) {
                throw new DuplicateException("DUPLICATE_LIFELINE",
                        "Lifeline '" + name + "' already exists on diagram '"
                        + diagramName + "'");
            }
        }
        if (ROLE_OPS.findByUuid(d, classifierRoleUuid) == null) {
            throw new NotFoundException("ROLE_NOT_FOUND",
                    "ClassifierRole uuid '" + classifierRoleUuid
                    + "' not found on diagram '" + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("CreateLifeline:" + name)) {
            Object lifeline = LIFELINE_OPS.build(d, name);
            // Inherit x from the role so the lifeline is vertically
            // aligned with its role head. Default y to a non-zero
            // value so the lifeline body is visible below the role
            // head. Without the x inheritance, two lifelines would
            // both sit at x=0 and the message arrow between them
            // would have zero length (invisible on the canvas).
            int lifelineX = 0;
            try {
                Object role = ROLE_OPS.findByUuid(d, classifierRoleUuid);
                if (role != null) {
                    org.tigris.gef.presentation.Fig roleFig =
                            d.presentationFor(role);
                    if (roleFig != null) {
                        lifelineX = roleFig.getX();
                    }
                }
            } catch (RuntimeException ignored) {
                // best-effort probe; fall back to x=0.
            }
            try {
                LIFELINE_OPS.setPosition(d, lifeline, lifelineX,
                        LIFELINE_DEFAULT_Y);
            } catch (RuntimeException ignored) {
                // best-effort.
            }
            String lifelineUuid = uuidOf(lifeline);
            registerLifelineUuid(diagramName, lifelineUuid);
            return new SequenceLifelineEntity(
                    lifelineUuid, name, classifierRoleUuid, false,
                    diagramUuidOf(d),
                    xOf(d, lifeline), yOf(d, lifeline));
        }
    }

    public List<SequenceLifelineEntity> listLifelines(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        List<SequenceLifelineEntity> out =
                new ArrayList<SequenceLifelineEntity>();
        String dUuid = diagramUuidOf(d);
        // Filter by /lifelines-created uuid Set (MDR returns all
        // ClassifierRoles as "lifelines"; we want only the registered
        // subset).
        for (Object node : LIFELINE_OPS.findAll(d)) {
            String nodeUuid = uuidOf(node);
            if (!isLifelineUuid(diagramName, nodeUuid)) {
                continue;
            }
            String nodeName = Model.getFacade().getName(node);
            out.add(new SequenceLifelineEntity(
                    nodeUuid, nodeName, nodeUuid, false,
                    dUuid, xOf(d, node), yOf(d, node)));
        }
        return out;
    }

    public SequenceLifelineEntity getLifelineByName(String diagramName,
                                                    String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object lifeline = null;
        for (Object node : LIFELINE_OPS.findAll(d)) {
            if (name.equals(Model.getFacade().getName(node))
                    && isLifelineUuid(diagramName, uuidOf(node))) {
                lifeline = node;
                break;
            }
        }
        if (lifeline == null) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "Lifeline '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        String nodeUuid = uuidOf(lifeline);
        return new SequenceLifelineEntity(
                nodeUuid, name, nodeUuid, false,
                diagramUuidOf(d), xOf(d, lifeline), yOf(d, lifeline));
    }

    public SequenceLifelineEntity findLifelineByUuid(String diagramName,
                                                     String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        if (!isLifelineUuid(diagramName, uuid)) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "Lifeline uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Object lifeline = LIFELINE_OPS.findByUuid(d, uuid);
        if (lifeline == null) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "Lifeline uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Facade facade = Model.getFacade();
        return new SequenceLifelineEntity(
                uuidOf(lifeline), facade.getName(lifeline), uuid, false,
                diagramUuidOf(d), xOf(d, lifeline), yOf(d, lifeline));
    }

    public void deleteLifelineByName(String diagramName, String name) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object lifeline = null;
        for (Object node : LIFELINE_OPS.findAll(d)) {
            if (name.equals(Model.getFacade().getName(node))
                    && isLifelineUuid(diagramName, uuidOf(node))) {
                lifeline = node;
                break;
            }
        }
        if (lifeline == null) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "Lifeline '" + name + "' not found on diagram '"
                    + diagramName + "'");
        }
        String lifelineUuid = uuidOf(lifeline);
        try (UndoScope s = UndoScope.open("DeleteLifeline:" + name)) {
            cascadeDeleteLifelineMessages(d, lifeline);
            LIFELINE_OPS.delete(d, lifeline);
        }
        unregisterLifelineUuid(diagramName, lifelineUuid);
    }

    public void deleteLifelineByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        if (!isLifelineUuid(diagramName, uuid)) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "Lifeline uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Object lifeline = LIFELINE_OPS.findByUuid(d, uuid);
        if (lifeline == null) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "Lifeline uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open("DeleteLifeline:uuid=" + uuid)) {
            cascadeDeleteLifelineMessages(d, lifeline);
            LIFELINE_OPS.delete(d, lifeline);
        }
        unregisterLifelineUuid(diagramName, uuid);
    }

    // ---- Message ----

    public SequenceMessageEntity createMessage(String diagramName,
                                               String name,
                                               String actionSignature,
                                               String messageType,
                                               boolean activation,
                                               String fromUuid,
                                               String toUuid) {
        AbstractDiagramServiceHelper.requireNonEmptyName(name);
        AbstractDiagramServiceHelper.requireNonEmptyName(messageType);
        AbstractDiagramServiceHelper.requireNonEmptyName(fromUuid);
        AbstractDiagramServiceHelper.requireNonEmptyName(toUuid);
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object from = LIFELINE_OPS.findByUuid(d, fromUuid);
        if (from == null) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "From lifeline uuid '" + fromUuid
                    + "' not found on diagram '" + diagramName + "'");
        }
        Object to = LIFELINE_OPS.findByUuid(d, toUuid);
        if (to == null) {
            throw new NotFoundException("LIFELINE_NOT_FOUND",
                    "To lifeline uuid '" + toUuid
                    + "' not found on diagram '" + diagramName + "'");
        }
        int seq = MSG_OPS.findAll(d).size() + 1;
        int x = xOf(d, from);
        int y = BASE_Y + seq * DELTA_Y;
        try (UndoScope s = UndoScope.open(
                "CreateMsg:" + name + "#" + seq)) {
            Object msg = MSG_OPS.build(d, from, to, name,
                    actionSignature, messageType, activation);
            // Method extraction: only for syncCall messages with a
            // non-empty actionSignature. Best-effort — a failure here
            // does not roll back the message.
            String methodUuid = "";
            if ("syncCall".equals(messageType)
                    && actionSignature != null
                    && !actionSignature.isEmpty()) {
                try {
                    Object toRole = LIFELINE_OPS.findByUuid(d, toUuid);
                    if (toRole != null) {
                        Object targetClass = findOrCreateClassForRole(
                                toRole, diagramName);
                        if (targetClass != null) {
                            Object op = METHOD_OPS.addMethod(
                                    targetClass, actionSignature);
                            if (op != null) {
                                methodUuid = uuidOf(op);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    // best-effort: message already created.
                    java.util.logging.Logger.getLogger(
                            SequenceDiagramService.class.getName())
                            .log(java.util.logging.Level.WARNING,
                                    "Method extraction failed for '"
                                    + name + "' on '" + diagramName
                                    + "': " + e.getMessage(), e);
                }
            }
            return new SequenceMessageEntity(
                    uuidOf(msg), name,
                    actionSignature == null ? "" : actionSignature,
                    messageType, seq, activation,
                    fromUuid, toUuid,
                    diagramUuidOf(d), x, y, methodUuid);
        }
    }

    public List<SequenceMessageEntity> listMessages(String diagramName) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        List<SequenceMessageEntity> out =
                new ArrayList<SequenceMessageEntity>();
        Facade facade = Model.getFacade();
        String dUuid = diagramUuidOf(d);
        int seq = 0;
        for (Object edge : MSG_OPS.findAll(d)) {
            seq++;
            String msgName = facade.getName(edge);
            Object sender = facade.getSender(edge);
            Object receiver = facade.getReceiver(edge);
            out.add(new SequenceMessageEntity(
                    uuidOf(edge), msgName, "", "", seq, false,
                    uuidOf(sender), uuidOf(receiver),
                    dUuid, xOf(d, edge), yOf(d, edge), ""));
        }
        // MSG_OPS.findAll iterates graph-model edges in insertion
        // order, which is monotonic for a freshly-created diagram.
        // For lists built across operations we sort by sequenceNumber
        // to be robust to mid-stream deletes.
        java.util.Collections.sort(out, (a, b) ->
                Integer.compare(a.sequenceNumber(), b.sequenceNumber()));
        return out;
    }

    public SequenceMessageEntity getMessageByUuid(String diagramName,
                                                  String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object msg = MSG_OPS.findByUuid(d, uuid);
        if (msg == null) {
            throw new NotFoundException("MESSAGE_NOT_FOUND",
                    "Message uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        Facade facade = Model.getFacade();
        Object sender = facade.getSender(msg);
        Object receiver = facade.getReceiver(msg);
        // sequenceNumber on lookup is unstable; report the 1-based
        // position in the current graph-edge ordering.
        int seq = 0;
        for (Object edge : MSG_OPS.findAll(d)) {
            seq++;
            if (edge == msg) {
                break;
            }
        }
        return new SequenceMessageEntity(
                uuidOf(msg), facade.getName(msg), "", "",
                seq, false,
                uuidOf(sender), uuidOf(receiver),
                diagramUuidOf(d), xOf(d, msg), yOf(d, msg), "");
    }

    public void deleteMessageByUuid(String diagramName, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireSequenceDiagram(d);
        Object msg = MSG_OPS.findByUuid(d, uuid);
        if (msg == null) {
            throw new NotFoundException("MESSAGE_NOT_FOUND",
                    "Message uuid '" + uuid + "' not found on diagram '"
                    + diagramName + "'");
        }
        try (UndoScope s = UndoScope.open(
                "DeleteMsg:uuid=" + uuid)) {
            MSG_OPS.delete(d, msg);
        }
    }

    // ---- Class-binding helpers (method-extraction support) ----

    /**
     * Return the {@code MClassifier} to which a ClassifierRole
     * would be bound, looking up an existing one by role name or
     * auto-creating one. The returned class is the canonical target
     * for method extraction, and is also used by
     * {@link #createRole} as the auto-bind target via
     * {@code CollaborationsHelper.addBase}.
     *
     * <p>This is the "reference class" path that supports method
     * extraction: messages on the role (or on its paired lifeline)
     * can be wired to a method on the returned class via
     * {@link MethodOperations#addMethod}.</p>
     *
     * <p><b>MDR quirk.</b> {@link Facade#getBase(Object)} on a
     * {@code ClassifierRole} returns a {@code Collection<Classifier>}
     * (MDR's {@code ClassifierRole.getBase()} is multi-valued), not
     * a single {@code MClassifier} — passing it back to
     * {@code getUUID} throws
     * {@code IllegalArgumentException: Unrecognized object}. We
     * therefore use {@link Facade#getBases(Object)} and pick the
     * first element when the role is already bound. (Path 1 below.)</p>
     *
     * <p>Idempotency: if a class with the same name already exists
     * in the diagram's namespace, it is reused (lookup by name)
     * rather than duplicated.</p>
     *
     * <p><b>Binding semantics.</b> The returned class is what the
     * role is (or will be) bound to. The actual
     * {@code addBase(role, returnedClass)} call happens in
     * {@link #createRole} (the binding is applied whether the
     * caller passed an explicit {@code baseUuid} or not). Method
     * extraction on subsequent {@code /messages} calls works
     * because the helper looks up the class by name again.</p>
     *
     * @param role        the ClassifierRole to find or create a
     *                    class for
     * @param diagramName the diagram name (used to locate the
     *                    namespace)
     * @return the MClassifier (existing or newly created), or null
     *         on failure
     */
    private static Object findOrCreateClassForRole(Object role,
                                                    String diagramName) {
        if (role == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        // Path 1: if the role has a base, return it. MDR's
        // ClassifierRole.getBase() returns a Collection<Classifier>
        // (multi-valued) — pick the first element. (EUML has a
        // single-valued base; getBases() still returns a
        // singleton Collection on EUML.)
        try {
            java.util.Collection bases = facade.getBases(role);
            if (bases != null && !bases.isEmpty()) {
                return bases.iterator().next();
            }
        } catch (RuntimeException ignored) {
            // best-effort probe; fall through to lookup.
        }
        // (Don't call facade.setBase(role, ...) here — it throws
        // IllegalArgumentException for ClassifierRole in MDR.)
        String roleName = facade.getName(role);
        // Path 2: look up an MClass with the same name in the
        // diagram's namespace (covers the case where the SD was
        // already bound to a class). The class is NOT bound to the
        // role here — the binding call happens in
        // {@link #createRole}.
        Object ns = getDiagramNamespaceByName(diagramName);
        Object existing = findClassByName(roleName, ns);
        if (existing != null) {
            return existing;
        }
        // Path 2b: cross-diagram auto-bind. Look in the project's
        // user-defined model root. This handles the common case
        // where the MClass lives in a class diagram (`Model`
        // package) and the role is being added to a sequence
        // diagram (whose own namespace is a Collaboration,
        // separate from the class diagram). Without this step
        // every role would auto-create a duplicate MClass under
        // its own collaboration, and baseUuid would point to a
        // copy that has no relationship to the user's class
        // diagram. Mirrors ArgoUML's own behaviour when a user
        // drops a Class from the navigator onto a sequence
        // diagram (UMLSequenceDiagram.makeNewFigCR + drop).
        existing = findClassByNameInProjectRoot(roleName);
        if (existing != null) {
            return existing;
        }
        // Path 3: auto-create a new MClass under the SD's
        // namespace (last resort). The class is local to this
        // SD's collaboration and is not visible on any class
        // diagram.
        try {
            return Model.getCoreFactory().buildClass(roleName, ns);
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(
                    SequenceDiagramService.class.getName())
                    .log(java.util.logging.Level.WARNING,
                            "Could not auto-create MClass " + roleName
                            + " for role in " + diagramName + ": "
                            + e.getMessage());
            return null;
        }
    }

    /**
     * Search the project's user-defined model root for an
     * {@code MClass} (or MInterface) with the given name. This is
     * the canonical home for classes created via the class-diagram
     * service; a sequence-diagram role with a matching name should
     * bind to it rather than auto-creating a duplicate. Returns
     * null when no match is found or when the project has no
     * user-defined roots.
     */
    private static Object findClassByNameInProjectRoot(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            Project p = ProjectManager.getManager().getCurrentProject();
            if (p == null) {
                return null;
            }
            for (Object root : p.getUserDefinedModelList()) {
                Object hit = findClassByName(name, root);
                if (hit != null) {
                    return hit;
                }
            }
        } catch (RuntimeException ignored) {
            // best-effort probe.
        }
        return null;
    }

    /** Return the namespace model element for the named diagram, or
     *  null if the diagram or its namespace cannot be resolved. */
    private static Object getDiagramNamespaceByName(String diagramName) {
        try {
            ArgoDiagram d = DiagramLocator.byName(diagramName);
            return d == null ? null : d.getNamespace();
        } catch (RuntimeException ignored) {
            // diagram not found, etc. — best-effort.
            return null;
        }
    }

    /** Look for an MClass with {@code name} as a direct owned
     *  element of {@code ns}. Recurses one level into nested
     *  namespaces — sufficient for the MVP. */
    private static Object findClassByName(String name, Object ns) {
        if (ns == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        try {
            java.util.Collection owned = facade.getOwnedElements(ns);
            if (owned != null) {
                for (Object e : owned) {
                    if (facade.isAClass(e)
                            && name.equals(facade.getName(e))) {
                        return e;
                    }
                }
                // Recurse one level for nested packages.
                if (facade.isANamespace(ns)) {
                    for (Object e : owned) {
                        if (facade.isANamespace(e)) {
                            Object hit = findClassByName(name, e);
                            if (hit != null) {
                                return hit;
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // best-effort traversal.
        }
        return null;
    }

    // ---- Cascade helpers ----

    /**
     * Count ClassifierRoles currently on the diagram, filtered to
     * /roles-created elements only (consistent with the rest of the
     * service). Used by {@link #createRole} to compute the
     * auto-layout x-coordinate. Returns 0 for a fresh diagram.
     */
    private static int countRoles(ArgoDiagram d) {
        if (d == null || d.getGraphModel() == null) {
            return 0;
        }
        int n = 0;
        for (Object node : d.getGraphModel().getNodes()) {
            if (Model.getFacade().isAClassifierRole(node)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Cascade-delete every message whose sender or receiver
     * references the given role's uuid. Per the locked design
     * decision we do NOT auto-pair Lifelines with Roles, so we
     * compare uuids directly against the role object.
     */
    private static void cascadeDeleteRoleMessages(ArgoDiagram d,
                                                  Object role) {
        if (d == null || role == null) {
            return;
        }
        String roleUuid = uuidOf(role);
        for (Object msg : MSG_OPS.findAll(d)) {
            Object sender = Model.getFacade().getSender(msg);
            Object receiver = Model.getFacade().getReceiver(msg);
            String senderUuid = uuidOf(sender);
            String receiverUuid = uuidOf(receiver);
            if (roleUuid.equals(senderUuid)
                    || roleUuid.equals(receiverUuid)) {
                MSG_OPS.delete(d, msg);
            }
        }
    }

    /**
     * Cascade-delete every message whose sender or receiver
     * references the given lifeline's uuid.
     */
    private static void cascadeDeleteLifelineMessages(ArgoDiagram d,
                                                       Object lifeline) {
        if (d == null || lifeline == null) {
            return;
        }
        String lifeUuid = uuidOf(lifeline);
        for (Object msg : MSG_OPS.findAll(d)) {
            Object sender = Model.getFacade().getSender(msg);
            Object receiver = Model.getFacade().getReceiver(msg);
            String senderUuid = uuidOf(sender);
            String receiverUuid = uuidOf(receiver);
            if (lifeUuid.equals(senderUuid)
                    || lifeUuid.equals(receiverUuid)) {
                MSG_OPS.delete(d, msg);
            }
        }
    }

    /**
     * Walk the project's user-defined model and the diagram's
     * namespace (via {@link DiagramOperations}) for any Classifier
     * (MClass, MInterface) whose uuid matches. Used to resolve a
     * {@code baseUuid} argument on {@link #createRole} — best-effort
     * because ArgoUML namespaces are deep.
     */
    private static Object findBaseClassifierByUuid(String baseUuid) {
        if (baseUuid == null || baseUuid.isEmpty()) {
            return null;
        }
        Facade facade = Model.getFacade();
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p == null) {
            return null;
        }
        for (Object root : p.getUserDefinedModelList()) {
            Object hit = walkForClassifier(root, facade, baseUuid);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static Object walkForClassifier(Object ns, Facade facade,
                                            String uuid) {
        if (ns == null) {
            return null;
        }
        if (uuid.equals(facade.getUUID(ns))
                && facade.isAClassifier(ns)) {
            return ns;
        }
        java.util.Collection owned = facade.getOwnedElements(ns);
        if (owned == null) {
            return null;
        }
        for (Object e : owned) {
            Object hit = walkForClassifier(e, facade, uuid);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    // =================================================================
    // Lifeline bookkeeping — uuids of elements created via /lifelines
    //
    // NOTE: these per-diagram Sets are NON-UNDOABLE state (see
    // unregisterRoleUuid / unregisterLifelineUuid which are called
    // OUTSIDE the surrounding UndoScope on delete). If the model
    // delete is rolled back via the ArgoUML UndoManager the Set is
    // NOT restored — this is deliberate. The Set is a local
    // discriminator with no ArgoUML persistence, and the alternative
    // (snapshotting Set state in the UndoScope) would leak
    // implementation details through the SPI.
    // =================================================================

    /** Get-or-create the per-diagram set of /lifelines-created uuids.
     *  Returns a thread-safe Set backed by a ConcurrentHashMap so
     *  concurrent /lifelines POSTs across threads cannot corrupt it. */
    private static Set<String> lifelineSet(String diagramName) {
        Set<String> s = LIFELINE_UUIDS_BY_DIAGRAM.get(diagramName);
        if (s == null) {
            s = Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>());
            Set<String> prev = LIFELINE_UUIDS_BY_DIAGRAM.putIfAbsent(diagramName, s);
            if (prev != null) {
                s = prev;
            }
        }
        return s;
    }

    /** True iff {@code uuid} was registered via createLifeline. */
    private static boolean isLifelineUuid(String diagramName, String uuid) {
        if (uuid == null || uuid.isEmpty()) return false;
        Set<String> s = LIFELINE_UUIDS_BY_DIAGRAM.get(diagramName);
        return s != null && s.contains(uuid);
    }

    /** Register a uuid as a /lifelines-created element. */
    private static void registerLifelineUuid(String diagramName, String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        lifelineSet(diagramName).add(uuid);
    }

    /** Unregister a uuid from the /lifelines set (on delete).
     *  Called OUTSIDE the UndoScope — see field note above. */
    private static void unregisterLifelineUuid(String diagramName, String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        Set<String> s = LIFELINE_UUIDS_BY_DIAGRAM.get(diagramName);
        if (s != null) s.remove(uuid);
    }

    /** True iff {@code uuid} was registered via createRole. */
    private static boolean isRoleUuid(String diagramName, String uuid) {
        if (uuid == null || uuid.isEmpty()) return false;
        Set<String> s = ROLE_UUIDS_BY_DIAGRAM.get(diagramName);
        return s != null && s.contains(uuid);
    }

    /** Register a uuid as a /roles-created element. The Set is
     *  backed by a ConcurrentHashMap so concurrent POSTs across
     *  threads cannot corrupt it. */
    private static void registerRoleUuid(String diagramName, String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        Set<String> s = ROLE_UUIDS_BY_DIAGRAM.get(diagramName);
        if (s == null) {
            s = Collections.newSetFromMap(
                    new ConcurrentHashMap<String, Boolean>());
            Set<String> prev = ROLE_UUIDS_BY_DIAGRAM.putIfAbsent(diagramName, s);
            if (prev != null) s = prev;
        }
        s.add(uuid);
    }

    /** Unregister a uuid from the /roles set (on delete).
     *  Called OUTSIDE the UndoScope — see field note above. */
    private static void unregisterRoleUuid(String diagramName, String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        Set<String> s = ROLE_UUIDS_BY_DIAGRAM.get(diagramName);
        if (s != null) s.remove(uuid);
    }
}

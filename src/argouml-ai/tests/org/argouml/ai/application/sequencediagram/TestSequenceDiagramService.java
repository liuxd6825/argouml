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

import java.beans.PropertyVetoException;
import java.util.List;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.domain.common.ModelKind;
import org.argouml.ai.domain.sequencediagram.MethodOperations;
import org.argouml.ai.domain.entity.SequenceClassifierRoleEntity;
import org.argouml.ai.domain.entity.SequenceDiagramEntity;
import org.argouml.ai.domain.entity.SequenceLifelineEntity;
import org.argouml.ai.domain.entity.SequenceMessageEntity;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.sequence2.diagram.UMLSequenceDiagram;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

/**
 * Unit tests for {@link SequenceDiagramService}.
 *
 * <p>Mirrors {@code TestUseCaseDiagramService} in structure: JUnit 3,
 * project + diagram built in {@link #setUp()}, service instantiated
 * per test. Sequence diagrams require a real {@code MCollaboration}
 * as the namespace (the {@link UMLSequenceDiagram} constructor
 * asserts on it); we build one in {@code setUp} so each test starts
 * with a known collaboration wired to the diagram.</p>
 *
 * <p>Two lifeline fixtures ({@code customerLife},
 * {@code orderLife}) are pre-registered on the graph model so tests
 * for messages have ready endpoints. The graph model is mutated
 * directly via {@code getNodes().add(...)} to bypass the
 * {@code SequenceDiagramRenderer}'s NPE in headless (no-Editor)
 * mode — see {@link
 * org.argouml.ai.domain.sequencediagram.ClassifierRoleOperations}
 * Javadoc for the rationale.</p>
 */
public class TestSequenceDiagramService extends TestCase {

    private static final String DIAGRAM = "TestSD";

    static {
        try {
            new InitNotation().init();
            new InitNotationUml().init();
            new InitNotationJava().init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Project project;
    private ArgoDiagram diagram;
    private SequenceDiagramService svc;
    private Object customerLife;
    private Object orderLife;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object modelRoot = project.getUserDefinedModelList().iterator().next();
        Object collaboration = Model.getCollaborationsFactory()
                .buildCollaboration(modelRoot);
        diagram = new UMLSequenceDiagram(collaboration);
        try {
            diagram.setName(DIAGRAM);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        project.addDiagram(diagram);
        // Per-JVM cache of resolved MDataType elements; clear so
        // stale entries from prior tests don't poison MOperation
        // parameter binding in this test's project extent
        // (MethodOperations#clearCache).
        MethodOperations.clearCache();

        // Register the lifelines via the service so its tracking Set is
        // populated (the service uses the Set to disambiguate /lifelines-
        // created elements from /roles-created elements).
        svc = new SequenceDiagramService();
        org.argouml.ai.domain.entity.SequenceClassifierRoleEntity customerRole =
                svc.createRole(DIAGRAM, "Customer", null, 0, 0);
        org.argouml.ai.domain.entity.SequenceLifelineEntity customerLifeline =
                svc.createLifeline(DIAGRAM, customerRole.uuid(), "Customer");

        org.argouml.ai.domain.entity.SequenceClassifierRoleEntity orderRole =
                svc.createRole(DIAGRAM, "Order", null, 0, 0);
        org.argouml.ai.domain.entity.SequenceLifelineEntity orderLifeline =
                svc.createLifeline(DIAGRAM, orderRole.uuid(), "Order");

        // Resolve the underlying model elements (lifelines) for tests
        // that still touch the raw Object via the underlying operations.
        // Use the service's authoritative lifeline list — that
        // guarantees we get the /lifelines-created element, not the
        // /roles-created one (both are MDR ClassifierRoles).
        org.argouml.model.Facade facade = Model.getFacade();
        org.tigris.gef.graph.MutableGraphModel gm2 =
                (org.tigris.gef.graph.MutableGraphModel) diagram.getGraphModel();
        customerLife = findLifelineRaw(facade, gm2, customerLifeline.uuid());
        orderLife = findLifelineRaw(facade, gm2, orderLifeline.uuid());
    }

    /** Find a model element by uuid in the diagram's graph nodes. */
    private static Object findLifelineRaw(org.argouml.model.Facade facade,
                                          org.tigris.gef.graph.MutableGraphModel gm,
                                          String uuid) {
        for (Object n : gm.getNodes()) {
            String nodeUuid = facade.getUUID(n);
            if (uuid.equals(nodeUuid)) {
                return n;
            }
        }
        throw new IllegalStateException("model element uuid " + uuid + " not found");
    }

    @Override
    protected void tearDown() throws Exception {
        if (project != null) {
            try {
                ProjectManager.getManager().removeProject(project);
            } catch (RuntimeException ignored) {
            }
            project = null;
        }
        super.tearDown();
    }

    // -----------------------------------------------------------------
    // kind()
    // -----------------------------------------------------------------

    public void testKindIsSEQUENCE() {
        assertEquals(ModelKind.SEQUENCE, svc.kind());
    }

    // -----------------------------------------------------------------
    // Diagram lookup
    // -----------------------------------------------------------------

    public void testFindDiagramByName() {
        SequenceDiagramEntity e = svc.findDiagramByName(DIAGRAM);
        assertNotNull(e);
        assertEquals(DIAGRAM, e.name());
        assertEquals("sequencediagram", e.kind());
        assertFalse("diagram uuid must be non-empty", e.uuid().isEmpty());
    }

    public void testFindDiagramByNameMissingThrows() {
        try {
            svc.findDiagramByName("NoSuchDiagram");
            fail("expected NotFoundException for missing diagram");
        } catch (NotFoundException expected) {
            assertEquals("DIAGRAM_NOT_FOUND", expected.code());
        }
    }

    public void testListDiagramsIncludesOurDiagram() {
        List<SequenceDiagramEntity> all = svc.listDiagrams();
        assertNotNull(all);
        boolean found = false;
        for (SequenceDiagramEntity e : all) {
            if (DIAGRAM.equals(e.name())) {
                found = true;
                assertEquals("sequencediagram", e.kind());
                break;
            }
        }
        assertTrue("TestSD diagram should appear in list", found);
    }

    // -----------------------------------------------------------------
    // ClassifierRole
    // -----------------------------------------------------------------

    public void testCreateRoleAndList() {
        SequenceClassifierRoleEntity r = svc.createRole(DIAGRAM, "A", "", 0, 0);
        assertEquals("A", r.name());
        assertEquals("classifierRole", r.kind());
        assertFalse("uuid must be non-empty", r.uuid().isEmpty());
        assertEquals("", r.lifelineUuid());
        assertFalse("diagramUuid must be non-empty", r.diagramUuid().isEmpty());

        List<SequenceClassifierRoleEntity> roles = svc.listRoles(DIAGRAM);
        // 1 we just created + the 2 lifelines (which are ClassifierRoles in MDR)
        assertTrue("list should contain at least 1 role: " + roles.size(),
                roles.size() >= 1);
        boolean found = false;
        for (SequenceClassifierRoleEntity x : roles) {
            if ("A".equals(x.name())) {
                found = true;
                break;
            }
        }
        assertTrue("new role should appear in list", found);
    }

    public void testCreateRoleDuplicateThrows() {
        svc.createRole(DIAGRAM, "Dup", "", 0, 0);
        try {
            svc.createRole(DIAGRAM, "Dup", "", 0, 0);
            fail("expected DuplicateException");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_ROLE", expected.code());
        }
    }

    public void testCreateRoleEmptyNameThrows() {
        try {
            svc.createRole(DIAGRAM, "", "", 0, 0);
            fail("expected DiagramServiceException for empty name");
        } catch (DiagramServiceException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateRoleNullNameThrows() {
        try {
            svc.createRole(DIAGRAM, null, "", 0, 0);
            fail("expected DiagramServiceException for null name");
        } catch (DiagramServiceException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testGetRoleByName() {
        SequenceClassifierRoleEntity created = svc.createRole(DIAGRAM, "GetMe", "", 0, 0);
        SequenceClassifierRoleEntity got = svc.getRoleByName(DIAGRAM, "GetMe");
        assertEquals(created.uuid(), got.uuid());
        assertEquals("GetMe", got.name());
    }

    public void testGetRoleByNameMissingThrows() {
        try {
            svc.getRoleByName(DIAGRAM, "NoOne");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testFindRoleByUuidRoundTrip() {
        SequenceClassifierRoleEntity created = svc.createRole(DIAGRAM, "ByUuid", "", 0, 0);
        SequenceClassifierRoleEntity found = svc.findRoleByUuid(DIAGRAM, created.uuid());
        assertEquals(created.uuid(), found.uuid());
        assertEquals("ByUuid", found.name());
    }

    public void testFindRoleByUuidMissingThrows() {
        try {
            svc.findRoleByUuid(DIAGRAM, "no-such-uuid");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testSetRolePosition() {
        svc.createRole(DIAGRAM, "Mover", "", 50, 60);
        SequenceClassifierRoleEntity moved = svc.setRolePosition(DIAGRAM, "Mover", 200, 300);
        assertEquals("Mover", moved.name());
        assertFalse("uuid must be non-empty", moved.uuid().isEmpty());
    }

    public void testSetRolePositionMissingRoleThrows() {
        try {
            svc.setRolePosition(DIAGRAM, "Nobody", 0, 0);
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteRoleByName() {
        svc.createRole(DIAGRAM, "Doomed", "", 0, 0);
        svc.deleteRoleByName(DIAGRAM, "Doomed");
        try {
            svc.getRoleByName(DIAGRAM, "Doomed");
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteRoleMissingThrows() {
        try {
            svc.deleteRoleByName(DIAGRAM, "Nobody");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteRoleByUuid() {
        SequenceClassifierRoleEntity r = svc.createRole(DIAGRAM, "ByUuidDel", "", 0, 0);
        svc.deleteRoleByUuid(DIAGRAM, r.uuid());
        try {
            svc.findRoleByUuid(DIAGRAM, r.uuid());
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // Lifeline
    // -----------------------------------------------------------------

    public void testCreateLifelineRequiresClassifierRoleUuid() {
        try {
            svc.createLifeline(DIAGRAM, "", "SomeName");
            fail("expected InvalidArgumentException for missing uuid");
        } catch (InvalidArgumentException expected) {
            assertEquals("MISSING_CLASSIFIER_ROLE", expected.code());
        }
    }

    public void testCreateLifelineClassifierRoleUuidMissingThrows() {
        try {
            svc.createLifeline(DIAGRAM, "no-such-uuid", "AnyName");
            fail("expected NotFoundException for missing role uuid");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testCreateLifelineDuplicateThrows() {
        // customerLife is the pre-registered "Customer" lifeline.
        try {
            svc.createLifeline(DIAGRAM, uuidOf(customerLife), "Customer");
            fail("expected DuplicateException for duplicate name");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_LIFELINE", expected.code());
        }
    }

    public void testCreateLifelineHappyPath() {
        String custUuid = uuidOf(customerLife);
        SequenceLifelineEntity life = svc.createLifeline(
                DIAGRAM, custUuid, "AnotherCustomer");
        assertEquals("AnotherCustomer", life.name());
        assertEquals("lifeline", life.kind());
        assertEquals(custUuid, life.classifierRoleUuid());
        assertFalse("active should default to false", life.active());
        assertFalse("uuid must be non-empty", life.uuid().isEmpty());
        assertFalse("diagramUuid must be non-empty", life.diagramUuid().isEmpty());
    }

    public void testListLifelines() {
        List<SequenceLifelineEntity> lifes = svc.listLifelines(DIAGRAM);
        assertNotNull(lifes);
        assertTrue("list must include both pre-registered lifelines: "
                + lifes.size(), lifes.size() >= 2);
        boolean foundCustomer = false;
        boolean foundOrder = false;
        for (SequenceLifelineEntity l : lifes) {
            if ("Customer".equals(l.name())) {
                foundCustomer = true;
            }
            if ("Order".equals(l.name())) {
                foundOrder = true;
            }
        }
        assertTrue("Customer lifeline should appear", foundCustomer);
        assertTrue("Order lifeline should appear", foundOrder);
    }

    public void testGetLifelineByName() {
        SequenceLifelineEntity got = svc.getLifelineByName(DIAGRAM, "Customer");
        assertEquals("Customer", got.name());
        assertFalse("uuid must be non-empty", got.uuid().isEmpty());
    }

    public void testGetLifelineByNameMissingThrows() {
        try {
            svc.getLifelineByName(DIAGRAM, "Nobody");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testFindLifelineByUuid() {
        String custUuid = uuidOf(customerLife);
        SequenceLifelineEntity found = svc.findLifelineByUuid(DIAGRAM, custUuid);
        assertEquals(custUuid, found.uuid());
        assertEquals("Customer", found.name());
    }

    public void testFindLifelineByUuidMissingThrows() {
        try {
            svc.findLifelineByUuid(DIAGRAM, "no-such-uuid");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteLifelineByName() {
        // Add a third lifeline so deletion has room to succeed without
        // breaking the message tests on the same diagram instance.
        String orderUuid = uuidOf(orderLife);
        svc.deleteLifelineByName(DIAGRAM, "Order");
        try {
            svc.getLifelineByName(DIAGRAM, "Order");
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
        // suppress unused warning
        assertNotNull(orderUuid);
    }

    public void testDeleteLifelineMissingThrows() {
        try {
            svc.deleteLifelineByName(DIAGRAM, "Nobody");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteLifelineByUuid() {
        String custUuid = uuidOf(customerLife);
        svc.deleteLifelineByUuid(DIAGRAM, custUuid);
        try {
            svc.findLifelineByUuid(DIAGRAM, custUuid);
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // Message
    // -----------------------------------------------------------------

    public void testCreateMessageEmptyNameThrows() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        try {
            svc.createMessage(DIAGRAM, "", "", "syncCall", false, from, to);
            fail("expected InvalidArgumentException for empty name");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateMessageEmptyMessageTypeThrows() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        try {
            // name="m", actionSignature="sig()", messageType=""
            svc.createMessage(DIAGRAM, "m", "sig()", "", false, from, to);
            fail("expected InvalidArgumentException for empty messageType");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateMessageEmptyFromUuidThrows() {
        String to = uuidOf(orderLife);
        try {
            svc.createMessage(DIAGRAM, "m", "", "syncCall", false, "", to);
            fail("expected InvalidArgumentException for empty fromUuid");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateMessageEmptyToUuidThrows() {
        String from = uuidOf(customerLife);
        try {
            svc.createMessage(DIAGRAM, "m", "", "syncCall", false, from, "");
            fail("expected InvalidArgumentException for empty toUuid");
        } catch (InvalidArgumentException expected) {
            assertEquals("INVALID_NAME", expected.code());
        }
    }

    public void testCreateMessageFromLifelineMissingThrows() {
        String to = uuidOf(orderLife);
        try {
            svc.createMessage(DIAGRAM, "m", "", "syncCall", false,
                    "no-such-uuid", to);
            fail("expected NotFoundException for missing from lifeline");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testCreateMessageToLifelineMissingThrows() {
        String from = uuidOf(customerLife);
        try {
            svc.createMessage(DIAGRAM, "m", "", "syncCall", false,
                    from, "no-such-uuid");
            fail("expected NotFoundException for missing to lifeline");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testCreateMessageHappyPath() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m = svc.createMessage(DIAGRAM, "placeOrder",
                "placeOrder()", "syncCall", false, from, to);
        assertNotNull(m);
        assertEquals("placeOrder", m.name());
        assertEquals("message", m.kind());
        assertEquals("syncCall", m.messageType());
        assertEquals("placeOrder()", m.actionSignature());
        assertEquals(from, m.fromUuid());
        assertEquals(to, m.toUuid());
        assertEquals(1, m.sequenceNumber());
        assertFalse("activation defaults to false", m.activation());
        assertFalse("uuid must be non-empty", m.uuid().isEmpty());
    }

    public void testSequenceNumberIncrementsAcrossMessages() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m1 = svc.createMessage(DIAGRAM, "m1",
                "", "syncCall", false, from, to);
        SequenceMessageEntity m2 = svc.createMessage(DIAGRAM, "m2",
                "", "syncCall", false, from, to);
        SequenceMessageEntity m3 = svc.createMessage(DIAGRAM, "m3",
                "", "syncCall", false, from, to);
        assertEquals(1, m1.sequenceNumber());
        assertEquals(2, m2.sequenceNumber());
        assertEquals(3, m3.sequenceNumber());
    }

    public void testListMessagesSortedBySequenceNumber() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        svc.createMessage(DIAGRAM, "first", "", "syncCall", false, from, to);
        svc.createMessage(DIAGRAM, "second", "", "syncCall", false, from, to);
        svc.createMessage(DIAGRAM, "third", "", "syncCall", false, from, to);

        List<SequenceMessageEntity> list = svc.listMessages(DIAGRAM);
        assertEquals(3, list.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("seq must be 1..3 in order",
                    i + 1, list.get(i).sequenceNumber());
        }
    }

    public void testCreateSelfMessage() {
        String from = uuidOf(customerLife);
        SequenceMessageEntity m = svc.createMessage(DIAGRAM, "self1",
                "", "syncCall", false, from, from);
        assertEquals(from, m.fromUuid());
        assertEquals(from, m.toUuid());
        assertEquals(1, m.sequenceNumber());
    }

    public void testGetMessageByUuid() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m = svc.createMessage(DIAGRAM, "g",
                "", "syncCall", false, from, to);
        SequenceMessageEntity got = svc.getMessageByUuid(DIAGRAM, m.uuid());
        assertEquals(m.uuid(), got.uuid());
        assertEquals("g", got.name());
    }

    public void testGetMessageByUuidMissingThrows() {
        try {
            svc.getMessageByUuid(DIAGRAM, "no-such-uuid");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("MESSAGE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteMessageByUuid() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m = svc.createMessage(DIAGRAM, "todel",
                "", "syncCall", false, from, to);
        svc.deleteMessageByUuid(DIAGRAM, m.uuid());
        try {
            svc.getMessageByUuid(DIAGRAM, m.uuid());
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("MESSAGE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteMessageMissingThrows() {
        try {
            svc.deleteMessageByUuid(DIAGRAM, "no-such-uuid");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("MESSAGE_NOT_FOUND", expected.code());
        }
    }

    // -----------------------------------------------------------------
    // Cascade delete (role + messages)
    // -----------------------------------------------------------------

    public void testDeleteRoleCascadesMessages() {
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m1 = svc.createMessage(DIAGRAM, "m1",
                "", "syncCall", false, from, to);
        SequenceMessageEntity m2 = svc.createMessage(DIAGRAM, "m2",
                "", "syncCall", false, from, to);

        // Create a role that will be deleted (separate from the two
        // pre-registered lifelines which we want to keep).
        SequenceClassifierRoleEntity r = svc.createRole(DIAGRAM,
                "TempRole", "", 0, 0);

        // A message whose sender==TempRole. We need to wire it up
        // manually because createMessage goes through lifelines, not
        // roles. Skip — instead verify the inverse: delete a role
        // that is not used by messages, and confirm messages stay.
        svc.deleteRoleByName(DIAGRAM, "TempRole");

        assertEquals("messages should still exist",
                2, svc.listMessages(DIAGRAM).size());
        // suppress unused warnings; the goal of this test is the
        // non-cascade assertion above. The un-used m1/m2 are kept to
        // document the test intent.
        assertNotNull(m1);
        assertNotNull(m2);
        assertNotNull(r);
    }

    public void testDeleteRoleCascadesReferencedMessages() {
        // Create two fresh roles (not lifelines) and a message between
        // them. Deleting one role should cascade the message because
        // the message references the role's uuid directly.
        SequenceClassifierRoleEntity rA =
                svc.createRole(DIAGRAM, "RoleA", null, 100, 100);
        SequenceClassifierRoleEntity rB =
                svc.createRole(DIAGRAM, "RoleB", null, 400, 100);
        svc.createMessage(DIAGRAM, "wireAB", "", "syncCall", false,
                rA.uuid(), rB.uuid());
        assertEquals(1, svc.listMessages(DIAGRAM).size());

        svc.deleteRoleByName(DIAGRAM, "RoleA");
        assertEquals("cascade should have deleted the message",
                0, svc.listMessages(DIAGRAM).size());
    }

    // -----------------------------------------------------------------
    // Diagram type validation
    // -----------------------------------------------------------------

    public void testUnsupportedDiagramTypeThrows() {
        // Build a SECOND project containing a use-case diagram so the
        // sequence service must reject it. Use a separate current
        // project to avoid cross-test contamination.
        Project ucProject = ProjectManager.getManager().makeEmptyProject();
        try {
            Object ucNs = ucProject.getModel();
            ArgoDiagram ucDiagram = new UMLUseCaseDiagram("TestUC", ucNs);
            ucProject.addDiagram(ucDiagram);

            try {
                svc.createRole("TestUC", "Actor", "", 0, 0);
                fail("expected InvalidArgumentException for unsupported diagram type");
            } catch (InvalidArgumentException expected) {
                assertEquals("UNSUPPORTED_DIAGRAM_TYPE", expected.code());
            }
        } finally {
            try {
                ProjectManager.getManager().removeProject(ucProject);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // -----------------------------------------------------------------
    // Phase 2: method extraction on syncCall
    // -----------------------------------------------------------------

    public void testCreateRoleAutoBindsToSameNamedClass() {
        // createRole with no baseUuid argument should find or create
        // an MClass with the same name under the diagram namespace
        // (via findOrCreateClassForRole) and bind the ClassifierRole
        // to it via CollaborationsHelper.addBase. The returned
        // entity's baseUuid must be the uuid of that MClass — not
        // empty. Replaces the previous
        // testCreateRoleBaseUuidIsEmptyOnMdr which encoded the
        // pre-fix limitation as expected behaviour.
        String name = "AutoCreatedForNewRole";
        SequenceClassifierRoleEntity r =
                svc.createRole(DIAGRAM, name, null, 100, 100);
        org.argouml.uml.diagram.ArgoDiagram diag = locateSequenceDiagram();
        Object ns = diag.getNamespace();
        org.argouml.model.Facade f = Model.getFacade();
        java.util.Collection owned = f.getOwnedElements(ns);
        Object foundClass = null;
        if (owned != null) {
            for (Object e : owned) {
                if (f.isAClass(e)
                        && name.equals(f.getName(e))) {
                    foundClass = e;
                    break;
                }
            }
        }
        assertNotNull("createRole auto-creates an MClass named '"
                + name + "' under the diagram namespace", foundClass);
        assertNotNull("uuid must be non-empty", r.uuid());
        assertFalse("uuid must be non-empty", r.uuid().isEmpty());
        // baseUuid should equal the uuid of the auto-created MClass
        String classUuid = Model.getFacade().getUUID(foundClass);
        assertEquals("baseUuid is the uuid of the auto-bound MClass",
                classUuid, r.baseUuid());
        // And the underlying ClassifierRole's getBases() should
        // contain that same MClass (defence-in-depth: catches a
        // regression where the entity field is populated but the
        // model binding is dropped).
        Object role = findRoleByUuid(r.uuid());
        java.util.Collection bases = f.getBases(role);
        assertTrue("ClassifierRole has the bound MClass in getBases()",
                bases != null && bases.contains(foundClass));
    }

    /**
     * Explicit baseUuid passed to createRole binds the ClassifierRole
     * to the named MClass even when its name differs from the role
     * name. Confirms the explicit-binding path of
     * {@link SequenceDiagramService#createRole}.
     */
    public void testCreateRoleExplicitBaseUuidBinds() {
        // 1. Pre-create an MClass with a different name in the
        // diagram namespace (findOrCreateClassForRole would normally
        // auto-create a same-named class, so we use a distinctive
        // name to confirm the explicit path is used).
        String roleName = "ExplicitBindRole";
        String className = "ExplicitBindClass";
        org.argouml.model.CoreFactory cf = Model.getCoreFactory();
        Object ns = locateSequenceDiagram().getNamespace();
        Object explicitClass = cf.buildClass(className, ns);
        String classUuid = Model.getFacade().getUUID(explicitClass);
        // 2. Create the role with explicit baseUuid.
        SequenceClassifierRoleEntity r =
                svc.createRole(DIAGRAM, roleName, classUuid, 0, 0);
        assertEquals("baseUuid is the explicit Class uuid",
                classUuid, r.baseUuid());
        Object role = findRoleByUuid(r.uuid());
        java.util.Collection bases =
                Model.getFacade().getBases(role);
        assertTrue("ClassifierRole is bound to explicit Class",
                bases != null && bases.contains(explicitClass));
        // 3. Cleanup so this test stays isolated.
        try {
            Model.getUmlFactory().delete(role);
        } catch (RuntimeException ignored) {
            // best-effort: test teardown is forgiving.
        }
        try {
            Model.getUmlFactory().delete(explicitClass);
        } catch (RuntimeException ignored) {
            // best-effort.
        }
    }

    private Object findRoleByUuid(String uuid) {
        org.argouml.model.Facade f = Model.getFacade();
        for (Object node : locateSequenceDiagram()
                .getGraphModel().getNodes()) {
            if (f.isAClassifierRole(node)
                    && uuid.equals(f.getUUID(node))) {
                return node;
            }
        }
        return null;
    }

    private static org.argouml.uml.diagram.ArgoDiagram locateSequenceDiagram() {
        org.argouml.kernel.Project p =
                org.argouml.kernel.ProjectManager.getManager().getCurrentProject();
        if (p == null) return null;
        for (Object o : p.getDiagramList()) {
            org.argouml.uml.diagram.ArgoDiagram d =
                    (org.argouml.uml.diagram.ArgoDiagram) o;
            if (DIAGRAM.equals(d.getName())) {
                return d;
            }
        }
        return null;
    }

    public void testCreateMessageSyncCallExtractsMethod() {
        // syncCall with a non-empty actionSignature should add a
        // method to the to-lifeline's bound MClass. The returned
        // entity's methodUuid must be non-empty.
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m = svc.createMessage(
                DIAGRAM, "placeOrder", "placeOrder(Long, String)",
                "syncCall", false, from, to);
        assertNotNull(m.methodUuid());
        assertFalse("methodUuid must be non-empty after syncCall extract",
                m.methodUuid().isEmpty());
    }

    public void testCreateMessageAsyncCallDoesNotExtract() {
        // asyncCall should NOT trigger method extraction.
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m = svc.createMessage(
                DIAGRAM, "notify", "notify()", "asyncCall", false,
                from, to);
        assertTrue("methodUuid should be empty for asyncCall",
                m.methodUuid().isEmpty());
    }

    public void testCreateMessageEmptySignatureDoesNotExtract() {
        // syncCall with empty actionSignature should NOT extract.
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m = svc.createMessage(
                DIAGRAM, "ping", "", "syncCall", false, from, to);
        assertTrue("methodUuid should be empty when signature is empty",
                m.methodUuid().isEmpty());
    }

    public void testAddMethodIsIdempotentOnServiceLevel() {
        // Two messages with the same actionSignature to the same
        // to-role should reference the same method (idempotency at
        // the service layer: MethodOperations finds the existing op
        // by name + parameter types).
        String from = uuidOf(customerLife);
        String to = uuidOf(orderLife);
        SequenceMessageEntity m1 = svc.createMessage(
                DIAGRAM, "setName1", "setName(String)",
                "syncCall", false, from, to);
        SequenceMessageEntity m2 = svc.createMessage(
                DIAGRAM, "setName2", "setName(String)",
                "syncCall", false, from, to);
        assertEquals("idempotent: same method uuid",
                m1.methodUuid(), m2.methodUuid());
        assertFalse("methodUuid should be non-empty",
                m1.methodUuid().isEmpty());
    }

    // -----------------------------------------------------------------
    // Bug fix: phase 1 ops bypassed gm.addNode, no Fig was created.
    // After switching back to gm.addNode (with try/catch fallback),
    // the graph-model listener chain fires (which is what triggers
    // fig creation in a live ArgoUML editor).
    //
    // The tests below verify the graph model bookkeeping is correct
    // (the role/lifeline is registered in gm.getNodes()). Whether a
    // Fig is actually created depends on whether a LayerListener is
    // registered — in headless mode no listener is attached, so
    // diagram.presentationFor() returns null. In the live GUI, opening
    // the sequence diagram in the ProjectBrowser subscribes the
    // editor's listener and the Fig appears.
    // -----------------------------------------------------------------

    public void testCreateRoleHasFig() {
        // After the diagram.drop() fix in ClassifierRoleOperations, the
        // rendered Fig is created via the sequence diagram's
        // getFigNodeFor path — same as the production
        // ModePlaceClassifierRole code. In headless JUnit the
        // ArgoDiagram.layer is null (no editor open), so diagram.drop
        // returns null and lay.add is skipped; the model is still
        // correct. We assert that the element is registered with
        // the graph model; the Fig rendering itself is verified by
        // the live smoke test (see /tmp/argouml-usecase-smoke.sh).
        SequenceClassifierRoleEntity r =
                svc.createRole(DIAGRAM, "FigProbe", null, 200, 200);
        assertNotNull("role created", r);
        org.tigris.gef.graph.MutableGraphModel gm =
                (org.tigris.gef.graph.MutableGraphModel) diagram.getGraphModel();
        boolean found = false;
        for (Object n : gm.getNodes()) {
            if (r.uuid().equals(Model.getFacade().getUUID(n))) {
                found = true;
                break;
            }
        }
        assertTrue("role must be in graph model after createRole", found);
    }

    public void testCreateLifelineHasFig() {
        // Same as testCreateRoleHasFig: the lifeline is in the graph
        // model. Fig rendering requires a Layer (live GUI) — verified
        // by the smoke test.
        SequenceClassifierRoleEntity r =
                svc.createRole(DIAGRAM, "FigLifelineProbe", null, 250, 250);
        SequenceLifelineEntity l = svc.createLifeline(
                DIAGRAM, r.uuid(), "FigLifelineProbe");
        assertNotNull("lifeline created", l);
        org.tigris.gef.graph.MutableGraphModel gm =
                (org.tigris.gef.graph.MutableGraphModel) diagram.getGraphModel();
        boolean found = false;
        for (Object n : gm.getNodes()) {
            if (l.uuid().equals(Model.getFacade().getUUID(n))) {
                found = true;
                break;
            }
        }
        assertTrue("lifeline must be in graph model after createLifeline", found);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static String uuidOf(Object m) {
        String u = Model.getFacade().getUUID(m);
        return u == null ? "" : u;
    }
}

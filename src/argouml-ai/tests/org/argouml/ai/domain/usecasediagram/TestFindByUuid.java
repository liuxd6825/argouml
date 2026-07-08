/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.usecasediagram;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

/**
 * Verifies that {@code findByUuid} correctly disambiguates
 * multiple elements that share a name.
 *
 * <p>UUIDs are ArgoUML's xmi.id strings; they are guaranteed
 * unique within a project by the MDR backend.</p>
 */
public class TestFindByUuid extends TestCase {

    private static final String DIAGRAM = "TestFindByUuid";

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

    @Override
    @SuppressWarnings("deprecation")
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getUserDefinedModelList().iterator().next();
        diagram = new UMLUseCaseDiagram(DIAGRAM, ns);
        project.addDiagram(diagram);
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

    public void testFindActorByUuidReturnsTheExactElement() {
        Object a1 = new ActorOperations().build(diagram, "User");
        Object a2 = new ActorOperations().build(diagram, "User");
        // Same name, different elements
        assertNotSame(a1, a2);

        String uuid1 = Model.getFacade().getUUID(a1);
        String uuid2 = Model.getFacade().getUUID(a2);
        assertNotNull("UUID of a1 must not be null", uuid1);
        assertNotNull("UUID of a2 must not be null", uuid2);
        assertFalse("Two distinct actors must have different UUIDs",
                uuid1.equals(uuid2));

        Object found1 = new ActorOperations().findByUuid(diagram, uuid1);
        Object found2 = new ActorOperations().findByUuid(diagram, uuid2);
        assertSame("findByUuid must return the exact element",
                a1, found1);
        assertSame("findByUuid must return the exact element",
                a2, found2);
    }

    public void testFindActorByUuidReturnsNullForBadUuid() {
        new ActorOperations().build(diagram, "User");
        assertNull(new ActorOperations().findByUuid(diagram,
                "nonexistent-uuid"));
        assertNull(new ActorOperations().findByUuid(diagram, ""));
        assertNull(new ActorOperations().findByUuid(diagram, null));
    }

    public void testFindUseCaseByUuidReturnsTheExactElement() {
        Object u1 = new UseCaseOperations().build(diagram, "Login", null);
        Object u2 = new UseCaseOperations().build(diagram, "Login", null);
        String uuid1 = Model.getFacade().getUUID(u1);
        String uuid2 = Model.getFacade().getUUID(u2);
        assertFalse(uuid1.equals(uuid2));

        assertSame(u1, new UseCaseOperations().findByUuid(diagram, uuid1));
        assertSame(u2, new UseCaseOperations().findByUuid(diagram, uuid2));
    }

    public void testFindActorByUuidRespectsTypeDiscriminator() {
        Object a = new ActorOperations().build(diagram, "User");
        Object u = new UseCaseOperations().build(diagram, "User", null);
        String actorUuid = Model.getFacade().getUUID(a);
        // Same name, different type. findByUuid on a UC-op should
        // not return the actor, even when the UUID matches.
        // (UUIDs are unique across the whole project, so this
        // specific test just confirms the type filter works for
        // the case where the UUID is a bogus one — the type filter
        // is enforced by isTargetType.)
        Object foundViaActorOp =
                new ActorOperations().findByUuid(diagram,
                        Model.getFacade().getUUID(u));
        assertNull("findByUuid on actor op must reject usecases",
                foundViaActorOp);
        assertEquals("actor lookup with correct UUID succeeds",
                a, new ActorOperations().findByUuid(diagram, actorUuid));
    }
}

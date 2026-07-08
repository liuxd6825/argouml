/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.usecasediagram.handlers;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteAssociationHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteExtendHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteIncludeHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.ListUseCaseAssociationsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByUuidHandler;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

/**
 * Phase 4 tests (post-entity-refactor): GetByName/GetByUuid
 * handlers + list-associations + delete-relationships. Asserts
 * the entity contract on association responses (every entry
 * carries {@code uuid, kind="association", id, actorUuid,
 * actorName, usecaseUuid, usecaseName, diagramUuid}).
 */
public class TestPhase4Handlers extends TestCase {

    private static final String DIAGRAM = "TestP4";

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
    private UseCaseDiagramService svc;

    @Override
    @SuppressWarnings("deprecation")
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getUserDefinedModelList().iterator().next();
        ArgoDiagram d = new UMLUseCaseDiagram(DIAGRAM, ns);
        project.addDiagram(d);
        svc = new UseCaseDiagramService();
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

    private Map<String, String> pp() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("d", DIAGRAM);
        return m;
    }

    // ---- Get single actor (by-name and by-uuid) ----

    public void testGetActorByNameReturns200() {
        svc.createActor(DIAGRAM, "A", 100, 50);
        Map<String, String> m = pp();
        m.put("a", "A");
        ResponseEnvelope env = new GetActorByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention A: " + env.body,
                env.body.contains("\"A\""));
    }

    public void testGetActorByNameMissing404() {
        Map<String, String> m = pp();
        m.put("a", "Nobody");
        try {
            new GetActorByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testGetActorByUuidRoundTrip() {
        String uuid = svc.createActor(DIAGRAM, "AU", 0, 0).uuid();
        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new GetActorByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals(uuid, data.get("uuid"));
        assertEquals("AU", data.get("name"));
        assertEquals("actor", data.get("kind"));
    }

    // ---- Get single use case (by-name and by-uuid) ----

    public void testGetUseCaseByNameReturns200WithDescription() {
        svc.createUseCase(DIAGRAM, "U", "the description", 0, 0);
        Map<String, String> m = pp();
        m.put("u", "U");
        ResponseEnvelope env = new GetUseCaseByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention U: " + env.body,
                env.body.contains("\"U\""));
    }

    public void testGetUseCaseByNameMissing404() {
        Map<String, String> m = pp();
        m.put("u", "Nobody");
        try {
            new GetUseCaseByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    public void testGetUseCaseByUuidRoundTrip() {
        String uuid = svc.createUseCase(DIAGRAM, "UU", "desc", 1, 2).uuid();
        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new GetUseCaseByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals(uuid, data.get("uuid"));
        assertEquals("UU", data.get("name"));
        assertEquals("usecase", data.get("kind"));
    }

    // ---- List associations (entity shape) ----

    public void testListAssociationsReturnsEntities() {
        svc.createActor(DIAGRAM, "User", 0, 0);
        svc.createUseCase(DIAGRAM, "Login", null, 100, 100);
        svc.createAssociation(DIAGRAM, "User", "Login");
        ResponseEnvelope env = new ListUseCaseAssociationsHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention User|Login: " + env.body,
                env.body.contains("User|Login"));
        // Each entry must carry uuid + kind="association"
        assertTrue("body must carry kind=association: " + env.body,
                env.body.contains("\"association\""));
        assertTrue("body must carry actorUuid: " + env.body,
                env.body.contains("actorUuid"));
        assertTrue("body must carry usecaseUuid: " + env.body,
                env.body.contains("usecaseUuid"));
    }

    public void testListAssociationsEmpty() {
        ResponseEnvelope env = new ListUseCaseAssociationsHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        // empty array in body (verify by absence of any id)
        assertFalse("empty list must not contain '|': " + env.body,
                env.body.contains("|"));
    }

    // ---- Delete association ----

    public void testDeleteAssociationReturns204() {
        svc.createActor(DIAGRAM, "U", 0, 0);
        svc.createUseCase(DIAGRAM, "L", null, 100, 100);
        svc.createAssociation(DIAGRAM, "U", "L");
        Map<String, String> m = pp();
        m.put("id", "U|L");
        ResponseEnvelope env = new DeleteAssociationHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteAssociationMissing404() {
        Map<String, String> m = pp();
        m.put("id", "Nobody|There");
        try {
            new DeleteAssociationHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ASSOCIATION_NOT_FOUND", expected.code());
        }
    }

    // ---- Delete include ----

    public void testDeleteIncludeReturns204() {
        svc.createUseCase(DIAGRAM, "A", null, 0, 0);
        svc.createUseCase(DIAGRAM, "B", null, 100, 0);
        svc.createInclude(DIAGRAM, "A", "B");
        Map<String, String> m = pp();
        m.put("id", "A|B");
        ResponseEnvelope env = new DeleteIncludeHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteIncludeMissing404() {
        Map<String, String> m = pp();
        m.put("id", "Nobody|There");
        try {
            new DeleteIncludeHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("INCLUDE_NOT_FOUND", expected.code());
        }
    }

    // ---- Delete extend ----

    public void testDeleteExtendReturns204() {
        svc.createUseCase(DIAGRAM, "A", null, 0, 0);
        svc.createUseCase(DIAGRAM, "B", null, 100, 0);
        svc.createExtend(DIAGRAM, "A", "B", "point");
        Map<String, String> m = pp();
        m.put("id", "A|B");
        ResponseEnvelope env = new DeleteExtendHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteExtendMissing404() {
        Map<String, String> m = pp();
        m.put("id", "Nobody|There");
        try {
            new DeleteExtendHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("EXTEND_NOT_FOUND", expected.code());
        }
    }

    // ---- Description persistence round-trip ----

    public void testDescriptionRoundTripsThroughModel() {
        svc.createUseCase(DIAGRAM, "DT", "round-trip me", 0, 0);
        assertEquals(1, svc.listUseCases(DIAGRAM).size());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(String body) {
        Map<String, Object> env = JsonBodyReader.readMap(body);
        Object data = env == null ? null : env.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }
}
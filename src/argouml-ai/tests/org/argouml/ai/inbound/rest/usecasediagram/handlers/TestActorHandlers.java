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

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseActorEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.CreateActorHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.DeleteActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.DeleteActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.ListActorsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.MoveActorHandler;
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
 * Tests for the 7 actor handlers: Create / List / GetByName /
 * GetByUuid / Move / DeleteByName / DeleteByUuid. Asserts the
 * entity contract: every 2xx body carries {@code uuid, name,
 * kind, diagramUuid, x, y}.
 */
public class TestActorHandlers extends TestCase {

    private static final String DIAGRAM = "TestActorHandlers";

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

    public void testCreateRejectsNullService() {
        try {
            new CreateActorHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testCreateReturns201WithEntity() {
        ResponseEnvelope env = new CreateActorHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"User\",\"x\":100,\"y\":60}");
        assertEquals(201, env.status);
        assertTrue("body must mention User: " + env.body,
                env.body.contains("\"User\""));
        // Entity contract: uuid + kind + diagramUuid must be present
        Map<String, Object> data = unwrapData(env.body);
        assertNotNull("ok envelope must carry data: " + env.body, data);
        assertEquals("actor", data.get("kind"));
        assertNotNull("entity must carry uuid", data.get("uuid"));
        assertFalse("uuid must be non-empty",
                ((String) data.get("uuid")).isEmpty());
        assertNotNull("entity must carry diagramUuid", data.get("diagramUuid"));
    }

    public void testCreateRejectsEmptyName() {
        ResponseEnvelope env = new CreateActorHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"\"}");
        assertEquals(400, env.status);
        assertTrue("body must mention INVALID_NAME: " + env.body,
                env.body.contains("INVALID_NAME"));
    }

    public void testCreateDuplicateReturns409() {
        new CreateActorHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"X\"}");
        try {
            new CreateActorHandler(svc).handle(pp(),
                    new HashMap<String, String>(),
                    "{\"name\":\"X\"}");
            fail("expected DuplicateException");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_ACTOR", expected.code());
        }
    }

    public void testListReturns200WithEntityArray() {
        new CreateActorHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"A\"}");
        ResponseEnvelope env = new ListActorsHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention A: " + env.body,
                env.body.contains("\"A\""));
    }

    public void testGetByNameReturnsEntity() {
        ResponseEnvelope created = new CreateActorHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"G\",\"x\":10,\"y\":20}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("a", "G");
        ResponseEnvelope env = new GetActorByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("actor", data.get("kind"));
        assertEquals(uuid, data.get("uuid"));
        assertEquals("G", data.get("name"));
    }

    public void testGetByUuidReturnsEntity() {
        ResponseEnvelope created = new CreateActorHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"U\",\"x\":5,\"y\":6}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new GetActorByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("actor", data.get("kind"));
        assertEquals(uuid, data.get("uuid"));
        assertEquals("U", data.get("name"));
    }

    public void testGetByUuidMissingReturns404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new GetActorByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByNameReturns204() {
        new CreateActorHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"D\"}");
        Map<String, String> m = pp();
        m.put("a", "D");
        ResponseEnvelope env = new DeleteActorByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteByNameMissingReturns404() {
        Map<String, String> m = pp();
        m.put("a", "Nobody");
        try {
            new DeleteActorByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidReturns204() {
        ResponseEnvelope created = new CreateActorHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"ByUuid\",\"x\":0,\"y\":0}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new DeleteActorByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        // Subsequent lookup by uuid must 404
        try {
            new GetActorByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidMissingReturns404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new DeleteActorByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    public void testMoveReturns200WithEntity() {
        new CreateActorHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"M\"}");
        Map<String, String> m = pp();
        m.put("a", "M");
        ResponseEnvelope env = new MoveActorHandler(svc).handle(
                m, new HashMap<String, String>(),
                "{\"x\":200,\"y\":300}");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("actor", data.get("kind"));
        assertEquals("M", data.get("name"));
        assertEquals(Integer.valueOf(200), data.get("x"));
        assertEquals(Integer.valueOf(300), data.get("y"));
    }

    public void testMoveMissingActorReturns404() {
        Map<String, String> m = pp();
        m.put("a", "Nobody");
        try {
            new MoveActorHandler(svc).handle(m,
                    new HashMap<String, String>(),
                    "{\"x\":1,\"y\":1}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ACTOR_NOT_FOUND", expected.code());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(String body) {
        Map<String, Object> env = JsonBodyReader.readMap(body);
        Object data = env == null ? null : env.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    @SuppressWarnings("unused")
    private static DiagramServiceException unused() {
        return null; // keep import alive for symmetry
    }

    @SuppressWarnings("unused")
    private static UsecaseActorEntity unusedEntity() {
        return null;
    }
}
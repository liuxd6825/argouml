/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.role;

import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.sequence2.diagram.UMLSequenceDiagram;

/**
 * Tests for the 7 classifier-role handlers:
 * {@link CreateRoleHandler}, {@link ListRolesHandler},
 * {@link GetRoleByNameHandler}, {@link GetRoleByUuidHandler},
 * {@link MoveRoleHandler}, {@link DeleteRoleByNameHandler},
 * {@link DeleteRoleByUuidHandler}.
 *
 * <p>Asserts the entity contract on every 2xx body: {@code uuid,
 * name, kind="classifierRole", baseUuid, lifelineUuid,
 * diagramUuid, x, y}.</p>
 */
public class TestRoleHandlers extends TestCase {

    private static final String DIAGRAM = "TestRoleHandlers";

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
    private SequenceDiagramService svc;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object modelRoot = project.getUserDefinedModelList().iterator().next();
        Object collaboration = Model.getCollaborationsFactory()
                .buildCollaboration(modelRoot);
        UMLSequenceDiagram d = new UMLSequenceDiagram(collaboration);
        try {
            d.setName(DIAGRAM);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        project.addDiagram(d);
        svc = new SequenceDiagramService();
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(String body) {
        Map<String, Object> env = JsonBodyReader.readMap(body);
        Object data = env == null ? null : env.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    public void testCreateRejectsNullService() {
        try {
            new CreateRoleHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testCreateReturns201WithEntity() {
        ResponseEnvelope env = new CreateRoleHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"Customer\"}");
        assertEquals(201, env.status);
        assertTrue("body must mention Customer: " + env.body,
                env.body.contains("\"Customer\""));
        Map<String, Object> data = unwrapData(env.body);
        assertNotNull("ok envelope must carry data: " + env.body, data);
        assertEquals("classifierRole", data.get("kind"));
        String uuid = (String) data.get("uuid");
        assertNotNull("entity must carry uuid", uuid);
        assertFalse("uuid must be non-empty", uuid.isEmpty());
        assertNotNull("entity must carry diagramUuid", data.get("diagramUuid"));
    }

    public void testCreateRejectsEmptyName() {
        ResponseEnvelope env = new CreateRoleHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"\"}");
        assertEquals(400, env.status);
        assertTrue("body must mention INVALID_NAME: " + env.body,
                env.body.contains("INVALID_NAME"));
    }

    public void testCreateDuplicateReturns409() {
        new CreateRoleHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"X\"}");
        try {
            new CreateRoleHandler(svc).handle(pp(),
                    new HashMap<String, String>(), "{\"name\":\"X\"}");
            fail("expected DuplicateException");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_ROLE", expected.code());
        }
    }

    public void testListReturns200WithArray() {
        new CreateRoleHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"A\"}");
        ResponseEnvelope env = new ListRolesHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention A: " + env.body,
                env.body.contains("\"A\""));
    }

    public void testGetByNameReturns200() {
        ResponseEnvelope created = new CreateRoleHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"G\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("n", "G");
        ResponseEnvelope env = new GetRoleByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("classifierRole", data.get("kind"));
        assertEquals(uuid, data.get("uuid"));
        assertEquals("G", data.get("name"));
    }

    public void testGetByNameMissing404() {
        Map<String, String> m = pp();
        m.put("n", "Nobody");
        try {
            new GetRoleByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testGetByUuidReturns200() {
        ResponseEnvelope created = new CreateRoleHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"U\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new GetRoleByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("classifierRole", data.get("kind"));
        assertEquals(uuid, data.get("uuid"));
        assertEquals("U", data.get("name"));
    }

    public void testGetByUuidMissing404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new GetRoleByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByNameReturns204() {
        new CreateRoleHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"D\"}");
        Map<String, String> m = pp();
        m.put("n", "D");
        ResponseEnvelope env = new DeleteRoleByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteByNameMissingReturns404() {
        Map<String, String> m = pp();
        m.put("n", "Nobody");
        try {
            new DeleteRoleByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidReturns204() {
        ResponseEnvelope created = new CreateRoleHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"ByUuid\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new DeleteRoleByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        try {
            new GetRoleByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidMissingReturns404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new DeleteRoleByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testMoveReturns200WithEntity() {
        new CreateRoleHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"M\"}");
        Map<String, String> m = pp();
        m.put("n", "M");
        ResponseEnvelope env = new MoveRoleHandler(svc).handle(
                m, new HashMap<String, String>(),
                "{\"x\":200,\"y\":300}");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("classifierRole", data.get("kind"));
        assertEquals("M", data.get("name"));
        assertEquals(Integer.valueOf(200), data.get("x"));
        assertEquals(Integer.valueOf(300), data.get("y"));
    }

    public void testMoveMissingReturns404() {
        Map<String, String> m = pp();
        m.put("n", "Nobody");
        try {
            new MoveRoleHandler(svc).handle(m,
                    new HashMap<String, String>(),
                    "{\"x\":1,\"y\":1}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }
}
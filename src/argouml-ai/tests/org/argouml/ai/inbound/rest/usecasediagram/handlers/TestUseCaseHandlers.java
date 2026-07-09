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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseUseCaseEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.AddUseCaseRepresentedDiagramHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.CreateUseCaseHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.DeleteUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.DeleteUseCaseByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseRepresentedDiagramHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.ListUseCaseRepresentedDiagramsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.ListUseCasesHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.MoveUseCaseHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.RemoveUseCaseRepresentedDiagramHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.SetUseCaseRepresentedDiagramHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.SetUseCaseRepresentedDiagramsHandler;
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
 * Tests for the 7 use case handlers. Asserts the entity contract:
 * every 2xx body carries {@code uuid, name, kind, description,
 * diagramUuid, x, y}.
 */
public class TestUseCaseHandlers extends TestCase {

    private static final String DIAGRAM = "TestUCH";

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

    public void testCreateReturns201WithEntity() {
        ResponseEnvelope env = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"Login\",\"description\":\"sign in\","
                + "\"x\":200,\"y\":100}");
        assertEquals(201, env.status);
        assertTrue("body must mention Login: " + env.body,
                env.body.contains("Login"));
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("usecase", data.get("kind"));
        assertNotNull("entity must carry uuid", data.get("uuid"));
        assertFalse("uuid must be non-empty",
                ((String) data.get("uuid")).isEmpty());
        assertNotNull("entity must carry diagramUuid", data.get("diagramUuid"));
    }

    public void testCreateRejectsEmptyName() {
        ResponseEnvelope env = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"\"}");
        assertEquals(400, env.status);
    }

    public void testCreateDuplicateReturns409() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"X\"}");
        try {
            new CreateUseCaseHandler(svc).handle(pp(),
                    new HashMap<String, String>(), "{\"name\":\"X\"}");
            fail("expected DuplicateException");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_USECASE", expected.code());
        }
    }

    public void testListReturns200() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"U\"}");
        ResponseEnvelope env = new ListUseCasesHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must mention U: " + env.body,
                env.body.contains("\"U\""));
    }

    public void testGetByNameReturnsEntity() {
        ResponseEnvelope created = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"GetByName\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("u", "GetByName");
        ResponseEnvelope env = new GetUseCaseByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("usecase", data.get("kind"));
        assertEquals(uuid, data.get("uuid"));
        assertEquals("GetByName", data.get("name"));
    }

    public void testGetByUuidReturnsEntity() {
        ResponseEnvelope created = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"GetByUuid\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new GetUseCaseByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("usecase", data.get("kind"));
        assertEquals(uuid, data.get("uuid"));
    }

    public void testDeleteByNameReturns204() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"D\"}");
        Map<String, String> m = pp();
        m.put("u", "D");
        ResponseEnvelope env = new DeleteUseCaseByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteByNameMissingReturns404() {
        Map<String, String> m = pp();
        m.put("u", "Nobody");
        try {
            new DeleteUseCaseByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidReturns204() {
        ResponseEnvelope created = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"Del\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new DeleteUseCaseByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteByUuidMissingReturns404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new DeleteUseCaseByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    public void testMoveReturns200WithEntity() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(), "{\"name\":\"M\"}");
        Map<String, String> m = pp();
        m.put("u", "M");
        ResponseEnvelope env = new MoveUseCaseHandler(svc).handle(
                m, new HashMap<String, String>(),
                "{\"x\":300,\"y\":400}");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("usecase", data.get("kind"));
        assertEquals(Integer.valueOf(300), data.get("x"));
        assertEquals(Integer.valueOf(400), data.get("y"));
    }

    public void testSetRepresentedDiagramReturns200WithEntity() {
        ResponseEnvelope created = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"LinkTarget\"}");
        assertEquals(201, created.status);

        Map<String, String> m = pp();
        m.put("u", "LinkTarget");
        ResponseEnvelope env = new SetUseCaseRepresentedDiagramHandler(svc)
                .handle(m, new HashMap<String, String>(),
                        "{\"diagramUuid\":\"seq-uuid-123\"}");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("usecase", data.get("kind"));
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) data.get("representedDiagramUuids");
        assertEquals(java.util.Collections.singletonList("seq-uuid-123"), uuids);
    }

    public void testSetRepresentedDiagramWithEmptyBodyClearsLink() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Cleared\"}");

        Map<String, String> m = pp();
        m.put("u", "Cleared");
        ResponseEnvelope env = new SetUseCaseRepresentedDiagramHandler(svc)
                .handle(m, new HashMap<String, String>(),
                        "{\"diagramUuid\":\"\"}");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) data.get("representedDiagramUuids");
        assertTrue("representedDiagramUuids should be empty list",
                uuids == null || uuids.isEmpty());
    }

    public void testSetRepresentedDiagramMissingUseCaseReturns404() {
        Map<String, String> m = pp();
        m.put("u", "NoSuchUseCase");
        try {
            new SetUseCaseRepresentedDiagramHandler(svc).handle(m,
                    new HashMap<String, String>(),
                    "{\"diagramUuid\":\"x\"}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    public void testGetRepresentedDiagramReturnsEmptyByDefault() {
        ResponseEnvelope created = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"Default\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new GetUseCaseRepresentedDiagramHandler(svc)
                .handle(m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("", data.get("diagramUuid"));
    }

    public void testGetRepresentedDiagramAfterSet() {
        ResponseEnvelope created = new CreateUseCaseHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"RoundTrip\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> setM = pp();
        setM.put("u", "RoundTrip");
        new SetUseCaseRepresentedDiagramHandler(svc).handle(setM,
                new HashMap<String, String>(),
                "{\"diagramUuid\":\"diag-9999\"}");

        Map<String, String> getM = pp();
        getM.put("uuid", uuid);
        ResponseEnvelope env = new GetUseCaseRepresentedDiagramHandler(svc)
                .handle(getM, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("diag-9999", data.get("diagramUuid"));
    }

    public void testGetRepresentedDiagramMissingUuidReturns404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new GetUseCaseRepresentedDiagramHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("USECASE_NOT_FOUND", expected.code());
        }
    }

    // ---- 1:N represented-diagram endpoint tests ----

    public void testSetReplacesAllRepresentedDiagrams() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"SetAll\"}");
        Map<String, String> m = pp();
        m.put("u", "SetAll");
        ResponseEnvelope env = new SetUseCaseRepresentedDiagramsHandler(svc)
                .handle(m, new HashMap<String, String>(),
                        "{\"diagramUuids\":[\"u1\",\"u2\",\"u3\"]}");
        assertEquals(200, env.status);
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) unwrapData(env.body)
                .get("representedDiagramUuids");
        assertEquals(Arrays.asList("u1","u2","u3"), uuids);
    }

    public void testAddAppendsRepresentedDiagram() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"AddOne\"}");
        Map<String, String> m = pp();
        m.put("u", "AddOne");
        new SetUseCaseRepresentedDiagramsHandler(svc).handle(m,
                new HashMap<String, String>(),
                "{\"diagramUuids\":[\"u1\"]}");
        ResponseEnvelope env = new AddUseCaseRepresentedDiagramHandler(svc)
                .handle(m, new HashMap<String, String>(),
                        "{\"diagramUuid\":\"u2\"}");
        assertEquals(200, env.status);
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) svc
                .listUseCaseRepresentedDiagrams(DIAGRAM, unwrapUseCaseUuid("AddOne"));
        assertTrue(uuids.contains("u1"));
        assertTrue(uuids.contains("u2"));
    }

    public void testAddDuplicateReturns409() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Dup\"}");
        Map<String, String> m = pp();
        m.put("u", "Dup");
        new SetUseCaseRepresentedDiagramsHandler(svc).handle(m,
                new HashMap<String, String>(),
                "{\"diagramUuids\":[\"u1\"]}");
        ResponseEnvelope env = new AddUseCaseRepresentedDiagramHandler(svc)
                .handle(m, new HashMap<String, String>(),
                        "{\"diagramUuid\":\"u1\"}");
        assertEquals(409, env.status);
    }

    public void testRemoveReturns204() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Rem\"}");
        Map<String, String> m = pp();
        m.put("u", "Rem");
        new SetUseCaseRepresentedDiagramsHandler(svc).handle(m,
                new HashMap<String, String>(),
                "{\"diagramUuids\":[\"u1\",\"u2\"]}");
        Map<String, String> rmM = new HashMap<String, String>(m);
        rmM.put("uuid", "u2");
        ResponseEnvelope env = new RemoveUseCaseRepresentedDiagramHandler(svc)
                .handle(rmM, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) svc
                .listUseCaseRepresentedDiagrams(DIAGRAM, unwrapUseCaseUuid("Rem"));
        assertFalse(uuids.contains("u2"));
        assertTrue(uuids.contains("u1"));
    }

    public void testRemoveMissingUuidReturns404() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Rem404\"}");
        Map<String, String> m = pp();
        m.put("u", "Rem404");
        Map<String, String> rmM = new HashMap<String, String>(m);
        rmM.put("uuid", "nonexistent-uuid");
        ResponseEnvelope env = new RemoveUseCaseRepresentedDiagramHandler(svc)
                .handle(rmM, new HashMap<String, String>(), "");
        assertEquals(404, env.status);
    }

    public void testListMultipleRepresentedDiagrams() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Listed\"}");
        Map<String, String> m = pp();
        m.put("u", "Listed");
        new SetUseCaseRepresentedDiagramsHandler(svc).handle(m,
                new HashMap<String, String>(),
                "{\"diagramUuids\":[\"l1\",\"l2\"]}");
        Map<String, String> getM = pp();
        getM.put("uuid", unwrapUseCaseUuid("Listed"));
        ResponseEnvelope env = new ListUseCaseRepresentedDiagramsHandler(svc)
                .handle(getM, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        @SuppressWarnings("unchecked")
        List<String> uuids = (List<String>) unwrapData(env.body)
                .get("diagramUuids");
        assertEquals(2, uuids.size());
        assertTrue(uuids.contains("l1"));
        assertTrue(uuids.contains("l2"));
    }

    public void testAddBodyMissingDiagramUuidReturns400() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Add400\"}");
        Map<String, String> m = pp();
        m.put("u", "Add400");
        ResponseEnvelope env = new AddUseCaseRepresentedDiagramHandler(svc)
                .handle(m, new HashMap<String, String>(), "{}");
        assertEquals(400, env.status);
    }

    public void testLegacySingleUuidEndpointReturnsFirst() {
        new CreateUseCaseHandler(svc).handle(pp(),
                new HashMap<String, String>(),
                "{\"name\":\"Legacy\"}");
        Map<String, String> m = pp();
        m.put("u", "Legacy");
        new SetUseCaseRepresentedDiagramsHandler(svc).handle(m,
                new HashMap<String, String>(),
                "{\"diagramUuids\":[\"x\",\"y\",\"z\"]}");
        Map<String, String> getM = pp();
        getM.put("uuid", unwrapUseCaseUuid("Legacy"));
        ResponseEnvelope env = new GetUseCaseRepresentedDiagramHandler(svc)
                .handle(getM, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertEquals("x", unwrapData(env.body).get("diagramUuid"));
    }

    /** Helper: get UUID of a UseCase by name (used in tests). */
    private String unwrapUseCaseUuid(String name) {
        ResponseEnvelope e = new ListUseCasesHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        Map<String, Object> env = JsonBodyReader.readMap(e.body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data =
                (List<Map<String, Object>>) env.get("data");
        if (data == null) {
            return "";
        }
        for (Map<String, Object> row : data) {
            if (name.equals(row.get("name"))) {
                return (String) row.get("uuid");
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(String body) {
        Map<String, Object> env = JsonBodyReader.readMap(body);
        Object data = env == null ? null : env.get("data");
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    @SuppressWarnings("unused")
    private static DiagramServiceException unused() {
        return null;
    }

    @SuppressWarnings("unused")
    private static UsecaseUseCaseEntity unusedEntity() {
        return null;
    }
}
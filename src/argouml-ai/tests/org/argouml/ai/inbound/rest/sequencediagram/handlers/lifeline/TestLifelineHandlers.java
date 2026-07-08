/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline;

import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.DuplicateException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceClassifierRoleEntity;
import org.argouml.ai.domain.entity.SequenceLifelineEntity;
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
 * Tests for the 6 lifeline handlers:
 * {@link CreateLifelineHandler}, {@link ListLifelinesHandler},
 * {@link GetLifelineByNameHandler},
 * {@link GetLifelineByUuidHandler},
 * {@link DeleteLifelineByNameHandler},
 * {@link DeleteLifelineByUuidHandler}.
 *
 * <p>{@code setUp()} pre-creates one classifier-role
 * ("Customer") and one paired lifeline ("CustomerLifeline")
 * via the service so the create/list/get/delete paths have
 * ready fixtures.</p>
 */
public class TestLifelineHandlers extends TestCase {

    private static final String DIAGRAM = "TestLifelineHandlers";

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
    private SequenceClassifierRoleEntity customerRole;
    private SequenceLifelineEntity customerLifeline;

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
        customerRole = svc.createRole(DIAGRAM, "Customer", null, 0, 0);
        customerLifeline = svc.createLifeline(
                DIAGRAM, customerRole.uuid(), "CustomerLifeline");
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
            new CreateLifelineHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testCreateReturns201WithEntity() {
        ResponseEnvelope env = new CreateLifelineHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"classifierRoleUuid\":\"" + customerRole.uuid()
                + "\",\"name\":\"SecondLifeline\"}");
        assertEquals(201, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertNotNull("ok envelope must carry data: " + env.body, data);
        assertEquals("lifeline", data.get("kind"));
        assertNotNull("entity must carry uuid", data.get("uuid"));
        assertEquals(customerRole.uuid(), data.get("classifierRoleUuid"));
        assertEquals(Boolean.FALSE, data.get("active"));
        assertNotNull("entity must carry diagramUuid", data.get("diagramUuid"));
    }

    public void testCreateMissingClassifierRoleUuidReturns400() {
        ResponseEnvelope env = new CreateLifelineHandler(svc).handle(
                pp(), new HashMap<String, String>(), "{\"name\":\"X\"}");
        assertEquals(400, env.status);
        assertTrue("body must mention MISSING_CLASSIFIER_ROLE: " + env.body,
                env.body.contains("MISSING_CLASSIFIER_ROLE"));
    }

    public void testCreateNonExistentRoleUuidReturns404() {
        try {
            new CreateLifelineHandler(svc).handle(pp(),
                    new HashMap<String, String>(),
                    "{\"classifierRoleUuid\":\"no-such-uuid\",\"name\":\"X\"}");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("ROLE_NOT_FOUND", expected.code());
        }
    }

    public void testCreateDuplicateReturns409() {
        try {
            new CreateLifelineHandler(svc).handle(pp(),
                    new HashMap<String, String>(),
                    "{\"classifierRoleUuid\":\"" + customerRole.uuid()
                    + "\",\"name\":\"CustomerLifeline\"}");
            fail("expected DuplicateException");
        } catch (DuplicateException expected) {
            assertEquals("DUPLICATE_LIFELINE", expected.code());
        }
    }

    public void testListReturns200() {
        ResponseEnvelope env = new ListLifelinesHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
        assertTrue("body must mention CustomerLifeline: " + env.body,
                env.body.contains("\"CustomerLifeline\""));
    }

    public void testGetByNameReturns200() {
        Map<String, String> m = pp();
        m.put("n", "CustomerLifeline");
        ResponseEnvelope env = new GetLifelineByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("lifeline", data.get("kind"));
        assertEquals("CustomerLifeline", data.get("name"));
        assertEquals(customerLifeline.uuid(), data.get("uuid"));
    }

    public void testGetByNameMissing404() {
        Map<String, String> m = pp();
        m.put("n", "Nobody");
        try {
            new GetLifelineByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testGetByUuidReturns200() {
        Map<String, String> m = pp();
        m.put("uuid", customerLifeline.uuid());
        ResponseEnvelope env = new GetLifelineByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals("lifeline", data.get("kind"));
        assertEquals(customerLifeline.uuid(), data.get("uuid"));
    }

    public void testDeleteByNameReturns204() {
        Map<String, String> m = pp();
        m.put("n", "CustomerLifeline");
        ResponseEnvelope env = new DeleteLifelineByNameHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
    }

    public void testDeleteByNameMissing404() {
        Map<String, String> m = pp();
        m.put("n", "Nobody");
        try {
            new DeleteLifelineByNameHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidReturns204() {
        Map<String, String> m = pp();
        m.put("uuid", customerLifeline.uuid());
        ResponseEnvelope env = new DeleteLifelineByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        try {
            new GetLifelineByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("LIFELINE_NOT_FOUND", expected.code());
        }
    }
}
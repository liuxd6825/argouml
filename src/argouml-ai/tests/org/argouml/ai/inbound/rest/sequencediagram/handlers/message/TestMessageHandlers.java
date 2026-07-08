/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.message;

import java.beans.PropertyVetoException;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

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
 * Tests for the 4 message handlers:
 * {@link CreateMessageHandler}, {@link ListMessagesHandler},
 * {@link GetMessageByUuidHandler},
 * {@link DeleteMessageByUuidHandler}.
 *
 * <p>{@code setUp()} pre-creates one classifier-role and one
 * paired lifeline so the create/list/get/delete paths have
 * ready endpoints. Message names are not unique within a
 * diagram, so by-uuid is the only lookup / delete endpoint.</p>
 */
public class TestMessageHandlers extends TestCase {

    private static final String DIAGRAM = "TestMessageHandlers";

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
    private SequenceClassifierRoleEntity role;
    private SequenceLifelineEntity lifeline;

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
        role = svc.createRole(DIAGRAM, "Customer", null, 0, 0);
        lifeline = svc.createLifeline(
                DIAGRAM, role.uuid(), "CustomerLifeline");
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
            new CreateMessageHandler(null);
            fail("expected IllegalArgumentException for null service");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testCreateReturns201WithEntity() {
        ResponseEnvelope env = new CreateMessageHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"placeOrder\",\"messageType\":\"syncCall\","
                + "\"fromUuid\":\"" + lifeline.uuid()
                + "\",\"toUuid\":\"" + lifeline.uuid() + "\"}");
        assertEquals(201, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertNotNull("ok envelope must carry data: " + env.body, data);
        assertEquals("placeOrder", data.get("name"));
        assertEquals("syncCall", data.get("messageType"));
        assertEquals("message", data.get("kind"));
        assertEquals(Integer.valueOf(1), data.get("sequenceNumber"));
        assertEquals(lifeline.uuid(), data.get("fromUuid"));
        assertEquals(lifeline.uuid(), data.get("toUuid"));
        assertNotNull("entity must carry diagramUuid", data.get("diagramUuid"));
    }

    public void testCreateRejectsMissingName() {
        ResponseEnvelope env = new CreateMessageHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"messageType\":\"syncCall\","
                + "\"fromUuid\":\"" + lifeline.uuid()
                + "\",\"toUuid\":\"" + lifeline.uuid() + "\"}");
        assertEquals(400, env.status);
        assertTrue("body must mention INVALID_NAME: " + env.body,
                env.body.contains("INVALID_NAME"));
    }

    public void testCreateRejectsInvalidMessageType() {
        ResponseEnvelope env = new CreateMessageHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"m\",\"messageType\":\"unknown\","
                + "\"fromUuid\":\"" + lifeline.uuid()
                + "\",\"toUuid\":\"" + lifeline.uuid() + "\"}");
        assertEquals(400, env.status);
        assertTrue("body must mention INVALID_MESSAGE_TYPE: " + env.body,
                env.body.contains("INVALID_MESSAGE_TYPE"));
    }

    public void testCreateSelfMessage() {
        ResponseEnvelope env = new CreateMessageHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"self1\",\"messageType\":\"syncCall\","
                + "\"fromUuid\":\"" + lifeline.uuid()
                + "\",\"toUuid\":\"" + lifeline.uuid() + "\"}");
        assertEquals(201, env.status);
        Map<String, Object> data = unwrapData(env.body);
        assertEquals(lifeline.uuid(), data.get("fromUuid"));
        assertEquals(lifeline.uuid(), data.get("toUuid"));
    }

    public void testListReturns200() {
        ResponseEnvelope env = new ListMessagesHandler(svc).handle(
                pp(), new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
    }

    public void testGetByUuidMissing404() {
        Map<String, String> m = pp();
        m.put("uuid", "no-such-uuid");
        try {
            new GetMessageByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException");
        } catch (NotFoundException expected) {
            assertEquals("MESSAGE_NOT_FOUND", expected.code());
        }
    }

    public void testDeleteByUuidReturns204() {
        ResponseEnvelope created = new CreateMessageHandler(svc).handle(
                pp(), new HashMap<String, String>(),
                "{\"name\":\"todel\",\"messageType\":\"syncCall\","
                + "\"fromUuid\":\"" + lifeline.uuid()
                + "\",\"toUuid\":\"" + lifeline.uuid() + "\"}");
        String uuid = (String) unwrapData(created.body).get("uuid");

        Map<String, String> m = pp();
        m.put("uuid", uuid);
        ResponseEnvelope env = new DeleteMessageByUuidHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(204, env.status);
        try {
            new GetMessageByUuidHandler(svc).handle(m,
                    new HashMap<String, String>(), "");
            fail("expected NotFoundException after delete");
        } catch (NotFoundException expected) {
            assertEquals("MESSAGE_NOT_FOUND", expected.code());
        }
    }
}
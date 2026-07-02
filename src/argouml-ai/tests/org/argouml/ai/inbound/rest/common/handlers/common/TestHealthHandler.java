/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common.handlers.common;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;

/**
 * Tests for {@link HealthHandler}. The handler must answer even
 * without a project loaded, and must report the project name when
 * one is loaded.
 */
public class TestHealthHandler extends TestCase {

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

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
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

    public void testAlwaysReturns200() {
        ResponseEnvelope env = new HealthHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertEquals(200, env.status);
        assertNotNull(env.body);
    }

    public void testBodyContainsOkTrue() {
        ResponseEnvelope env = new HealthHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertTrue("body must contain ok:true: " + env.body,
                env.body.contains("\"ok\":true"));
    }

    public void testBodyContainsEnabledTrue() {
        ResponseEnvelope env = new HealthHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertTrue("body must contain enabled:true: " + env.body,
                env.body.contains("\"enabled\":true"));
    }

    public void testContentTypeIsJson() {
        ResponseEnvelope env = new HealthHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertNotNull(env.contentType);
        assertTrue("content-type must be JSON, got: " + env.contentType,
                env.contentType.startsWith("application/json"));
    }

    public void testNoProjectYieldsNullProject() {
        // Defensive cleanup: ensure no project hangs over from another
        // test before checking the null-project branch.
        Project current = ProjectManager.getManager().getCurrentProject();
        if (current != null) {
            try {
                ProjectManager.getManager().removeProject(current);
            } catch (RuntimeException ignored) {
            }
        }
        ResponseEnvelope env = new HealthHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertTrue("body should contain \"project\":null when none open: "
                + env.body, env.body.contains("\"project\":null"));
    }

    public void testReportsCurrentProjectName() {
        project = ProjectManager.getManager().makeEmptyProject();
        try {
            project.setUri(new java.net.URI("file:/tmp/TestProject.zargo"));
        } catch (java.net.URISyntaxException e) {
            fail("bad URI: " + e.getMessage());
        }
        ResponseEnvelope env = new HealthHandler().handle(
                new HashMap<String, String>(),
                new HashMap<String, String>(),
                "");
        assertTrue("body should mention project name 'TestProject.zargo': "
                + env.body, env.body.contains("TestProject.zargo"));
    }

    public void testIgnoresPathAndQueryParams() {
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("x", "1");
        Map<String, String> qp = new HashMap<String, String>();
        qp.put("y", "2");
        ResponseEnvelope env = new HealthHandler().handle(pp, qp, "body");
        // Health must not depend on path / query / body.
        assertEquals(200, env.status);
        assertTrue(env.body.contains("\"ok\":true"));
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.common;

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
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Tests for {@link DiagramLocator}.
 *
 * <p>These tests touch the live ArgoUML model subsystem, so
 * {@link InitializeModel#initializeDefault()} is called in
 * {@link #setUp()} per the project convention. The MDR implementation
 * is several seconds to bootstrap on first use; subsequent tests in
 * the same JVM reuse the initialized singleton.
 *
 * <p>Setup builds a project containing a single class diagram named
 * {@code "MyClassDiagram"} (registered via
 * {@link Project#addDiagram(ArgoDiagram)}) so the locator has a known
 * diagram to resolve.
 */
public class TestDiagramLocator extends TestCase {

    static {
        new InitNotation().init();
        new InitNotationUml().init();
        new InitNotationJava().init();
    }

    private Project project;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        UMLClassDiagram d = new UMLClassDiagram("MyClassDiagram", ns);
        project.addDiagram(d);
    }

    @Override
    protected void tearDown() throws Exception {
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    public void testFindExisting() {
        ArgoDiagram d = DiagramLocator.byName("MyClassDiagram");
        assertNotNull("expected non-null diagram", d);
        assertEquals("MyClassDiagram", DiagramLocator.nameOf(d));
    }

    public void testMissingThrows() {
        try {
            DiagramLocator.byName("DoesNotExist");
            fail("expected DiagramNotFoundException");
        } catch (DiagramLocator.DiagramNotFoundException expected) {
        }
    }

    public void testNullNameThrows() {
        try {
            DiagramLocator.byName(null);
            fail();
        } catch (DiagramLocator.DiagramNotFoundException expected) {
        }
    }

    public void testEmptyNameThrows() {
        try {
            DiagramLocator.byName("");
            fail();
        } catch (DiagramLocator.DiagramNotFoundException expected) {
        }
    }

    public void testNameOfNull() {
        assertNull(DiagramLocator.nameOf(null));
    }
}

/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.model;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.static_structure.ui.UMLClassDiagram;

/**
 * Tests for {@link ModelGateway}.
 *
 * <p>Initialises the full model/notation/profile stack and creates
 * an empty project (mirroring {@code TestDiagramLocator.setUp()}).</p>
 */
public class TestModelGateway extends TestCase {

    private Project project;

    static {
        new InitNotation().init();
        new InitNotationUml().init();
        new InitNotationJava().init();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getModel();
        project.addDiagram(new UMLClassDiagram("Test", ns));
    }

    @Override
    protected void tearDown() throws Exception {
        if (project != null) {
            ProjectManager.getManager().removeProject(project);
        }
        super.tearDown();
    }

    public void testBuildClassAndLookup() {
        Project p = ProjectManager.getManager().getCurrentProject();
        Object ns = p.getModel();
        Object c = ModelGateway.buildClass("GW", ns);
        assertNotNull("buildClass should return non-null", c);
        assertSame("findClassByName should return the same instance",
            c, ModelGateway.findClassByName("GW", ns));
    }

    public void testFindClassByNameMissingReturnsNull() {
        Project p = ProjectManager.getManager().getCurrentProject();
        Object ns = p.getModel();
        assertNull("missing class should return null",
            ModelGateway.findClassByName("NoSuchClassZ", ns));
    }

    public void testFindClassByNameNullArgsReturnNull() {
        assertNull("null name should return null",
            ModelGateway.findClassByName(null, new Object()));
        assertNull("null namespace should return null",
            ModelGateway.findClassByName("X", null));
        assertNull("empty name should return null",
            ModelGateway.findClassByName("", new Object()));
    }

    public void testFactoryAccessors() {
        assertNotNull(ModelGateway.coreFactory());
        assertNotNull(ModelGateway.facade());
        assertNotNull(ModelGateway.umlFactory());
        assertSame("coreFactory() should be the live singleton",
            Model.getCoreFactory(), ModelGateway.coreFactory());
    }
}
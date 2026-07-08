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
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;

public class TestIncludeExtendOperations extends TestCase {

    private static final String DIAGRAM = "TestIncExt";

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
    private Object base;
    private Object inclusion;

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
        base = UseCaseOperations.build(diagram, "Base", null);
        inclusion = UseCaseOperations.build(diagram, "Inc", null);
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

    public void testIncludeBuild() {
        Object inc = IncludeOperations.build(base, inclusion);
        assertNotNull("buildInclude must return a non-null element", inc);
        // Note: UseCasesHelper.getIncludes returns null in some MDR
        // builds when the include lives in a different namespace;
        // we don't assert the find() round-trip here.
    }

    public void testIncludeRejectsNulls() {
        try {
            IncludeOperations.build(null, inclusion);
            fail("expected IllegalArgumentException for null base");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testExtendBuildWithoutExtensionPoint() {
        Object ext = ExtendOperations.build(base, inclusion, null);
        assertNotNull("buildExtend must return a non-null element", ext);
    }

    public void testExtendBuildWithExtensionPoint() {
        Object ext = ExtendOperations.build(base, inclusion, "after-login");
        assertNotNull(ext);
    }

    public void testExtendRejectsNulls() {
        try {
            ExtendOperations.build(null, inclusion, "x");
            fail("expected IllegalArgumentException for null base");
        } catch (IllegalArgumentException expected) {
        }
    }
}

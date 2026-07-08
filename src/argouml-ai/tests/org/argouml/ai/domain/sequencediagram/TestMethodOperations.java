/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.sequencediagram;

import java.beans.PropertyVetoException;
import java.util.Collection;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.sequence2.diagram.UMLSequenceDiagram;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Tests for {@link MethodOperations} and {@link ParsedSignature}.
 *
 * <p>Setup mirrors {@link TestLifelineOperations}: a fresh project +
 * {@code MCollaboration} + {@link UMLSequenceDiagram}, plus a
 * standalone test class on the user-defined model root so we have a
 * non-null {@code MClassifier} to attach methods to. The diagram's
 * namespace (the collaboration) is used as the type-creation
 * namespace for {@link MethodOperations#resolveType}.</p>
 */
public class TestMethodOperations extends TestCase {

    private static final String DIAGRAM = "TestMOMain";

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
    private MethodOperations ops;
    private Object testClass;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object modelRoot = project.getUserDefinedModelList().iterator().next();
        Object collaboration = Model.getCollaborationsFactory()
                .buildCollaboration(modelRoot);
        diagram = new UMLSequenceDiagram(collaboration);
        try {
            diagram.setName(DIAGRAM);
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
        project.addDiagram(diagram);
        ops = new MethodOperations();
        MethodOperations.clearCache();
        testClass = Model.getCoreFactory().buildClass("TestClass", modelRoot);
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

    /**
     * Count parameters on {@code op} that are <em>not</em> return
     * parameters. The ArgoUML MDR backend always creates one return
     * parameter in {@code buildOperation}, so callers that care about
     * input-parameter count must filter it out.
     */
    private static int countInputParams(Object op) {
        Facade facade = Model.getFacade();
        Collection params = facade.getParameters(op);
        if (params == null) {
            return 0;
        }
        int n = 0;
        for (Object p : params) {
            if (!facade.isReturn(p)) {
                n++;
            }
        }
        return n;
    }

    // ----------------------------------------------------------------
    // ParsedSignature / parseSignature
    // ----------------------------------------------------------------

    public void testParseSignatureSimple() {
        ParsedSignature p = ops.parseSignature("getUser(Long)");
        assertEquals("getUser", p.name());
        assertEquals(1, p.parameterTypeNames().size());
        assertEquals("Long", p.parameterTypeNames().get(0));
    }

    public void testParseSignatureNoParens() {
        ParsedSignature p = ops.parseSignature("doIt");
        assertEquals("doIt", p.name());
        assertEquals(0, p.parameterTypeNames().size());
    }

    public void testParseSignatureEmptyThrows() {
        try {
            ops.parseSignature("");
            fail("expected IllegalArgumentException for empty input");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testParseSignatureNullThrows() {
        try {
            ops.parseSignature(null);
            fail("expected IllegalArgumentException for null input");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testParseSignatureWhitespaceTolerant() {
        ParsedSignature p = ops.parseSignature("  getUser ( Long  )  ");
        assertEquals("getUser", p.name());
        assertEquals(1, p.parameterTypeNames().size());
        assertEquals("Long", p.parameterTypeNames().get(0));
    }

    public void testParseSignatureMultipleParams() {
        ParsedSignature p = ops.parseSignature("add(int, int, String)");
        assertEquals("add", p.name());
        assertEquals(3, p.parameterTypeNames().size());
        assertEquals("int", p.parameterTypeNames().get(0));
        assertEquals("int", p.parameterTypeNames().get(1));
        assertEquals("String", p.parameterTypeNames().get(2));
    }

    // ----------------------------------------------------------------
    // resolveType
    // ----------------------------------------------------------------

    public void testResolveTypeCaches() {
        Object t1 = ops.resolveType("Long", diagram);
        Object t2 = ops.resolveType("Long", diagram);
        assertNotNull("first resolveType must succeed", t1);
        assertSame("resolveType must cache across calls", t1, t2);
    }

    // ----------------------------------------------------------------
    // addMethod
    // ----------------------------------------------------------------

    public void testAddMethodIsIdempotent() {
        Object op1 = ops.addMethod(testClass, "getUser(Long)");
        Object op2 = ops.addMethod(testClass, "getUser(Long)");
        assertNotNull("first addMethod must succeed", op1);
        assertNotNull("second addMethod must also return", op2);
        assertSame("idempotent: second call returns same operation",
                op1, op2);
    }

    public void testAddMethodSetsName() {
        Object op = ops.addMethod(testClass, "getUser(Long)");
        assertNotNull(op);
        assertEquals("getUser", Model.getFacade().getName(op));
    }

    public void testAddMethodAddsOneInputParameter() {
        Object op = ops.addMethod(testClass, "setName(String)");
        assertNotNull(op);
        assertEquals("one input parameter",
                1, countInputParams(op));
    }

    public void testAddMethodNoInputParameters() {
        Object op = ops.addMethod(testClass, "getNow()");
        assertNotNull(op);
        assertEquals("no input parameters (return param only)",
                0, countInputParams(op));
    }

    public void testAddMethodBadSignatureReturnsNull() {
        Object op = ops.addMethod(testClass, "");
        assertNull("empty signature must return null (not throw)", op);
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.common;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Shared JUnit 3 setUp / tearDown for all AI-REST handler tests.
 *
 * <p>Boilerplate that was previously copy-pasted into ~25 test
 * classes (one per handler). The static block initialises the
 * ArgoUML notation providers exactly once per JVM, {@link #setUp}
 * builds a fresh empty project with a single diagram, and
 * {@link #tearDown} removes the project. Subclasses supply the
 * concrete diagram via {@link #createDiagram}.</p>
 *
 * <p>Test methods get a clean ArgoUML model with one named diagram
 * (the constant {@link #DIAGRAM}) to mutate. {@link #pp()} and
 * {@link #ppWithId(String, String)} produce ready-to-use path
 * parameter maps.</p>
 */
public abstract class AbstractDiagramHandlerTestCase extends TestCase {

    /** Default diagram name. Tests that need a different name can
     *  shadow this in a subclass field. */
    protected static final String DIAGRAM = "Test";

    static {
        try {
            new InitNotation().init();
            new InitNotationUml().init();
            new InitNotationJava().init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Project project;

    @Override
    @SuppressWarnings("deprecation")
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        Object ns = project.getUserDefinedModelList().iterator().next();
        ArgoDiagram d = createDiagram(DIAGRAM, ns);
        project.addDiagram(d);
    }

    /**
     * Subclass hook: produce the right ArgoDiagram subclass for
     * the kind under test. Most tests just do
     * {@code new UMLUseCaseDiagram(name, ns)} or
     * {@code new UMLClassDiagram(name, ns)}.
     */
    protected abstract ArgoDiagram createDiagram(String name, Object namespace);

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
     * Build a path-parameter map with only the {@code "d"} key
     * (the diagram name). Sub-handler tests can use this when the
     * endpoint does not require an entity id in the path.
     */
    protected Map<String, String> pp() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("d", DIAGRAM);
        return m;
    }

    /**
     * Build a path-parameter map with {@code "d"} plus one extra
     * id key (e.g. {@code "a"} for actors, {@code "u"} for use
     * cases, {@code "c"} for classes, {@code "id"} for
     * relationships).
     */
    protected Map<String, String> ppWithId(String idName, String id) {
        Map<String, String> m = pp();
        m.put(idName, id);
        return m;
    }
}

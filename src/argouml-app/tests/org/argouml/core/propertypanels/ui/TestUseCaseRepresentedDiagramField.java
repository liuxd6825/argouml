/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    test (2026-07-12)
 *****************************************************************************
 */
// Copyright (c) 1996-2009 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies.  This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free.  The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason.  IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE FOR ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.core.propertypanels.ui;

import java.lang.reflect.Method;

import javax.swing.table.AbstractTableModel;

import junit.framework.TestCase;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.diagram.DiagramFactory;
import org.argouml.uml.diagram.use_case.ui.UMLUseCaseDiagram;
import org.argouml.util.ItemUID;

/**
 * Tests for the ReadableBehavior of {@link UseCaseRepresentedDiagramField}.
 *
 * <p>Three regression checks:</p>
 * <ol>
 *   <li>When the link list is empty, the table model is empty.</li>
 *   <li>When the link list contains a valid diagram's ItemUID, the
 *       first row contains the diagram's user-given name (no
 *       {@code (missing diagram)}), a localized path (with
 *       {@code untitledModel} translated), and a localized type
 *       label (e.g. "Use Case Diagram").</li>
 *   <li>When the link list contains an unknown UUID, the row falls
 *       back to {@code (missing diagram)} without throwing.</li>
 * </ol>
 */
public class TestUseCaseRepresentedDiagramField extends TestCase {

    private Project project;
    private Object namespace;
    private ArgoDiagram diagA;

    public TestUseCaseRepresentedDiagramField(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        // false = don't add default Class + Use Case diagrams, so the
        // lookup tests are not contaminated by extra diagrams that share
        // the project's namespace UUID.
        project = ProjectManager.getManager().makeEmptyProject(false);
        new InitNotation().init();
        new InitNotationUml().init();
        // The project's default root model is named "untitledModel" by
        // ProjectManager.createDefaultModel — we use it as the diagram's
        // namespace so the path display shows the localized model name.
        namespace = project.getRoots().iterator().next();
    }

    @Override
    public void tearDown() throws Exception {
        if (diagA != null) {
            DiagramFactory.getInstance().removeDiagram(diagA);
            diagA = null;
        }
        // Don't delete the project's root model — it's torn down when
        // the project itself is removed below.
        namespace = null;
        ProjectManager.getManager().removeProject(project);
        super.tearDown();
    }

    /** Build the model-backed rows via the private buildRows. */
    private Object[][] getRowsViaReflection(UseCaseRepresentedDiagramField f) throws Exception {
        Method m = UseCaseRepresentedDiagramField.class
                .getDeclaredMethod("buildRows", Object.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<?> rows = (java.util.List<?>) m.invoke(null, f.getUseCaseForTest());
        Object[][] out = new Object[rows.size()][];
        int i = 0;
        for (Object row : rows) {
            Class<?> rc = row.getClass();
            Method getN = rc.getDeclaredMethod("getName");
            Method getP = rc.getDeclaredMethod("getPath");
            Method getT = rc.getDeclaredMethod("getType");
            out[i++] = new Object[] {
                getN.invoke(row), getP.invoke(row), getT.invoke(row)
            };
        }
        return out;
    }

    /**
     * Empty link list renders as zero rows.
     */
    public void testEmptyLinkListProducesZeroRows() throws Exception {
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            UseCaseRepresentedDiagramField f = new UseCaseRepresentedDiagramField();
            f.setTarget(useCase);
            Object[][] rows = getRowsViaReflection(f);
            assertEquals(0, rows.length);
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * Linking B's diagram UUID produces a row whose Diagram field is
     * the diagram's user-given name, Path is the localized "/无标题模型"
     * (or "/untitledModel" in en), and Type is the localized "Use Case Diagram".
     *
     * <p>This is the bug-fix regression: previously lookup used the
     * namespace UUID (which every diagram in the project shares), so
     * rows fell back to "(missing diagram)" + the raw UUID string.
     * Storing the per-diagram ItemUID + matching by ItemUID resolves
     * the actual diagram.</p>
     */
    public void testLinkedDiagramProducesHumanReadableRow() throws Exception {
        diagA = new UMLUseCaseDiagram("Login Flow", namespace);
        if (diagA.getItemUID() == null) {
            diagA.setItemUID(new ItemUID());
        }
        project.addDiagram(diagA);

        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            UseCaseOperations.addRepresentedDiagram(
                    useCase, diagA.getItemUID().toString());
            UseCaseRepresentedDiagramField f = new UseCaseRepresentedDiagramField();
            f.setTarget(useCase);

            Object[][] rows = getRowsViaReflection(f);
            assertEquals("One link -> one row", 1, rows.length);

            String diagramField = (String) rows[0][0];
            String pathField = (String) rows[0][1];
            String typeField = (String) rows[0][2];

            assertEquals("Diagram field must show real diagram name, not"
                            + " '(missing diagram)'",
                    "Login Flow", diagramField);

            // Path must NOT contain a raw UUID and must include the
            // localized untitled model name (or the English fallback).
            assertFalse("Path must not be a raw UUID: " + pathField,
                    pathField.contains(":"));
            assertTrue("Path must include the model name, got: " + pathField,
                    pathField.contains("untitledModel")
                            || pathField.toLowerCase().contains("untitled")
                            || pathField.contains("无标题模型"));

            assertEquals("Type must be localized Use Case Diagram label",
                    org.argouml.i18n.Translator.localize("label.usecase-diagram"),
                    typeField);
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * Legacy data stored as the namespace UUID must still resolve
     * (fall back to namespace match for backwards compatibility with
     * .zargo files persisted before the ItemUID fix).
     */
    public void testLegacyNamespaceUuidStillResolves() throws Exception {
        diagA = new UMLUseCaseDiagram("Legacy Diagram", namespace);
        project.addDiagram(diagA);

        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            // Simulate a pre-fix stored link: the *namespace UUID*
            // (shared by every diagram in the project).
            String nsUuid = (String) Model.getFacade().getUUID(
                    diagA.getNamespace());
            UseCaseOperations.addRepresentedDiagram(useCase, nsUuid);
            UseCaseRepresentedDiagramField f = new UseCaseRepresentedDiagramField();
            f.setTarget(useCase);

            Object[][] rows = getRowsViaReflection(f);
            assertEquals("Legacy namespace UUID must still find the diagram",
                    1, rows.length);
            String diagramField = (String) rows[0][0];
            assertEquals("Legacy link must show real diagram name",
                    "Legacy Diagram", diagramField);
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }

    /**
     * Unknown UUIDs render gracefully with the (missing diagram) fallback
     * text and never throw — including when the link list contains an
     * entry that no longer resolves to any diagram.
     */
    public void testUnknownUuidFallsBackToMissingDiagram() throws Exception {
        Object useCase = Model.getUseCasesFactory().createUseCase();
        try {
            UseCaseOperations.addRepresentedDiagram(useCase,
                    "totally-not-a-real-uuid");
            UseCaseRepresentedDiagramField f = new UseCaseRepresentedDiagramField();
            f.setTarget(useCase);

            // Should not throw.
            Object[][] rows = getRowsViaReflection(f);
            assertEquals(1, rows.length);
            assertEquals("(missing diagram)", (String) rows[0][0]);
        } finally {
            Model.getUmlFactory().delete(useCase);
        }
    }
}
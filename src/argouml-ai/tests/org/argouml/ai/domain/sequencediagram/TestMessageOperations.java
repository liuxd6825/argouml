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

import junit.framework.TestCase;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.java.InitNotationJava;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.sequence2.diagram.UMLSequenceDiagram;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * Tests for {@link MessageOperations}. Two lifelines are pre-built
 * and wired into the diagram so the message factory can build an
 * {@code MAssociationRole} between them.
 *
 * <p>Tests bypass the graph-model listener chain because the
 * sequence diagram's {@code SequenceDiagramRenderer} NPEs when a
 * node is added via the standard {@code gm.addNode(elem)} path in
 * headless mode — see {@link ClassifierRoleOperations} Javadoc.</p>
 */
public class TestMessageOperations extends TestCase {

    private static final String DIAGRAM = "TestSDMsg";

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
    private MessageOperations ops;
    private Object customerLife;
    private Object orderLife;

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

        MutableGraphModel gm =
                (MutableGraphModel) diagram.getGraphModel();

        customerLife = Model.getCollaborationsFactory()
                .buildLifeline(collaboration);
        Model.getCoreHelper().setName(customerLife, "Customer");
        gm.getNodes().add(customerLife);

        orderLife = Model.getCollaborationsFactory()
                .buildLifeline(collaboration);
        Model.getCoreHelper().setName(orderLife, "Order");
        gm.getNodes().add(orderLife);

        ops = new MessageOperations();
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

    public void testBuildSyncMessage() {
        Object msg = ops.build(diagram, customerLife, orderLife,
                "placeOrder", "placeOrder()", "syncCall", false);
        assertNotNull("build must return a message", msg);
        assertTrue("must be Message: " + msg.getClass(),
                Model.getFacade().isAMessage(msg));
        assertEquals("placeOrder", Model.getFacade().getName(msg));
    }

    public void testBuildRejectsNullEnds() {
        try {
            ops.build(diagram, null, orderLife, "m", "", "syncCall", false);
            fail("expected IllegalArgumentException for null from");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testFindAllReturnsNonEmpty() {
        ops.build(diagram, customerLife, orderLife,
                "m1", "", "syncCall", false);
        assertTrue("findAll must include the new message",
                ops.findAll(diagram).size() >= 1);
    }

    public void testDeleteRemovesMessage() {
        Object msg = ops.build(diagram, customerLife, orderLife,
                "toDelete", "", "syncCall", false);
        String uuid = Model.getFacade().getUUID(msg);
        ops.delete(diagram, msg);
        assertNull("findByUuid must return null after delete",
                ops.findByUuid(diagram, uuid));
    }
}

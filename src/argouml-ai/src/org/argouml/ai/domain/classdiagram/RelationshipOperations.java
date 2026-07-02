/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.classdiagram;

import java.util.Collection;

import org.argouml.model.CoreFactory;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.GraphModel;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * Binary UML relationship creation / mutation against the model
 * layer and the diagram's graph model. Handles the three class-diagram
 * relationship kinds: <em>association</em> (with multiplicity and
 * role-name endpoints), <em>generalization</em> (parent / child), and
 * <em>dependency</em> (client / supplier). Pure functions on a
 * diagram; the class has no mutable state and depends only on the
 * ArgoUML model facade and the GEF graph-model API.
 *
 * <p><b>Architectural boundary.</b> Same as {@link ClassOperations}:
 * no HTTP, no AI, no inbound / outbound adapter layer, no Swing UI,
 * no {@code Fig} placement. The diagram is required because
 * relationships are registered on the diagram's graph model as edges;
 * the model element alone is not enough.
 *
 * <p>Endpoints are resolved by name-based lookup through
 * {@link ClassOperations#findByName} on the diagram's nodes; callers
 * that already hold the model elements can pass them directly (the
 * find*Between methods accept model elements).
 */
public final class RelationshipOperations {

    private RelationshipOperations() {
    }

    /**
     * Build a binary association between {@code classA} and
     * {@code classB} on the diagram. Both classes must already exist
     * on the diagram's graph model; the multiplicity strings
     * {@code multA} / {@code multB} and the role names
     * {@code labelA} / {@code labelB} are applied to the
     * corresponding endpoint (endpoint 0 belongs to {@code classA},
     * endpoint 1 belongs to {@code classB}, matching the
     * {@code c1, c2} order passed to
     * {@link CoreFactory#buildAssociation(Object, Object)}). Any of
     * those four strings may be null or empty, in which case that
     * attribute is left at its UML default.
     *
     * @param diagram the diagram on which to register the edge.
     * @param classA  one endpoint of the association.
     * @param classB  the other endpoint of the association.
     * @param multA   multiplicity at the {@code classA} end, e.g.
     *                {@code "1"} or {@code "0..*"}; null/empty to
     *                skip.
     * @param multB   multiplicity at the {@code classB} end; null/empty
     *                to skip.
     * @param labelA  role name at the {@code classA} end; null/empty
     *                to skip.
     * @param labelB  role name at the {@code classB} end; null/empty
     *                to skip.
     * @return the newly created association model element.
     */
    public static Object buildAssociation(ArgoDiagram diagram,
                                          Object classA, Object classB,
                                          String multA, String multB,
                                          String labelA, String labelB) {
        Object assoc = Model.getCoreFactory().buildAssociation(classA, classB);
        applyMultiplicities(assoc, multA, multB);
        applyRoleNames(assoc, labelA, labelB);
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        if (gm.canAddEdge(assoc)) {
            gm.addEdge(assoc);
        }
        return assoc;
    }

    /**
     * Build a generalization: {@code child} inherits from
     * {@code parent}. Both classes must already exist on the
     * diagram.
     */
    public static Object buildGeneralization(ArgoDiagram diagram,
                                            Object child, Object parent) {
        Object gen =
                Model.getCoreFactory().buildGeneralization(child, parent);
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        if (gm.canAddEdge(gen)) {
            gm.addEdge(gen);
        }
        return gen;
    }

    /**
     * Build a dependency: {@code client} depends on
     * {@code supplier}. Both classes must already exist on the
     * diagram.
     */
    public static Object buildDependency(ArgoDiagram diagram,
                                         Object client, Object supplier) {
        Object dep =
                Model.getCoreFactory().buildDependency(client, supplier);
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        if (gm.canAddEdge(dep)) {
            gm.addEdge(dep);
        }
        return dep;
    }

    /**
     * Find the association on the diagram whose endpoints are
     * exactly {@code classA} and {@code classB} (in either order).
     * Only {@link Facade#isAAssociation} edges are considered.
     *
     * @return the matching association, or {@code null} if no
     *         association connects those two classes on the diagram.
     */
    public static Object findAssociationBetween(ArgoDiagram diagram,
                                                Object classA, Object classB) {
        Facade facade = Model.getFacade();
        for (Object edge : edges(diagram)) {
            if (!facade.isAAssociation(edge)) {
                continue;
            }
            Collection conns = facade.getConnections(edge);
            if (endpointsMatch(conns, classA, classB)) {
                return edge;
            }
        }
        return null;
    }

    /**
     * Find the generalization on the diagram with {@code child} as
     * the specific end and {@code parent} as the general end.
     * Direction matters: passing the parent first and the child
     * second yields {@code null}.
     */
    public static Object findGeneralizationBetween(ArgoDiagram diagram,
                                                   Object child, Object parent) {
        Facade facade = Model.getFacade();
        for (Object edge : edges(diagram)) {
            if (!facade.isAGeneralization(edge)) {
                continue;
            }
            if (child.equals(facade.getSpecific(edge))
                    && parent.equals(facade.getGeneral(edge))) {
                return edge;
            }
        }
        return null;
    }

    /**
     * Find the dependency on the diagram where {@code client}
     * depends on {@code supplier}. Direction matters.
     *
     * @return the matching dependency, or {@code null} if no such
     *         dependency exists on the diagram.
     */
    public static Object findDependencyBetween(ArgoDiagram diagram,
                                               Object client,
                                               Object supplier) {
        if (client == null || supplier == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object edge : edges(diagram)) {
            if (!facade.isADependency(edge)) {
                continue;
            }
            // Dependency is directional: client depends on supplier.
            // Per UML/Facade contract: ask the dependency edge for its
            // clients and suppliers, then check membership.
            Collection clients = facade.getClients(edge);
            if (clients != null && clients.contains(client)) {
                Collection suppliers = facade.getSuppliers(edge);
                if (suppliers != null && suppliers.contains(supplier)) {
                    return edge;
                }
            }
        }
        return null;
    }

    /**
     * Remove the given relationship edge from the diagram's graph
     * model and (best-effort) delete the underlying model element.
     * Errors during the model delete are swallowed, mirroring
     * {@link ClassOperations#delete}.
     *
     * @param diagram      the diagram the edge is registered on.
     * @param relationship the relationship model element; a
     *                     {@code null} value is a no-op.
     */
    public static void delete(ArgoDiagram diagram, Object relationship) {
        if (relationship == null) {
            return;
        }
        MutableGraphModel gm = (MutableGraphModel) diagram.getGraphModel();
        if (gm.containsEdge(relationship)) {
            gm.removeEdge(relationship);
        }
        try {
            Model.getUmlFactory().delete(relationship);
        } catch (RuntimeException ignored) {
            // model may refuse
        }
    }

    /**
     * Return the edges of the diagram's graph model as an iterable.
     */
    private static Iterable<Object> edges(ArgoDiagram diagram) {
        GraphModel gm = diagram.getGraphModel();
        @SuppressWarnings("unchecked")
        Collection<Object> edges = (Collection<Object>) gm.getEdges();
        return edges;
    }

    /**
     * Return the two association-end multiplicities on a
     * binary association in the ({@code classA}, {@code classB})
     * endpoint order. Endpoints with null/empty inputs are left at
     * their UML default.
     */
    private static void applyMultiplicities(
            Object assoc, String multA, String multB) {
        if (multA == null && multB == null) {
            return;
        }
        Object[] endArr = endArray(assoc);
        if (endArr == null) {
            return;
        }
        CoreHelper helper = Model.getCoreHelper();
        if (multA != null) {
            helper.setMultiplicity(endArr[0], multA);
        }
        if (multB != null) {
            helper.setMultiplicity(endArr[1], multB);
        }
    }

    /**
     * Return the two association-end role names on a binary
     * association in the ({@code classA}, {@code classB}) endpoint
     * order. Endpoint AssociationEnds are named via
     * {@link CoreHelper#setName}; the rendered figure picks up
     * that name as the role-name label.
     */
    private static void applyRoleNames(
            Object assoc, String labelA, String labelB) {
        if (labelA == null && labelB == null) {
            return;
        }
        Object[] endArr = endArray(assoc);
        if (endArr == null) {
            return;
        }
        CoreHelper helper = Model.getCoreHelper();
        if (labelA != null && labelA.length() > 0) {
            helper.setName(endArr[0], labelA);
        }
        if (labelB != null && labelB.length() > 0) {
            helper.setName(endArr[1], labelB);
        }
    }

    /**
     * Return the two association ends as an array, or {@code null}
     * if the association does not have at least two connections.
     * Caller treats {@code null} as "nothing to do".
     */
    private static Object[] endArray(Object assoc) {
        Collection ends = Model.getFacade().getConnections(assoc);
        if (ends == null || ends.size() < 2) {
            return null;
        }
        return ends.toArray();
    }

    /**
     * Return {@code true} if the two-element collection
     * {@code conns} contains exactly the two endpoint classifiers
     * {@code c1} and {@code c2} (order-insensitive).
     */
    private static boolean endpointsMatch(Collection conns,
                                          Object c1, Object c2) {
        if (conns == null || conns.size() != 2) {
            return false;
        }
        Object[] arr = conns.toArray();
        Object t0 = Model.getFacade().getType(arr[0]);
        Object t1 = Model.getFacade().getType(arr[1]);
        return (c1.equals(t0) && c2.equals(t1))
                || (c1.equals(t1) && c2.equals(t0));
    }
}

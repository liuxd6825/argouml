/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai;

import java.util.Collections;
import java.util.List;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.DiagramServices;
import org.argouml.ai.inbound.rest.classdiagram.handlers.CreateClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.CreateInterfaceHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.DeleteClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.GetClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.ListClassesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.UpdateClassHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.AddAttributeHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.DeleteAttributeHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.GetAttributeHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.attribute.ListAttributesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.AddOperationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.DeleteOperationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.GetOperationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.operation.ListOperationsHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.AddAssociationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.AddDependencyHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.AddGeneralizationHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.DeleteRelationshipHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.ListAssociationsHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.ListDependenciesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.relationship.ListGeneralizationsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.CreateActorHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.DeleteActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.DeleteActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.GetActorByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.ListActorsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.actor.MoveActorHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateAssociationHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateExtendHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.CreateIncludeHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteAssociationHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteExtendHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.DeleteIncludeHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship.ListUseCaseAssociationsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.CreateUseCaseHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.DeleteUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.DeleteUseCaseByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByNameHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.GetUseCaseByUuidHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.ListUseCasesHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.MoveUseCaseHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.GetDiagramStateHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.ListLayerFigsHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.CreateLifelineHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.DeleteLifelineByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.DeleteLifelineByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.GetLifelineByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.GetLifelineByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline.ListLifelinesHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.CreateMessageHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.DeleteMessageByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.GetMessageByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.message.ListMessagesHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.CreateRoleHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.DeleteRoleByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.DeleteRoleByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.GetRoleByNameHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.GetRoleByUuidHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.ListRolesHandler;
import org.argouml.ai.inbound.rest.sequencediagram.handlers.role.MoveRoleHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.layout.CleanupDatatypesHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.layout.GetLayoutHandler;
import org.argouml.ai.inbound.rest.classdiagram.handlers.layout.PostLayoutHandler;
import org.argouml.ai.inbound.rest.common.Dispatcher;
import org.argouml.ai.inbound.rest.common.Method;
import org.argouml.ai.inbound.rest.common.Router;
import org.argouml.ai.inbound.rest.common.handlers.common.CreateDiagramHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.CreatePackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.DeletePackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.GetDiagramHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.HealthHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.GetPackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListDiagramsHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListPackageClassesHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.ListPackagesHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.MoveClassToPackageHandler;
import org.argouml.ai.inbound.rest.common.handlers.common.SnapshotHandler;
import org.argouml.ai.infrastructure.config.HttpServerSettingsTab;
import org.argouml.ai.infrastructure.config.ServerConfig;
import org.argouml.ai.infrastructure.config.ServerConfigStore;
import org.argouml.ai.infrastructure.http.NanoHttpAdapter;
import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.application.api.InitSubsystem;

import fi.iki.elonen.NanoHTTPD;

/**
 * Initializer for the embedded HTTP server subsystem.
 *
 * <p>Wired into the ArgoUML startup chain from
 * {@code org.argouml.application.Main#initializeSubsystems()} (the
 * same spot as {@link InitAiSubsystem}, which precedes it).</p>
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #init()} reads {@link ServerConfig} from
 *       {@link ServerConfigStore#defaultFile()};</li>
 *   <li>if {@code cfg.enabled == false}, returns immediately - no
 *       socket is opened, no router is built;</li>
 *   <li>otherwise builds a {@link Router} with every REST endpoint
 *       the MVP ships (Tasks 20-25), wraps it in a {@link Dispatcher},
 *       binds a {@link NanoHTTPD} to {@code cfg.bind:cfg.port}, and
 *       delegates each incoming request to {@code dispatcher.serve};</li>
 *   <li>{@link #getSettingsTabs()} returns a singleton
 *       {@link HttpServerSettingsTab} so the user can toggle
 *       {@code enabled} / change the port from the standard settings
 *       dialog.</li>
 * </ol>
 *
 * <p>Encoding: ASCII. The settings tab routes its user-facing strings
 * through {@code org.argouml.i18n.Translator}; this class adds no
 * labels of its own.</p>
 */
public class InitHttpServerSubsystem implements InitSubsystem {

    private NanoHttpAdapter adapter;

    @Override
    public void init() {
        ServerConfig cfg = new ServerConfigStore(
                ServerConfigStore.defaultFile()).load();
        if (!cfg.enabled) {
            return;
        }
        Router router = buildRouter();
        final Dispatcher dispatcher = new Dispatcher(router);
        NanoHTTPD server = new NanoHTTPD(cfg.bind, cfg.port) {
            @Override
            public Response serve(IHTTPSession session) {
                return dispatcher.serve(session);
            }
        };
        adapter = new NanoHttpAdapter(server);
        adapter.start();
        System.out.println("argouml-ai HTTP server bound to "
                + cfg.bind + ":" + adapter.boundPort()
                + " (enabled=" + cfg.enabled
                + ", timeoutSec=" + cfg.timeoutSec
                + ", maxBodyBytes=" + cfg.maxBodyBytes + ")");
    }

    @Override
    public List<AbstractArgoJPanel> getDetailsTabs() {
        return Collections.<AbstractArgoJPanel>emptyList();
    }

    @Override
    public List<GUISettingsTabInterface> getSettingsTabs() {
        return Collections.<GUISettingsTabInterface>singletonList(
                new HttpServerSettingsTab());
    }

    @Override
    public List<GUISettingsTabInterface> getProjectSettingsTabs() {
        return Collections.<GUISettingsTabInterface>emptyList();
    }

    /**
     * Build the {@link Router} with every MVP endpoint wired to a
     * constructor-injected handler. Package-private so a smoke test
     * can introspect the route table without actually binding a
     * socket.
     */
    Router buildRouter() {
        Router router = new Router();

        // The class-diagram service is the single integration point
        // for every classdiagram/* handler AND for the project-level
        // CreateDiagramHandler. Future diagram kinds (sequence, state,
        // etc.) will be looked up here by ModelKind and passed to their
        // own dedicated handler factories; diagram-create may be split
        // off into a dedicated DiagramManagementService at that point.
        ClassDiagramService svc = DiagramServices.classSvc();
        org.argouml.ai.application.usecasediagram.UseCaseDiagramService ucSvc =
                DiagramServices.useCaseSvc();
        org.argouml.ai.application.sequencediagram.SequenceDiagramService seqSvc =
                DiagramServices.sequenceSvc();

        // common (project-scoped) routes
        router.add(Method.GET, "/health", new HealthHandler());
        router.add(Method.GET, "/project/diagrams",
                new ListDiagramsHandler());
        router.add(Method.POST, "/project/diagrams",
                new CreateDiagramHandler(svc));
        // Package management
        router.add(Method.GET, "/project/packages",
                new ListPackagesHandler(svc));
        router.add(Method.POST, "/project/packages",
                new CreatePackageHandler(svc));
        router.add(Method.GET, "/project/packages/{name}",
                new GetPackageHandler(svc));
        router.add(Method.DELETE, "/project/packages/{name}",
                new DeletePackageHandler(svc));
        router.add(Method.POST,
                "/project/packages/{name}/classes/{c}",
                new MoveClassToPackageHandler(svc));
        router.add(Method.GET, "/project/packages/{name}/classes",
                new ListPackageClassesHandler(svc));
        router.add(Method.GET, "/project/diagrams/{d}",
                new GetDiagramHandler());
        router.add(Method.GET, "/project/diagrams/{d}/snapshot",
                new SnapshotHandler());

        // Classes & interfaces
        router.add(Method.GET, "/d/{d}/classes",
                new ListClassesHandler(svc));
        router.add(Method.GET, "/d/{d}/classes/{c}",
                new GetClassHandler(svc));
        router.add(Method.POST, "/d/{d}/classes",
                new CreateClassHandler(svc));
        router.add(Method.PUT, "/d/{d}/classes/{c}",
                new UpdateClassHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}",
                new DeleteClassHandler(svc));
        router.add(Method.POST, "/d/{d}/interfaces",
                new CreateInterfaceHandler(svc));

        // Attributes
        router.add(Method.POST, "/d/{d}/classes/{c}/attributes",
                new AddAttributeHandler(svc));
        router.add(Method.GET, "/d/{d}/classes/{c}/attributes",
                new ListAttributesHandler(svc));
        router.add(Method.GET, "/d/{d}/classes/{c}/attributes/{a}",
                new GetAttributeHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}/attributes/{a}",
                new DeleteAttributeHandler(svc));

        // Operations
        router.add(Method.POST, "/d/{d}/classes/{c}/operations",
                new AddOperationHandler(svc));
        router.add(Method.GET, "/d/{d}/classes/{c}/operations",
                new ListOperationsHandler(svc));
        router.add(Method.GET, "/d/{d}/classes/{c}/operations/{op}",
                new GetOperationHandler(svc));
        router.add(Method.DELETE, "/d/{d}/classes/{c}/operations/{op}",
                new DeleteOperationHandler(svc));

        // Relationships
        router.add(Method.GET, "/d/{d}/associations",
                new ListAssociationsHandler(svc));
        router.add(Method.POST, "/d/{d}/associations",
                new AddAssociationHandler(svc));
        router.add(Method.GET, "/d/{d}/generalizations",
                new ListGeneralizationsHandler(svc));
        router.add(Method.POST, "/d/{d}/generalizations",
                new AddGeneralizationHandler(svc));
        router.add(Method.GET, "/d/{d}/dependencies",
                new ListDependenciesHandler(svc));
        router.add(Method.POST, "/d/{d}/dependencies",
                new AddDependencyHandler(svc));
        router.add(Method.DELETE, "/d/{d}/relationships/{id}",
                new DeleteRelationshipHandler(svc));

        // Layout
        router.add(Method.GET, "/d/{d}/layout",
                new GetLayoutHandler(svc));
        router.add(Method.POST, "/d/{d}/layout",
                new PostLayoutHandler(svc));

        // Maintenance: dedupe DataType model elements accumulated by
        // older attribute-add paths. Project-wide, not diagram-scoped.
        router.add(Method.POST, "/project/cleanup-datatypes",
                new CleanupDatatypesHandler(svc));

        // Use-case diagram API (entity-based; uuid is the stable id)
        // Actor CRUD
        router.add(Method.POST, "/d/{d}/usecasediagram/actors",
                new CreateActorHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/actors",
                new ListActorsHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/actors/by-name/{a}",
                new GetActorByNameHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/actors/{uuid}",
                new GetActorByUuidHandler(ucSvc));
        router.add(Method.PUT, "/d/{d}/usecasediagram/actors/by-name/{a}",
                new MoveActorHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/actors/by-name/{a}",
                new DeleteActorByNameHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/actors/{uuid}",
                new DeleteActorByUuidHandler(ucSvc));
        // UseCase CRUD
        router.add(Method.POST, "/d/{d}/usecasediagram/usecases",
                new CreateUseCaseHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/usecases",
                new ListUseCasesHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/usecases/by-name/{u}",
                new GetUseCaseByNameHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/usecases/{uuid}",
                new GetUseCaseByUuidHandler(ucSvc));
        router.add(Method.PUT, "/d/{d}/usecasediagram/usecases/by-name/{u}",
                new MoveUseCaseHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/usecases/by-name/{u}",
                new DeleteUseCaseByNameHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/usecases/{uuid}",
                new DeleteUseCaseByUuidHandler(ucSvc));
        // Relationships
        router.add(Method.POST, "/d/{d}/usecasediagram/associations",
                new CreateAssociationHandler(ucSvc));
        router.add(Method.GET, "/d/{d}/usecasediagram/associations",
                new ListUseCaseAssociationsHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/associations/{id}",
                new DeleteAssociationHandler(ucSvc));
        router.add(Method.POST, "/d/{d}/usecasediagram/includes",
                new CreateIncludeHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/includes/{id}",
                new DeleteIncludeHandler(ucSvc));
        router.add(Method.POST, "/d/{d}/usecasediagram/extends",
                new CreateExtendHandler(ucSvc));
        router.add(Method.DELETE, "/d/{d}/usecasediagram/extends/{id}",
                new DeleteExtendHandler(ucSvc));

        // Sequence diagram API (entity-based; uuid is the stable id)
        // Diagram is created via POST /project/diagrams with kind:"sequencediagram" (existing endpoint)
        // ClassifierRole CRUD
        router.add(Method.POST, "/d/{d}/sequencediagram/roles",
                new CreateRoleHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/roles",
                new ListRolesHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/roles/by-name/{n}",
                new GetRoleByNameHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/roles/{uuid}",
                new GetRoleByUuidHandler(seqSvc));
        router.add(Method.PUT, "/d/{d}/sequencediagram/roles/by-name/{n}",
                new MoveRoleHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/roles/by-name/{n}",
                new DeleteRoleByNameHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/roles/{uuid}",
                new DeleteRoleByUuidHandler(seqSvc));
        // Lifeline CRUD
        router.add(Method.POST, "/d/{d}/sequencediagram/lifelines",
                new CreateLifelineHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/lifelines",
                new ListLifelinesHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/lifelines/by-name/{n}",
                new GetLifelineByNameHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/lifelines/{uuid}",
                new GetLifelineByUuidHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/lifelines/by-name/{n}",
                new DeleteLifelineByNameHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/lifelines/{uuid}",
                new DeleteLifelineByUuidHandler(seqSvc));
        // Message CRUD
        router.add(Method.POST, "/d/{d}/sequencediagram/messages",
                new CreateMessageHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/messages",
                new ListMessagesHandler(seqSvc));
        router.add(Method.GET, "/d/{d}/sequencediagram/messages/{uuid}",
                new GetMessageByUuidHandler(seqSvc));
        router.add(Method.DELETE, "/d/{d}/sequencediagram/messages/{uuid}",
                new DeleteMessageByUuidHandler(seqSvc));

        // Diagnostic — fig-count snapshot. Lets API clients verify
        // that a mutation actually created a Fig in the layer (visible
        // to the user), not just bookkeeping in the graph model.
        router.add(Method.GET, "/d/{d}/sequencediagram/figs",
                new GetDiagramStateHandler(seqSvc));

        // Diagnostic — list every fig in the layer with its class name
        // and owner uuid. Used during the diagram-drop-bug investigation.
        router.add(Method.GET, "/d/{d}/sequencediagram/figs/dump",
                new ListLayerFigsHandler(seqSvc));

        return router;
    }

    /**
     * @return the live adapter, or {@code null} when the subsystem
     *     has not yet started or the user disabled the server in
     *     {@link ServerConfig}. Exposed so integration tests can
     *     assert that {@link #init()} actually opened a socket
     *     (or did not).
     */
    NanoHttpAdapter getAdapter() {
        return adapter;
    }
}
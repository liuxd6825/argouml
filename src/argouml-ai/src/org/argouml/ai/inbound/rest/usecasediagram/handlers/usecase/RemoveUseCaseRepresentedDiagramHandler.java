package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;

public final class RemoveUseCaseRepresentedDiagramHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public RemoveUseCaseRepresentedDiagramHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null ? null : pathParams.get("u");
        String uuid = pathParams == null ? null : pathParams.get("uuid");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME", "UseCase name required"));
        }
        if (uuid == null || uuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME", "diagramUuid required"));
        }
        boolean removed = svc.removeUseCaseRepresentedDiagram(diagram, name, uuid);
        if (!removed) {
            return ResponseEnvelope.json(404, JsonError.of("UUID_NOT_FOUND",
                    "UUID not in link list"));
        }
        return ResponseEnvelope.json(204, "");
    }
}
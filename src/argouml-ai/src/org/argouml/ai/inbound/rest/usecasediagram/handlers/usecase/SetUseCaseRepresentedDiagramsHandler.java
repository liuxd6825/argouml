package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseUseCaseEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

public final class SetUseCaseRepresentedDiagramsHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public SetUseCaseRepresentedDiagramsHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null ? null : pathParams.get("u");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase name required"));
        }
        Map<String, Object> json;
        try { json = JsonBodyReader.readMap(body); }
        catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY", ex.getMessage()));
        }
        Object raw = json == null ? null : json.get("diagramUuids");
        List<String> uuids = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object o : (List) raw) {
                if (o != null) uuids.add(String.valueOf(o));
            }
        }
        UsecaseUseCaseEntity v = svc.setUseCaseRepresentedDiagrams(diagram, name, uuids);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}
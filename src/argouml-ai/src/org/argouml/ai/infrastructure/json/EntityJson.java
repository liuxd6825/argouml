/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.infrastructure.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.domain.entity.ActorEntity;
import org.argouml.ai.domain.entity.AssociationEntity;
import org.argouml.ai.domain.entity.ExtendEntity;
import org.argouml.ai.domain.entity.Identified;
import org.argouml.ai.domain.entity.IncludeEntity;
import org.argouml.ai.domain.entity.UseCaseDiagramEntity;
import org.argouml.ai.domain.entity.UseCaseEntity;

/**
 * Serialises {@link Identified} entities (and lists thereof) to
 * JSON-compatible {@code Map<String,Object>} trees, ready for
 * {@link JsonWriter#ok(Object)} to wrap in the standard success
 * envelope.
 *
 * <p>Why a hand-rolled marshaller instead of reflection?</p>
 * <ul>
 *   <li>The entity class hierarchy is small and stable; reflection
 *       would cost more in code than it saves in maintenance.</li>
 *   <li>Wire format is part of the public API contract; an explicit
 *       per-entity writer makes the contract obvious and reviewable.</li>
 *   <li>No third-party JSON library is on the classpath (the
 *       project uses {@link JsonMini}).</li>
 * </ul>
 *
 * <p>Field order is stable: every entity emits {@code uuid, name,
 * kind} first (in that order) so clients can pattern-match
 * confidently.</p>
 */
public final class EntityJson {

    private EntityJson() {
    }

    /** Serialize a single entity. */
    public static Map<String, Object> toMap(Identified e) {
        if (e == null) {
            return null;
        }
        if (e instanceof ActorEntity) {
            return toMap((ActorEntity) e);
        }
        if (e instanceof UseCaseEntity) {
            return toMap((UseCaseEntity) e);
        }
        if (e instanceof AssociationEntity) {
            return toMap((AssociationEntity) e);
        }
        if (e instanceof IncludeEntity) {
            return toMap((IncludeEntity) e);
        }
        if (e instanceof ExtendEntity) {
            return toMap((ExtendEntity) e);
        }
        if (e instanceof UseCaseDiagramEntity) {
            return toMap((UseCaseDiagramEntity) e);
        }
        // Fallback for any future entity type. Best-effort:
        // emit only the Identified contract.
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        return m;
    }

    /** Serialize a list of entities. Null elements become JSON
     *  nulls in the array; an empty list yields {@code []}. */
    public static List<Map<String, Object>> toMapList(
            List<? extends Identified> list) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(
                list == null ? 0 : list.size());
        if (list != null) {
            for (Identified e : list) {
                out.add(toMap(e));
            }
        }
        return out;
    }

    private static Map<String, Object> toMap(ActorEntity e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        m.put("diagramUuid", e.diagramUuid());
        m.put("x", e.x());
        m.put("y", e.y());
        return m;
    }

    private static Map<String, Object> toMap(UseCaseEntity e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        m.put("description", e.description());
        m.put("diagramUuid", e.diagramUuid());
        m.put("x", e.x());
        m.put("y", e.y());
        return m;
    }

    private static Map<String, Object> toMap(AssociationEntity e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        m.put("id", e.id());
        m.put("actorUuid", e.actorUuid());
        m.put("actorName", e.actorName());
        m.put("usecaseUuid", e.usecaseUuid());
        m.put("usecaseName", e.usecaseName());
        m.put("diagramUuid", e.diagramUuid());
        return m;
    }

    private static Map<String, Object> toMap(IncludeEntity e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        m.put("id", e.id());
        m.put("baseUuid", e.baseUuid());
        m.put("baseName", e.baseName());
        m.put("inclusionUuid", e.inclusionUuid());
        m.put("inclusionName", e.inclusionName());
        m.put("diagramUuid", e.diagramUuid());
        return m;
    }

    private static Map<String, Object> toMap(ExtendEntity e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        m.put("id", e.id());
        m.put("baseUuid", e.baseUuid());
        m.put("baseName", e.baseName());
        m.put("extensionUuid", e.extensionUuid());
        m.put("extensionName", e.extensionName());
        m.put("extensionPoint", e.extensionPoint());
        m.put("diagramUuid", e.diagramUuid());
        return m;
    }

    private static Map<String, Object> toMap(UseCaseDiagramEntity e) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("uuid", e.uuid());
        m.put("name", e.name());
        m.put("kind", e.kind());
        m.put("namespace", e.namespace());
        return m;
    }

    /**
     * Best-effort schema check for an ElementEntity map. Returns
     * {@code true} when {@code m} carries {@code uuid, name, kind,
     * diagramUuid, x, y} fields with the expected primitive types.
     * Used by tests to guard the wire contract.
     */
    public static boolean isElementEntityMap(Map<String, Object> m) {
        if (m == null) {
            return false;
        }
        return m.containsKey("uuid")
                && m.containsKey("name")
                && m.containsKey("kind")
                && m.containsKey("diagramUuid")
                && m.containsKey("x")
                && m.containsKey("y")
                && (m.get("x") instanceof Number)
                && (m.get("y") instanceof Number);
    }

    /**
     * Best-effort schema check for a DiagramEntity map. Returns
     * {@code true} when {@code m} carries {@code uuid, name, kind,
     * namespace} fields. Used by tests.
     */
    public static boolean isDiagramEntityMap(Map<String, Object> m) {
        if (m == null) {
            return false;
        }
        return m.containsKey("uuid")
                && m.containsKey("name")
                && m.containsKey("kind")
                && m.containsKey("namespace");
    }
}
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * In-memory cache for the {@code UseCase.representedDiagram} link
 * (now 1:N - a UseCase may link to multiple ArgoDiagrams).
 *
 * <p>See previous design notes - MDR {@code setType(handle, String)}
 * rejects a String type argument, so the model's tagged-value
 * round-trip is best-effort. This cache is the authoritative
 * store for the link set across all consumers (property panel,
 * REST handlers, right-click navigation menu).</p>
 *
 * @author mkl
 */
public final class RepresentedDiagramLinkCache {

    private static final Map<Object, List<String>> CACHE =
            new WeakHashMap<Object, List<String>>();

    private RepresentedDiagramLinkCache() {}

    /** Returns the full link list (immutable copy). Empty list = no links. */
    public static List<String> getAll(Object useCase) {
        if (useCase == null) {
            return Collections.emptyList();
        }
        List<String> list = CACHE.get(useCase);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(list));
    }

    /** Replace all links with {@code uuids}. */
    public static void put(Object useCase, Collection<String> uuids) {
        if (useCase == null) {
            return;
        }
        if (uuids == null || uuids.isEmpty()) {
            CACHE.remove(useCase);
        } else {
            CACHE.put(useCase, new ArrayList<String>(uuids));
        }
    }

    /** Append a single uuid (no-op if already present). */
    public static boolean addUuid(Object useCase, String uuid) {
        if (useCase == null || uuid == null || uuid.isEmpty()) {
            return false;
        }
        List<String> current = CACHE.get(useCase);
        if (current == null) {
            current = new ArrayList<String>();
            CACHE.put(useCase, current);
        }
        if (current.contains(uuid)) {
            return false;
        }
        current.add(uuid);
        return true;
    }

    /** Remove a single uuid. Returns true if removed. */
    public static boolean removeUuid(Object useCase, String uuid) {
        if (useCase == null || uuid == null) {
            return false;
        }
        List<String> current = CACHE.get(useCase);
        if (current == null) {
            return false;
        }
        boolean removed = current.remove(uuid);
        if (current.isEmpty()) {
            CACHE.remove(useCase);
        }
        return removed;
    }

    /** Clear all links for a UseCase. */
    public static void clear(Object useCase) {
        if (useCase == null) {
            return;
        }
        CACHE.remove(useCase);
    }
}

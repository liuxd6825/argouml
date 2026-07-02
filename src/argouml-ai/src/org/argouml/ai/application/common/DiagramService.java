/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.application.common;

import org.argouml.ai.domain.common.ModelKind;

/**
 * Per-kind application-service facade over the model layer. MVP
 * ships only {@code ClassDiagramService} as the implementer; future
 * kinds (use case, sequence, etc.) supply their own.
 *
 * <p>Implementations are stateless beyond a back-reference to the
 * current {@link org.argouml.kernel.Project}; they are looked up by
 * {@link DiagramServiceRegistry}.</p>
 */
public interface DiagramService {

    ModelKind kind();
}

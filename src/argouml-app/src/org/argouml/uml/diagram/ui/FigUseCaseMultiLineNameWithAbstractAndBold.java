/* $Id$
 *******************************************************************************
 * Copyright (c) 2009-2010 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bob Tarling
 *    Michiel van der Wulp
 *******************************************************************************
 */
// $Id$
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

package org.argouml.uml.diagram.ui;

import java.awt.Dimension;
import java.awt.Rectangle;

import org.argouml.uml.diagram.DiagramSettings;
import org.tigris.gef.presentation.FigText;

/**
 * Use-case-specific variant of {@link FigMultiLineNameWithAbstractAndBold}
 * that decouples {@code getMinimumSize().width} from the natural text
 * width.
 *
 * <p>{@link FigMultiLineNameWithAbstractAndBold#getMinimumSize()} returns
 * the multi-line aware size with width = max line width (text-driven).
 * That made the enclosing {@link FigUseCase} refuse to shrink below
 * text width via the corner resize handle, because the layout pass in
 * {@code FigCompartmentBox.setStandardBounds} always clamps the fig
 * width to {@code max(w, minimumSize.width)}.</p>
 *
 * <p>Here we override and force width to {@link FigText#MIN_TEXT_WIDTH}
 * (30 px, GEF hard floor) so the {@code FigUseCase} minimum width is
 * driven by its stereotype / extension-point constraints rather than
 * text length. The user can then drag-resize the ellipse width and
 * the text re-wraps at the new width via the inherited word-wrap
 * mechanism.</p>
 *
 * <p>Height stays multi-line aware via
 * {@link FigText#stuffMinimumSize(Dimension)}.</p>
 *
 * @since 2026-07-11
 */
public class FigUseCaseMultiLineNameWithAbstractAndBold
        extends FigMultiLineNameWithAbstractAndBold {

    /**
     * Construct a use-case-name fig that supports soft word-wrap and
     * width-decoupled minimum sizing (so the parent ellipse can be
     * shrunk by mouse-resize).
     *
     * @param owner owning UML element
     * @param bounds position and size
     * @param settings rendering settings
     * @param expandOnly true if fig should never contract
     */
    public FigUseCaseMultiLineNameWithAbstractAndBold(Object owner,
            Rectangle bounds, DiagramSettings settings, boolean expandOnly) {
        super(owner, bounds, settings, expandOnly);
    }

    /**
     * Width-decoupled minimum size. Width floored at {@code MIN_TEXT_WIDTH}
     * so the enclosing fig can be shrunk to any width by user resize.
     * Height is multi-line aware via {@link FigText#stuffMinimumSize}.
     */
    @Override
    public Dimension getMinimumSize() {
        if (getFont() == null) {
            return new Dimension();
        }
        if (getFontMetrics() == null) {
            return super.getMinimumSize();
        }
        Dimension d = new Dimension();
        stuffMinimumSize(d);
        // Decouple width from text length so the parent FigUseCase
        // can be mouse-resized narrower than the widest line.
        d.width = MIN_TEXT_WIDTH;
        return d;
    }
}
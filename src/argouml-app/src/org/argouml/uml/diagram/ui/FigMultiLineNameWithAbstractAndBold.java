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
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.uml.diagram.ui;

import java.awt.Dimension;
import java.awt.Rectangle;

import org.argouml.uml.diagram.DiagramSettings;
import org.tigris.gef.presentation.FigText;

/**
 * A multi-line variant of {@link FigNameWithAbstractAndBold} that supports
 * <em>soft word-wrap only</em>: a name longer than the Fig width is broken
 * at word boundaries. GEF persists the soft breaks as {@code \r} which
 * {@link FigText#paint(Graphics) paint} renders as a visible break but
 * {@link FigText#getText() getText} strips so the model name stays
 * single-line.
 *
 * <p>Hard line breaks (Enter inserting {@code \n}) are intentionally NOT
 * supported here: the parent {@link FigSingleLineText#initialize()} sets
 * {@code setReturnAction(END_EDITING)} so Enter exits the in-place editor
 * just like every other UML element. This avoids accumulating
 * newline-bloated names that the Navigator tree (JLabel) and the
 * property panel (JTextField) can't display coherently.</p>
 *
 * <p>Used by use-case-diagram node figs ({@code FigUseCase},
 * {@code FigActor}) so long names wrap visually inside the ellipse /
 * stick-man instead of being clipped. Inheritance over composition: we
 * still inherit the abstract-italic and bold-name font-style logic from
 * {@link FigNameWithAbstractAndBold}; only the {@code wordWrap}
 * configuration and {@code getMinimumSize} are overridden.</p>
 *
 * <p>Legacy {@code .zargo} files containing {@code \n} in names still
 * load correctly: {@link #getMinimumSize()} counts both hard and soft
 * returns (via {@link FigText#stuffMinimumSize}) and paint renders both.</p>
 *
 * @since 2026-07-11
 */
public class FigMultiLineNameWithAbstractAndBold extends FigNameWithAbstractAndBold {

    /**
     * Construct a soft-wrap name Fig that shows whether the associated
     * item is abstract (italics) or bold.
     *
     * @param owner owning UML element
     * @param bounds position and size
     * @param settings rendering settings
     * @param expandOnly true if fig should never contract
     */
    public FigMultiLineNameWithAbstractAndBold(Object owner, Rectangle bounds,
            DiagramSettings settings, boolean expandOnly) {
        super(owner, bounds, settings, expandOnly);
        // Enable GEF soft word-wrap only.
        //   setWordWrap(true) -> setBoundsImpl re-runs wordWrap() on width
        //     changes, inserting \r soft breaks that paint() renders but
        //     getText() filters out (model stays single-line).
        // We intentionally DO NOT call setReturnAction(INSERT) here — that
        // would make Enter insert \n into the model name, which we do not
        // want for use-case diagrams. The parent FigSingleLineText.initialize()
        // already configured setReturnAction(END_EDITING).
        setWordWrap(true);
    }

    /**
     * Override {@link org.argouml.uml.diagram.ui.FigSingleLineText#getMinimumSize()}
     * (which only counts a single font line) with a multi-line aware size.
     *
     * <p>{@link FigText#stuffMinimumSize(Dimension)} correctly counts both
     * hard ({@code \n}) and soft ({@code \r}) returns and respects the
     * GEF {@code MIN_TEXT_WIDTH = 30} floor. Without this override, parent
     * layouts ({@code FigCompartmentBox.setStandardBounds},
     * {@code FigActor.setStandardBounds}) read a single-line height, force
     * {@code nameFig.setBounds(..., single-line-height)}, and clip
     * {@code paint()} to render only the first line.</p>
     *
     * <p>{@code _fm} (FontMetrics) is normally populated lazily by
     * {@code paint()} / {@code calcBounds()}; {@code getFontMetrics()}
     * returns it (protected, accessible from subclasses). If it is still
     * null (no paint has run yet) we don't bother computing one — the
     * caller will just get the single-line size, which is the same as the
     * pre-fix behaviour. The paint flow populates {@code _fm} on first
     * use, so subsequent {@code getMinimumSize()} calls return the
     * multi-line size.</p>
     */
    @Override
    public Dimension getMinimumSize() {
        if (getFont() == null) {
            return new Dimension();
        }
        if (getFontMetrics() == null) {
            // _fm not yet populated by paint(); defer to FigSingleLineText's
            // single-line fallback so we don't risk ClassCast / null deref.
            return super.getMinimumSize();
        }
        Dimension d = new Dimension();
        stuffMinimumSize(d);
        return d;
    }
}
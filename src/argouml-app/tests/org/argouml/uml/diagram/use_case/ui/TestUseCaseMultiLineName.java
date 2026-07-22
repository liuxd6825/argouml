/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    test (2026-07-11)
 *****************************************************************************
 */
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

package org.argouml.uml.diagram.use_case.ui;

import java.awt.Rectangle;
import java.lang.reflect.Method;

import junit.framework.TestCase;

import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.diagram.DiagramSettings;
import org.argouml.uml.diagram.ui.FigNodeModelElement;
import org.tigris.gef.presentation.FigText;

/**
 * Tests that {@link FigUseCase}, {@link FigActor} and {@link FigExtensionPoint}
 * are configured for <em>soft word-wrap only</em>:
 *   - {@code returnAction == END_EDITING} so Enter exits the in-place
 *     editor (does NOT insert {@code \n} into the model name)
 *   - {@code wordWrap == true} so a name longer than the fig width is
 *     broken at word boundaries (GEF inserts {@code \r} soft returns
 *     that {@code paint()} renders but {@code getText()} strips)
 *   - {@code getMinimumSize()} counts both hard ({@code \n}) and soft
 *     ({@code \r}) returns, so legacy {@code .zargo} files that carry
 *     {@code \n} in names still render multi-line.
 *
 * <p>{@code FigNodeModelElement.getNameFig()} is {@code protected}; tests in
 * this package can't reach it directly, so we go through reflection.
 * Does NOT assert specific pixel dimensions (which depend on L&amp;F font
 * metrics); only checks qualitative invariants.</p>
 *
 * @since 2026-07-11
 */
public class TestUseCaseMultiLineName extends TestCase {

    private Rectangle bounds = new Rectangle(10, 10, 100, 60);
    private DiagramSettings settings = new DiagramSettings();

    public TestUseCaseMultiLineName(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        ProjectManager.getManager().makeEmptyProject();
        new InitNotation().init();
        new InitNotationUml().init();
    }

    /**
     * Reflectively call {@code FigNodeModelElement.getNameFig()}.
     * Method is protected in a different package; reflection sidesteps
     * the access modifier without making the production API public.
     * {@code getDeclaredMethod} (not {@code getMethod}) is needed because
     * {@code getMethod} only returns public members; and we must look it
     * up on {@code FigNodeModelElement.class} (not the subclass) because
     * {@code getDeclaredMethod} only finds methods declared on the given
     * class — inherited methods are not returned.
     */
    private static final Method GET_NAME_FIG;
    static {
        try {
            GET_NAME_FIG = FigNodeModelElement.class.getDeclaredMethod("getNameFig");
            GET_NAME_FIG.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static FigText getNameFig(Object figNode) throws Exception {
        return (FigText) GET_NAME_FIG.invoke(figNode);
    }

    // ----------------- FigUseCase -----------------

    /**
     * The use case name fig must be a
     * {@code FigUseCaseMultiLineNameWithAbstractAndBold} (the subclass
     * used by {@link FigUseCase#createNameFig()} to decouple minimum
     * width from text length, so the ellipse is mouse-resizable and
     * the text wraps at the user-chosen width).
     */
    public void testFigUseCaseNameIsSoftWrap() throws Exception {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            FigUseCase fig = new FigUseCase(uc, bounds, settings);
            FigText nf = getNameFig(fig);
            assertEquals("FigUseCase.nameFig must be "
                    + "FigUseCaseMultiLineNameWithAbstractAndBold",
                    "FigUseCaseMultiLineNameWithAbstractAndBold",
                    nf.getClass().getSimpleName());
            assertEquals("Enter must end editing (no \\n insertion); "
                    + "got returnAction=" + nf.getReturnAction(),
                    FigText.END_EDITING, nf.getReturnAction());
            assertTrue("wordWrap must be enabled for soft auto-wrap",
                    nf.isWordWrap());
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    /**
     * The new use-case-specific name fig must return a width-decoupled
     * minimum size, so the enclosing {@link FigUseCase} can be
     * mouse-resized to any width and the text wraps. Pre-fix the
     * enclosing ellipse refused to shrink below text width because
     * the shared {@code FigMultiLineNameWithAbstractAndBold.getMinimumSize()}
     * returned {@code width = max line width}.
     */
    public void testFigUseCaseNameMinSizeWidthIsDecoupled() throws Exception {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            FigUseCase fig = new FigUseCase(uc, bounds, settings);
            FigText nf = getNameFig(fig);
            nf.setText("a very long use case name that would normally"
                    + " force a minimum width equal to text width");
            // Force a font metrics init if not present.
            if (nf.isWordWrap()) {
                // word-wrap test path
            }
            int minWidth = nf.getMinimumSize().width;
            // Width should be floored at GEF MIN_TEXT_WIDTH (30) and
            // must NOT scale with text length.
            assertEquals("Min width must be GEF MIN_TEXT_WIDTH (30), not"
                            + " driven by text length; got " + minWidth,
                    FigText.MIN_TEXT_WIDTH, minWidth);
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    /**
     * Mouse-resize the {@link FigUseCase} ellipse wider (via
     * {@code setBounds(x, y, w, h)}) and verify that the name fig's
     * width grows accordingly — i.e. word-wrap width follows the
     * user's drag, not a cached text-min container box. This is the
     * direct regression test for the
     * {@link FigUseCase#calculateCompartmentBoxDimensions} removal.
     */
    public void testFigUseCaseResizeChangesNameFigWidth() throws Exception {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            // Start with a wider-than-default fig so we can also
            // verify a narrower resize path.
            FigUseCase fig = new FigUseCase(uc,
                    new Rectangle(10, 10, 200, 80), settings);
            FigText nf = getNameFig(fig);
            nf.setText("a very long use case name that must wrap");
            fig.setBounds(fig.getBounds());
            int wideNameWidth = nf.getBounds().width;

            // Resize narrower. FigUseCase minSize.width = ~80 incl.
            // surrounding lineWidth, so clamp ~ 82.
            fig.setBounds(10, 10, 82, 80);
            int narrowNameWidth = nf.getBounds().width;

            assertTrue("narrow resize nameFig width (" + narrowNameWidth
                            + ") must be < wide resize nameFig width ("
                            + wideNameWidth + ")",
                    narrowNameWidth < wideNameWidth);
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    /**
     * After resizing the {@link FigUseCase} ellipse (especially
     * tall), the name fig must be vertically centred inside the ellipse
     * so the text reads as being in the middle of the element. Pre-fix
     * (v3 removed-override variant) left the name fig at the ellipse's
     * top edge, which made the text appear glued to the top.
     */
    public void testFigUseCaseResizeKeepsNameFigVerticallyCentred()
            throws Exception {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            // Tall ellipse: default 60 high vs resize to 200 high.
            FigUseCase fig = new FigUseCase(uc,
                    new Rectangle(10, 10, 120, 200), settings);
            FigText nf = getNameFig(fig);
            fig.setBounds(fig.getBounds());

            int ellipseH = fig.getBigPort().getBounds().height;
            int nameY = nf.getBounds().y;
            int nameH = nf.getBounds().height;

            // Vertical centre of the ellipse minus vertical centre of
            // the name fig should be small — i.e. name is centred
            // inside the ellipse (within LINE_WIDTH tolerance).
            int ellipseCentre = fig.getBigPort().getBounds().y + ellipseH / 2;
            int nameCentre = nameY + nameH / 2;
            int offset = Math.abs(nameCentre - ellipseCentre);

            assertTrue("name fig must be vertically centred in the"
                            + " ellipse; nameCentre=" + nameCentre
                            + " ellipseCentre=" + ellipseCentre
                            + " (offset=" + offset + ", ellipse h=" + ellipseH + ")",
                    offset <= nameH / 2 + 2);
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    /**
     * A long single-line name, when constrained to a narrow width,
     * must trigger GEF soft-wrap (insert {@code \r} soft returns) and
     * the resulting height must grow accordingly. Direct test of the
     * {@code wordWrap} -> {@code calcBounds} chain without depending on
     * a paint pass having populated {@code _fm}.
     */
    public void testFigUseCaseLongNameGrowsHeightViaSoftWrap()
            throws Exception {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            FigUseCase fig = new FigUseCase(uc, bounds, settings);
            FigText nf = getNameFig(fig);

            // Single-line baseline: 1 line.
            nf.setText("Hi");
            int singleLineHeight = nf.getBounds().height;

            // Long single-line text that far exceeds 30 px MIN_TEXT_WIDTH.
            nf.setText("a very long use case name that must wrap");
            int preLayoutHeight = nf.getBounds().height;

            // Force a layout pass with a narrow width so word-wrap kicks
            // in. Setting a small width (smaller than the text's natural
            // width) makes setBoundsImpl re-run wordWrap on _curText.
            // We then call calcBounds() to refresh _h to fit the wrapped
            // lines — the same recalc that textEdited() triggers in the
            // editor flow.
            nf.setBounds(0, 0, 60, preLayoutHeight);
            // calcBounds is private in GEF FigText; instead simulate the
            // textEdited relayout path via the parent fig.
            fig.setBounds(fig.getBounds());
            int wrappedHeight = nf.getBounds().height;

            assertTrue("soft-wrapped height (" + wrappedHeight
                            + ") must exceed single-line height ("
                            + singleLineHeight + ")",
                    wrappedHeight > singleLineHeight);
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    /**
     * {@link Model#getCoreHelper()#setName} still accepts embedded
     * {@code \n} on the model layer. Legacy {@code .zargo} files
     * carrying {@code \n} in names load and render multi-line; the
     * fig's {@code getMinimumSize()} override counts {@code \n} so the
     * parent ellipse accommodates them.
     */
    public void testModelAcceptsEmbeddedNewlineInName() {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            Model.getCoreHelper().setName(uc, "Use\nCase");
            assertEquals("Use\nCase", Model.getFacade().getName(uc));
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    /**
     * After a {@code textEdited}-equivalent relayout (we call
     * {@code setBounds(getBounds())} directly to simulate it), the name
     * fig must keep enough vertical space to render every visible line
     * (whether soft-wrapped or legacy {@code \n}). Pre-fix,
     * {@code FigCompartmentBox.setStandardBounds} read
     * {@code FigSingleLineText.getMinimumSize()} (single-line only) and
     * clamped {@code nameFig._h} to ~21 px regardless of how many lines
     * the text had, so {@code paint()} only emitted the first line.
     */
    public void testFigUseCaseLayoutPreservesMultiLineHeight() throws Exception {
        Object uc = Model.getUseCasesFactory().createUseCase();
        try {
            FigUseCase fig = new FigUseCase(uc, bounds, settings);
            FigText nf = getNameFig(fig);
            // Use a long single-line name to drive soft-wrap (the
            // realistic path under soft-wrap-only policy); \n also
            // works via the legacy data path.
            nf.setText("A sufficiently long use case name that wraps");
            // Simulate the FigTextEditor -> textEdited -> setBounds flow.
            fig.setBounds(fig.getBounds());

            int singleLineH = 21;     // FigNodeModelElement.NAME_FIG_HEIGHT
            int actualH = nf.getBounds().height;
            assertTrue("nameFig height (" + actualH
                            + ") must exceed single-line minimum (" + singleLineH
                            + ") after relayout",
                    actualH > singleLineH);
            // Also verify the ellipse grew to contain the taller name.
            int bigPortH = fig.getBigPort().getBounds().height;
            assertTrue("ellipse (bigPort) height (" + bigPortH
                            + ") must exceed the single-line fig minimum",
                    bigPortH > singleLineH + 10);
        } finally {
            Model.getUmlFactory().delete(uc);
        }
    }

    // ----------------- FigActor -----------------

    public void testFigActorNameIsSoftWrap() throws Exception {
        Object act = Model.getUseCasesFactory().createActor();
        try {
            FigActor fig = new FigActor(act, bounds, settings);
            FigText nf = getNameFig(fig);
            assertEquals("FigActor.nameFig must be "
                    + "FigMultiLineNameWithAbstractAndBold",
                    "FigMultiLineNameWithAbstractAndBold",
                    nf.getClass().getSimpleName());
            assertEquals("Enter must end editing (no \\n insertion); "
                    + "got returnAction=" + nf.getReturnAction(),
                    FigText.END_EDITING, nf.getReturnAction());
            assertTrue("wordWrap must be enabled", nf.isWordWrap());
        } finally {
            Model.getUmlFactory().delete(act);
        }
    }

    /**
     * {@code MActor.name} still accepts embedded {@code \n} via
     * {@link Model#getCoreHelper()#setName} (backward compat).
     */
    public void testModelAcceptsEmbeddedNewlineInActorName() {
        Object act = Model.getUseCasesFactory().createActor();
        try {
            Model.getCoreHelper().setName(act, "Primary\nAlias");
            assertEquals("Primary\nAlias", Model.getFacade().getName(act));
        } finally {
            Model.getUmlFactory().delete(act);
        }
    }

    /**
     * Same regression check for {@link FigActor}: a long name must
     * keep its name fig tall enough after relayout.
     */
    public void testFigActorLayoutPreservesMultiLineHeight() throws Exception {
        Object act = Model.getUseCasesFactory().createActor();
        try {
            FigActor fig = new FigActor(act, bounds, settings);
            FigText nf = getNameFig(fig);
            nf.setText("A sufficiently long actor name that wraps");
            fig.setBounds(fig.getBounds());

            int actualH = nf.getBounds().height;
            assertTrue("nameFig height (" + actualH
                            + ") must exceed single-line minimum (21)",
                    actualH > 21);
        } finally {
            Model.getUmlFactory().delete(act);
        }
    }

    // ----------------- FigExtensionPoint -----------------

    public void testFigExtensionPointSoftWrapConfig() {
        Object useCase = Model.getUseCasesFactory().createUseCase();
        Object ep = Model.getUseCasesFactory().buildExtensionPoint(useCase);
        try {
            FigExtensionPoint fig = new FigExtensionPoint(ep, bounds, settings);
            assertEquals("Enter must end editing (no \\n insertion); "
                    + "got returnAction=" + fig.getReturnAction(),
                    FigText.END_EDITING, fig.getReturnAction());
            assertTrue("wordWrap must be enabled", fig.isWordWrap());
        } finally {
            Model.getUmlFactory().delete(ep);
            Model.getUmlFactory().delete(useCase);
        }
    }
}
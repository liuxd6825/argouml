/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.core.propertypanels.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.argouml.i18n.Translator;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.model.RepresentedDiagramLinkCache;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Custom property-panel field for {@code UseCase.representedDiagram}.
 *
 * <p>Renders a labelled text field with a "Browse..." button. The
 * Browse button opens a dialog listing every ArgoDiagram in the
 * current project; selecting one writes its namespace UUID (the
 * diagram's stable identity proxy — ArgoDiagram itself has no
 * UUID) into the text field. The text field is also directly
 * editable for users who already have a UUID.</p>
 *
* <p>Wired through ArgoUML's tagged-value mechanism (tag
 * name {@code "representedDiagram"}).</p>
 *
 * <p><b>MDR round-trip quirk</b>: the MDR backend's
 * {@code ExtensionMechanismsHelper.setType} rejects a String
 * type argument and throws {@code IllegalArgumentException};
 * only a {@code TagDefinition} object is accepted. We don't
 * have a TagDefinition for {@code "representedDiagram"} in
 * any profile, so the underlying {@code emh.setType(tv,
 * TAG_NAME)} call fails silently. The tagged value may still
 * get added to the model element, but {@code facade.getName(tv)}
 * returns null on read-back, so the value is invisible across
 * re-reads.</p>
 *
 * <p>To compensate, this class routes read and write through
 * the shared {@link RepresentedDiagramLinkCache} so the field's
 * value survives focus switches within the same JVM, and so
 * other readers (the AI REST service
 * {@code UseCaseDiagramService}, the right-click navigation
 * action) see the same value.</p>
 *
 * <p>Loaded by the XML property panel system via
 * {@code <custom-component class="...UseCaseRepresentedDiagramField"/>}
 * in panels.xml.</p>
 */
public final class UseCaseRepresentedDiagramField extends JPanel {

    private static final String TAG_NAME = "representedDiagram";

    private final JTextField textField = new JTextField();
    private final JButton browseButton = new JButton(
            Translator.localize("button.browse"));
    private Object useCase;
    private boolean suppressEvents;

    public UseCaseRepresentedDiagramField() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(new JLabel(Translator.localize("label.represented-diagram")));
        add(Box.createHorizontalStrut(6));
        textField.setMaximumSize(new java.awt.Dimension(
                Integer.MAX_VALUE, textField.getPreferredSize().height));
        add(textField);
        add(Box.createHorizontalStrut(6));
        add(browseButton);

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { commit(); }
            @Override
            public void removeUpdate(DocumentEvent e) { commit(); }
            @Override
            public void changedUpdate(DocumentEvent e) { commit(); }
        });
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openBrowseDialog();
            }
        });
    }

    /**
     * Bind the field to a model element. Called by the panel
     * infrastructure when the selection changes. Reads from
     * the cache first (filled by a previous {@link #commit}
     * in the same JVM); falls back to the tagged-value lookup
     * if the cache misses.
     */
    public void setTarget(Object target) {
        this.useCase = target;
        suppressEvents = true;
        try {
            textField.setText(lookupCached(target));
        } finally {
            suppressEvents = false;
        }
    }

    private void commit() {
        if (suppressEvents || useCase == null) {
            return;
        }
        String text = textField.getText() == null ? "" : textField.getText();
        RepresentedDiagramLinkCache.put(useCase,
                java.util.Collections.singletonList(text));
        try {
            org.argouml.model.ExtensionMechanismsFactory emf =
                    Model.getExtensionMechanismsFactory();
            org.argouml.model.ExtensionMechanismsHelper emh =
                    Model.getExtensionMechanismsHelper();
            Object tv = emf.createTaggedValue();
            emh.addTaggedValue(useCase, tv);
            emh.setType(tv, TAG_NAME);
            emh.setDataValues(tv, new String[] {text});
        } catch (RuntimeException ignored) {
            // best-effort: tagged-value write may fail on MDR
            // (setType rejects String arguments). The cache
            // write above is what makes the value visible
            // across focus switches and to other readers.
        }
    }

    /**
     * Look up the value for a UseCase: shared cache first, then
     * tagged-value scan as fallback. Cache hits make set/get
     * round-trip reliable within the JVM even when the
     * underlying tagged-value write/read is broken on the
     * current backend.
     */
    private static String lookupCached(Object target) {
        if (target == null) {
            return "";
        }
        List<String> cached = RepresentedDiagramLinkCache.getAll(target);
        if (!cached.isEmpty()) {
            return cached.get(0);
        }
        String fromTag = readTag(target);
        if (!fromTag.isEmpty()) {
            RepresentedDiagramLinkCache.put(target,
                    java.util.Collections.singletonList(fromTag));
        }
        return fromTag;
    }

    private void openBrowseDialog() {
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            return;
        }
        List<DiagramChoice> choices = new ArrayList<DiagramChoice>();
        Facade facade = Model.getFacade();
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) {
                continue;
            }
            ArgoDiagram ad = (ArgoDiagram) d;
            String uuid = "";
            Object ns = ad.getNamespace();
            if (ns != null) {
                try {
                    String u = facade.getUUID(ns);
                    if (u != null) {
                        uuid = u;
                    }
                } catch (RuntimeException ignored) {
                }
            }
            choices.add(new DiagramChoice(ad, uuid));
        }
        Window window = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(
                window instanceof Frame ? (Frame) window : null,
                Translator.localize("title.select-diagram"),
                window instanceof Dialog);
        dialog.setContentPane(buildDialogContent(choices, dialog));
        dialog.setSize(380, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel buildDialogContent(
            final List<DiagramChoice> choices, final JDialog dialog) {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        final JList list = new JList(choices.toArray());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedValue(findCurrentChoice(choices), true);
        root.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton ok = new JButton(Translator.localize("button.ok"));
        JButton cancel = new JButton(Translator.localize("button.cancel"));
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Object sel = list.getSelectedValue();
                if (sel instanceof DiagramChoice) {
                    suppressEvents = true;
                    try {
                        textField.setText(((DiagramChoice) sel).uuid);
                    } finally {
                        suppressEvents = false;
                    }
                    commit();
                }
                dialog.dispose();
            }
        });
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        list.addListSelectionListener(
                new javax.swing.event.ListSelectionListener() {
                    @Override
                    public void valueChanged(
                            javax.swing.event.ListSelectionEvent e) {
                        // no-op; selection committed on OK
                    }
                });
        buttons.add(ok);
        buttons.add(cancel);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private DiagramChoice findCurrentChoice(List<DiagramChoice> choices) {
        String current = textField.getText();
        if (current == null || current.isEmpty()) {
            return null;
        }
        for (DiagramChoice c : choices) {
            if (current.equals(c.uuid)) {
                return c;
            }
        }
        return null;
    }

    private static String readTag(Object target) {
        if (target == null) {
            return "";
        }
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(target);
            if (tvs == null) {
                return "";
            }
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if (TAG_NAME.equals(facade.getName(tv))) {
                    Object v = facade.getValue(tv);
                    return v == null ? "" : v.toString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }

    private static final class DiagramChoice {
        final ArgoDiagram diagram;
        final String uuid;
        final String label;
        DiagramChoice(ArgoDiagram d, String u) {
            this.diagram = d;
            this.uuid = u == null ? "" : u;
            this.label = (d.getName() == null ? "(unnamed)" : d.getName())
                    + "  [" + d.getClass().getSimpleName() + "]";
        }
        @Override
        public String toString() {
            return label + "  ::  " + uuid;
        }
    }
}
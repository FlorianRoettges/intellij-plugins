/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osmorc.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osmorc.frameworkintegration.*;
import org.osmorc.i18n.OsmorcBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * Dialog for creating/updating a framework instance.
 *
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class CreateFrameworkInstanceDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private TextFieldWithBrowseButton myBaseFolderChooser;
  private JTextField myNameField;
  private JBLabel myVersionLabel;

  private final FrameworkInstanceDefinition myInstance;
  private final DefaultListModel myModel;
  private final FrameworkIntegrator myIntegrator;

  public CreateFrameworkInstanceDialog(@NotNull FrameworkInstanceDefinition instance, @NotNull DefaultListModel model) {
    super(true);

    myInstance = instance;
    myModel = model;
    myIntegrator = FrameworkIntegratorRegistry.getInstance().findIntegratorByInstanceDefinition(instance);
    assert myIntegrator != null : instance;

    setTitle(OsmorcBundle.message("framework.edit.title", myIntegrator.getDisplayName()));
    setModal(true);

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    String title = OsmorcBundle.message("framework.path.chooser.title");
    String description = OsmorcBundle.message("framework.path.chooser.description", myIntegrator.getDisplayName());
    myBaseFolderChooser.addBrowseFolderListener(title, description, null, descriptor);
    myBaseFolderChooser.getTextField().setEditable(false);
    myBaseFolderChooser.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        checkInstance(); updateVersion();
      }
    });

    myNameField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        checkInstance();
      }
    });

    myBaseFolderChooser.setText(ObjectUtils.notNull(instance.getBaseFolder(), ""));
    myNameField.setText(ObjectUtils.notNull(instance.getName(), ""));
    myVersionLabel.setText(ObjectUtils.notNull(instance.getVersion(), ""));

    init();

    checkInstance();
  }

  private void checkInstance() {
    FrameworkInstanceDefinition newInstance = createDefinition();
    String message = myIntegrator.getFrameworkInstanceManager().checkValidity(newInstance);

    if (message == null) {
      for (int i = 0; i < myModel.size(); i++) {
        FrameworkInstanceDefinition instance = (FrameworkInstanceDefinition)myModel.get(i);
        if (newInstance.equals(instance) && instance != myInstance) {
          message = OsmorcBundle.message("framework.name.duplicate");
        }
      }
    }

    setErrorText(message);
    setOKActionEnabled(message == null);
  }

  private void updateVersion() {
    FrameworkInstanceDefinition instance = createDefinition();
    FrameworkInstanceManager manager = myIntegrator.getFrameworkInstanceManager();
    if (manager instanceof AbstractFrameworkInstanceManager) {
      String version = ((AbstractFrameworkInstanceManager)manager).getVersion(instance);
      if (version != null) {
        myNameField.setText(myIntegrator.getDisplayName() + " (" + version + ")");
        myVersionLabel.setText(version);
        return;
      }
    }

    myNameField.setText(myIntegrator.getDisplayName());
    myVersionLabel.setText("");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myBaseFolderChooser.getButton();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected String getHelpId() {
    return "reference.settings.project.osgi.new.framework.instance";
  }

  @NotNull
  public FrameworkInstanceDefinition createDefinition() {
    FrameworkInstanceDefinition framework = new FrameworkInstanceDefinition();
    framework.setName(myNameField.getText().trim());
    framework.setFrameworkIntegratorName(myIntegrator.getDisplayName());
    framework.setBaseFolder(myBaseFolderChooser.getText().trim());
    framework.setVersion(myVersionLabel.getText().trim());
    return framework;
  }
}

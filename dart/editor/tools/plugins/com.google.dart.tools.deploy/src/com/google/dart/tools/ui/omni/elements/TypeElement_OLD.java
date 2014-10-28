/*
 * Copyright (c) 2013, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.tools.ui.omni.elements;

import com.google.dart.engine.element.Element;
import com.google.dart.engine.element.FunctionTypeAliasElement;
import com.google.dart.engine.element.LibraryElement;
import com.google.dart.engine.source.Source;
import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.ui.DartElementLabels;
import com.google.dart.tools.ui.DartPluginImages;
import com.google.dart.tools.ui.DartToolsPlugin;
import com.google.dart.tools.ui.DartUI;
import com.google.dart.tools.ui.instrumentation.UIInstrumentationBuilder;
import com.google.dart.tools.ui.omni.OmniElement;
import com.google.dart.tools.ui.omni.OmniProposalProvider;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * {@link OmniElement} for types.
 */
public class TypeElement_OLD extends OmniElement {
  private static final String DEFAULT_PROJECT = "Dart SDK";
  private final Element element;

  public TypeElement_OLD(OmniProposalProvider provider, Element element) {
    super(provider);
    this.element = element;
  }

  @Override
  public String getId() {
    return element.getDisplayName();
  }

  @Override
  public ImageDescriptor getImageDescriptor() {
    if (element instanceof FunctionTypeAliasElement) {
      return DartPluginImages.DESC_DART_FUNCTIONTYPE_PUBLIC;
    }
    return DartPluginImages.DESC_DART_CLASS_PUBLIC;
  }

  @Override
  public String getInfoLabel() {
    String info = "";
    Source source = element.getSource();
    IResource resource = DartCore.getProjectManager().getResource(source);
    if (resource != null) {
      info = resource.getProject().getName();
    } else {
      IProject project = DartCore.getProjectManager().getProjectForContext(element.getContext());
      if (project != null) {
        info = project.getName();
      } else {
        info = DEFAULT_PROJECT;
      }
    }
    return info;
  }

  @Override
  public String getLabel() {
    StringBuffer result = new StringBuffer();
    result.append(element.getDisplayName());

    //cache detail offset (used for styling detail area in OmniElement.paint(...))
    detailOffset = result.length();

    LibraryElement library = element.getLibrary();
    if (library != null) {
      result.append(DartElementLabels.CONCAT_STRING);
      result.append(library.getDisplayName());
    }
    return result.toString();
  }

  @Override
  protected void doExecute(String text, UIInstrumentationBuilder instrumentation) {
    instrumentation.data("TypeElement.searchResultSelected", element.getDisplayName());
    try {
      DartUI.openInEditor(element);
    } catch (Throwable e) {
      DartToolsPlugin.log(e);
    }
  }
}

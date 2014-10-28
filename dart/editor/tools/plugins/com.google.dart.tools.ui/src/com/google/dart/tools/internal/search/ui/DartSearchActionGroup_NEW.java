/*
 * Copyright (c) 2014, the Dart project authors.
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
package com.google.dart.tools.internal.search.ui;

import com.google.dart.tools.ui.actions.AbstractDartSelectionActionGroup;
import com.google.dart.tools.ui.actions.DartEditorActionDefinitionIds;
import com.google.dart.tools.ui.actions.OpenAction_NEW;
import com.google.dart.tools.ui.internal.text.editor.DartEditor;

import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.actions.ActionGroup;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

/**
 * {@link ActionGroup} that adds the Dart search actions.
 * 
 * @coverage dart.editor.ui.search
 */
public class DartSearchActionGroup_NEW extends AbstractDartSelectionActionGroup {
  private FindReferencesAction_NEW findReferencesAction;
  private FindDeclarationsAction_NEW findDeclarationsAction;
  private OpenAction_NEW openAction;

  public DartSearchActionGroup_NEW(DartEditor editor) {
    super(editor);
    findReferencesAction = new FindReferencesAction_NEW(editor);
    findDeclarationsAction = new FindDeclarationsAction_NEW(editor);
    openAction = new OpenAction_NEW(editor);
    initActions();
    editor.setAction(findReferencesAction.getActionDefinitionId(), findReferencesAction);
    editor.setAction(findDeclarationsAction.getActionDefinitionId(), findDeclarationsAction);
    editor.setAction("OpenEditor", openAction);
    addActions(findReferencesAction, findDeclarationsAction, openAction);
    addActionDartSelectionListeners();
  }

  @Override
  public void dispose() {
    super.dispose();
    findReferencesAction = null;
    findDeclarationsAction = null;
    openAction = null;
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    ISelection selection = getContext().getSelection();
    updateActions(selection);
    appendToGroup(menu, ITextEditorActionConstants.GROUP_OPEN);
  }

  /**
   * Initializes definition attributes of actions.
   */
  private void initActions() {
    findReferencesAction.setActionDefinitionId(DartEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE);
    findReferencesAction.setId(DartEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE);
    findDeclarationsAction.setActionDefinitionId(DartEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKSPACE);
    findDeclarationsAction.setId(DartEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKSPACE);
    if (openAction != null) {
      openAction.setActionDefinitionId(DartEditorActionDefinitionIds.OPEN_EDITOR);
      openAction.setId(DartEditorActionDefinitionIds.OPEN_EDITOR);
    }
  }
}

/*
 * Copyright (c) 2012, the Dart project authors.
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
package com.google.dart.tools.ui.internal.refactoring;

import com.google.dart.tools.ui.DartToolsPlugin;
import com.google.dart.tools.ui.internal.text.editor.EditorUtility;
import com.google.dart.tools.ui.internal.util.CoreUtility;
import com.google.dart.tools.ui.internal.util.ExceptionHandler;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.actions.GlobalBuildAction;

/**
 * Helper to save dirty editors prior to starting a refactoring. Saving happens with automatic build
 * turned off, so when {@link #triggerIncrementalBuild()} should be called later to build changes
 * made by refactoring and also changes in saved editors.
 * 
 * @coverage dart.editor.ui.refactoring.ui
 */
public class RefactoringSaveHelper {

  private boolean filesSaved;
  private final int saveMode;

  /**
   * Save mode to save all dirty editors.
   */
  public static final int SAVE_ALL = 2;

  /**
   * Save mode to not save any editors.
   */
  public static final int SAVE_NOTHING = 3;

  public RefactoringSaveHelper(int saveMode) {
    Assert.isLegal(saveMode == SAVE_ALL || saveMode == SAVE_NOTHING);
    this.saveMode = saveMode;
  }

  /**
   * Saves all editors.
   * 
   * @return <code>true</code> if save was successful and refactoring can proceed; false if the
   *         refactoring must be cancelled
   */
  public boolean saveEditors(Shell shell) {
    // May be no save required.
    if (saveMode == SAVE_NOTHING) {
      return true;
    }
    // Prepare dirty editors.
    IEditorPart[] dirtyEditors = EditorUtility.getDirtyEditors();
    if (dirtyEditors.length == 0) {
      return true;
    }
    try {
      boolean autoBuild = CoreUtility.setAutoBuilding(false);
      try {
        // do save
        if (!DartToolsPlugin.getActiveWorkbenchWindow().getWorkbench().saveAllEditors(false)) {
          return false;
        }
        // done
        filesSaved = true;
      } finally {
        CoreUtility.setAutoBuilding(autoBuild);
      }
      return true;
    } catch (CoreException e) {
      ExceptionHandler.handle(
          e,
          shell,
          RefactoringMessages.RefactoringStarter_saving,
          RefactoringMessages.RefactoringStarter_unexpected_exception);
      return false;
    }
  }

  /**
   * Triggers an incremental build if this save helper did save files before.
   */
  public void triggerIncrementalBuild() {
    if (filesSaved && ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding()) {
      new GlobalBuildAction(
          DartToolsPlugin.getActiveWorkbenchWindow(),
          IncrementalProjectBuilder.INCREMENTAL_BUILD).run();
    }
  }
}

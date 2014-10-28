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
package com.google.dart.tools.debug.ui.internal.breakpoints;

import com.google.dart.tools.debug.core.DartDebugCorePlugin;
import com.google.dart.tools.debug.core.breakpoints.DartBreakpoint;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;

/**
 * Adapter class in charge of toggling Dart breakpoints.
 * 
 * @see IToggleBreakpointsTarget
 */
public class DartBreakpointAdapter implements IToggleBreakpointsTarget {

  /**
   * Create a new DartBreakpointAdapter.
   */
  public DartBreakpointAdapter() {

  }

  @Override
  public boolean canToggleLineBreakpoints(IWorkbenchPart part, ISelection selection) {
    return getEditor(part) != null;
  }

  @Override
  public boolean canToggleMethodBreakpoints(IWorkbenchPart part, ISelection selection) {
    return false;
  }

  @Override
  public boolean canToggleWatchpoints(IWorkbenchPart part, ISelection selection) {
    return false;
  }

  @Override
  public void toggleLineBreakpoints(IWorkbenchPart part, ISelection selection) throws CoreException {
    AbstractTextEditor editor = getEditor(part);

    if (editor != null) {
      IResource resource = (IResource) editor.getEditorInput().getAdapter(IResource.class);

      // if no resource is associated
      String filePath = null;
      if (resource == null) {
        resource = ResourcesPlugin.getWorkspace().getRoot();
        IEditorInput input = editor.getEditorInput();
        if (input instanceof FileStoreEditorInput) {
          filePath = ((FileStoreEditorInput) input).getURI().getPath();
        }
      }

      ITextSelection textSelection = (ITextSelection) selection;

      int lineNumber = textSelection.getStartLine() + 1;

      // Remove a breakpoint if one is set on this line.
      IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(
          DartDebugCorePlugin.DEBUG_MODEL_ID);

      for (int i = 0; i < breakpoints.length; i++) {
        IBreakpoint breakpoint = breakpoints[i];

        if (resource.equals(breakpoint.getMarker().getResource())) {
          if (((ILineBreakpoint) breakpoint).getLineNumber() == lineNumber) {
            breakpoint.delete();
            return;
          }
        }
      }

      // Check for a whitespace line.
      try {
        IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
        IRegion lineInfo = document.getLineInformation(textSelection.getStartLine());
        String line = document.get(lineInfo.getOffset(), lineInfo.getLength());

        line = line.trim();

        // Disallow setting breakpoints on whitespace lines.
        if (line.length() == 0) {
          return;
        }

        // Or line comment lines.
        if (line.startsWith("//")) {
          return;
        }
      } catch (BadLocationException e) {

      }

      DartBreakpoint breakpoint = new DartBreakpoint(resource, lineNumber, filePath);
      DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(breakpoint);
    }
  }

  @Override
  public void toggleMethodBreakpoints(IWorkbenchPart part, ISelection selection)
      throws CoreException {

  }

  @Override
  public void toggleWatchpoints(IWorkbenchPart part, ISelection selection) throws CoreException {

  }

  protected AbstractTextEditor getEditor(IWorkbenchPart part) {
    if (part instanceof AbstractTextEditor) {
      return (AbstractTextEditor) part;
    } else {
      return null;
    }
  }

}

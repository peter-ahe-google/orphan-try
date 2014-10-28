/*
 * Copyright (c) 2011, the Dart project authors.
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
package com.google.dart.tools.ui.internal.text.editor.selectionactions;

import com.google.dart.engine.utilities.source.SourceRange;
import com.google.dart.tools.core.model.DartModelException;
import com.google.dart.tools.core.model.SourceReference;
import com.google.dart.tools.ui.DartX;
import com.google.dart.tools.ui.internal.text.DartHelpContextIds;
import com.google.dart.tools.ui.internal.text.editor.DartEditor;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IUpdate;

import java.util.List;

public class GoToNextPreviousMemberAction extends Action implements IUpdate {

  public static final String NEXT_MEMBER = "GoToNextMember"; //$NON-NLS-1$
  public static final String PREVIOUS_MEMBER = "GoToPreviousMember"; //$NON-NLS-1$

  public static GoToNextPreviousMemberAction newGoToNextMemberAction(DartEditor editor) {
    String text = SelectionActionMessages.GotoNextMember_label;
    return new GoToNextPreviousMemberAction(editor, text, true);
  }

  public static GoToNextPreviousMemberAction newGoToPreviousMemberAction(DartEditor editor) {
    String text = SelectionActionMessages.GotoPreviousMember_label;
    return new GoToNextPreviousMemberAction(editor, text, false);
  }

  private static void addOffset(List<Integer> result, int offset) {
    if (offset >= 0) {
      result.add(new Integer(offset));
    }
  }

  private static SourceRange createNewSourceRange(Integer offset) {
    return new SourceRange(offset.intValue(), 0);
  }

  private static SourceRange createSourceRange(ITextSelection ts) {
    return new SourceRange(ts.getOffset(), ts.getLength());
  }

  // private static int firstOpeningBraceOffset(IInitializer iInitializer)
  // throws DartModelException {
  // try {
  // DartScanner scanner = ToolFactory.createScanner(false, false, false,
// false);
  // scanner.setSource(iInitializer.getSource().toCharArray());
  // int token = scanner.getNextToken();
  // while (token != ITerminalSymbols.TokenNameEOF
  // && token != ITerminalSymbols.TokenNameLBRACE)
  // token = scanner.getNextToken();
  // if (token == ITerminalSymbols.TokenNameLBRACE)
  // return iInitializer.getSourceRange().getOffset()
  // + scanner.getCurrentTokenStartPosition()
  // + scanner.getRawTokenSource().length;
  // return iInitializer.getSourceRange().getOffset();
  // } catch (InvalidInputException e) {
  // return iInitializer.getSourceRange().getOffset();
  // }
  // }

  private static Integer getNextOffset(int index, Integer[] offsetArray, Integer oldOffset) {
    if (index == -1) {
      return offsetArray[0];
    }

    if (index == 0) {
      if (offsetArray.length != 1) {
        return offsetArray[1];
      } else {
        return offsetArray[0];
      }
    }
    if (index > 0) {
      if (index == offsetArray.length - 1) {
        return oldOffset;
      }
      return offsetArray[index + 1];
    }
    Assert.isTrue(index < -1);
    int absIndex = Math.abs(index);
    if (absIndex > offsetArray.length) {
      return oldOffset;
    } else {
      return offsetArray[absIndex - 1];
    }
  }

  private static Integer getPreviousOffset(int index, Integer[] offsetArray, Integer oldOffset) {
    if (index == -1) {
      return oldOffset;
    }
    if (index == 0) {
      return offsetArray[0];
    }
    if (index > 0) {
      return offsetArray[index - 1];
    }
    Assert.isTrue(index < -1);
    int absIndex = Math.abs(index);
    return offsetArray[absIndex - 2];
  }

  private DartEditor fEditor;

  private boolean fIsGotoNext;

  /*
   * This constructor is for testing purpose only.
   */
  public GoToNextPreviousMemberAction(boolean isSelectNext) {
    super(""); //$NON-NLS-1$
    fIsGotoNext = isSelectNext;
  }

  private GoToNextPreviousMemberAction(DartEditor editor, String text, boolean isGotoNext) {
    super(text);
    Assert.isNotNull(editor);
    fEditor = editor;
    fIsGotoNext = isGotoNext;
    update();
    if (isGotoNext) {
      PlatformUI.getWorkbench().getHelpSystem().setHelp(
          this,
          DartHelpContextIds.GOTO_NEXT_MEMBER_ACTION);
    } else {
      PlatformUI.getWorkbench().getHelpSystem().setHelp(
          this,
          DartHelpContextIds.GOTO_PREVIOUS_MEMBER_ACTION);
    }
  }

  /*
   * (non-JavaDoc) Method declared in IAction.
   */
  @Override
  public final void run() {
    ITextSelection selection = getTextSelection();
    SourceRange newRange = createSourceRange(selection);
    // Check if new selection differs from current selection
    if (selection.getOffset() == newRange.getOffset()
        && selection.getLength() == newRange.getLength()) {
      return;
    }
    fEditor.selectAndReveal(newRange.getOffset(), newRange.getLength());
  }

  @Override
  public void update() {
    boolean enabled = false;
    SourceReference ref = getSourceReference();
    if (ref != null) {
      SourceRange range;
      try {
        range = ref.getSourceRange();
        enabled = range != null && range.getLength() > 0;
      } catch (DartModelException e) {
        // enabled= false;
      }
    }
    setEnabled(enabled);
  }

  private SourceReference getSourceReference() {
    DartX.todo();
    return null;
    // IEditorInput input = fEditor.getEditorInput();
    // return
    // DartToolsPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(
    // input);
  }

  // -- private helper methods

  private ITextSelection getTextSelection() {
    return (ITextSelection) fEditor.getSelectionProvider().getSelection();
  }
}

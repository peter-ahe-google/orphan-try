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

package com.google.dart.tools.debug.ui.internal.objectinspector;

import com.google.dart.engine.utilities.instrumentation.Base64;
import com.google.dart.tools.core.utilities.general.AdapterUtilities;
import com.google.dart.tools.debug.core.util.HistoryList;
import com.google.dart.tools.debug.core.util.HistoryListListener;
import com.google.dart.tools.debug.core.util.HistoryListMatcher;
import com.google.dart.tools.debug.core.util.IDartDebugValue;
import com.google.dart.tools.debug.ui.internal.DartDebugUIPlugin;
import com.google.dart.tools.debug.ui.internal.objectinspector.ExpressionEvaluateJob.ExpressionListener;
import com.google.dart.tools.debug.ui.internal.presentation.DartDebugModelPresentation;
import com.google.dart.tools.ui.DartToolsPlugin;
import com.google.dart.tools.ui.DartUI;
import com.google.dart.tools.ui.instrumentation.UIInstrumentation;
import com.google.dart.tools.ui.instrumentation.UIInstrumentationBuilder;
import com.google.dart.tools.ui.internal.text.editor.DartDocumentSetupParticipant;
import com.google.dart.tools.ui.internal.util.SelectionUtil;
import com.google.dart.tools.ui.text.DartPartitions;
import com.google.dart.tools.ui.text.DartSourceViewerConfiguration;
import com.google.dart.tools.ui.text.DartTextTools;

import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.core.model.IWatchExpressionResult;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IValueDetailListener;
import org.eclipse.debug.ui.contexts.DebugContextEvent;
import org.eclipse.debug.ui.contexts.IDebugContextListener;
import org.eclipse.debug.ui.contexts.IDebugContextService;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.IUndoManagerExtension;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.TextViewerUndoManager;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.operations.RedoActionHandler;
import org.eclipse.ui.operations.UndoActionHandler;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.texteditor.IUpdate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

// TODO: navigate to the code for an object

/**
 * The Dart object inspector view.
 */
public class ObjectInspectorView extends ViewPart implements IDebugEventSetListener,
    IDebugContextListener {
  class TextViewerAction extends Action implements IUpdate {
    private int actionId;

    TextViewerAction(int actionId) {
      this.actionId = actionId;
    }

    @Override
    public boolean isEnabled() {
      return sourceViewer.canDoOperation(actionId);
    }

    @Override
    public void run() {
      sourceViewer.doOperation(actionId);

      updateActions();
    }

    @Override
    public void update() {
      if (super.isEnabled() != isEnabled()) {
        setEnabled(isEnabled());
      }
    }
  }

  private class InspectItAction extends Action implements IUpdate {
    public InspectItAction() {
      super("Inspect It...", DartDebugUIPlugin.getImageDescriptor("obj16/watchlist_view.gif"));

      setId(getText());
    }

    @Override
    public void run() {
      String selection = getTextToEval();

      final UIInstrumentationBuilder instrumentation = UIInstrumentation.builder(getClass());
      instrumentation.record(new TextSelection(new Document(selection), 0, selection.length()));

      Job job = new ExpressionEvaluateJob(getValue(), selection, new ExpressionListener() {
        @Override
        public void watchEvaluationFinished(IWatchExpressionResult result, String stringValue) {
          try {
            if (result.hasErrors()) {
              displayError(result);
            } else {
              inspectAsync(result.getValue());
              instrumentation.data(
                  "InspectResult",
                  Base64.encodeBytes(String.valueOf(result.getValue()).getBytes()));
            }
            instrumentation.metric("Evaluate", "Completed");
          } finally {
            instrumentation.log();
          }
        }
      });

      job.schedule();
    }

    @Override
    public void update() {
      setEnabled(hasActiveConnection());
    }
  }

  private class PrintItAction extends Action implements IUpdate {
    public PrintItAction() {
      super("Print It", DartDebugUIPlugin.getImageDescriptor("obj16/variable_tab.gif"));

      setId(getText());
    }

    @Override
    public void run() {
      String selection = getTextToEval();

      final UIInstrumentationBuilder instrumentation = UIInstrumentation.builder(getClass());
      instrumentation.record(new TextSelection(new Document(selection), 0, selection.length()));

      Job job = new ExpressionEvaluateJob(getValue(), selection, new ExpressionListener() {
        @Override
        public void watchEvaluationFinished(IWatchExpressionResult result, String stringValue) {
          try {
            if (result.hasErrors()) {
              displayError(result);
            } else {
              displayResult(stringValue);
              resetCurrentValue();
              instrumentation.data(
                  "EvaluateResult",
                  Base64.encodeBytes(String.valueOf(result.getValue()).getBytes()));
            }

            instrumentation.metric("Evaluate", "Completed");
          } finally {
            instrumentation.log();
          }
        }
      });

      job.schedule();
    }

    @Override
    public void update() {
      setEnabled(hasActiveConnection());
    }
  }

  private static DartDebugModelPresentation presentation = new DartDebugModelPresentation();

  public static void inspect(IValue value) {
    inspect(value, false);
  }

  public static void inspect(IValue value, boolean newInspector) {
    try {
      IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

      ObjectInspectorView view;

      if (newInspector) {
        view = (ObjectInspectorView) page.showView(
            DartUI.ID_INSPECTOR_VIEW,
            Integer.toString(new Random().nextInt()),
            IWorkbenchPage.VIEW_ACTIVATE);
      } else {
        // find the best match to the current inspector
        if (page.getActivePart() instanceof ObjectInspectorView) {
          view = (ObjectInspectorView) page.getActivePart();
        } else {
          view = (ObjectInspectorView) page.showView(DartUI.ID_INSPECTOR_VIEW);
        }
      }

      view.inspectValue(value);
    } catch (PartInitException e) {
      DartDebugUIPlugin.logError(e);
    }
  }

  protected static void inspectAsync(final IValue value) {
    Display.getDefault().asyncExec(new Runnable() {
      @Override
      public void run() {
        inspect(value, false);
      }
    });
  }

  private SashForm sashForm;

  private TreeViewer treeViewer;

  private UndoActionHandler undoAction;

  private RedoActionHandler redoAction;

  private IUndoContext undoContext;

  private SourceViewer sourceViewer;

  private Map<String, IUpdate> textActions = new HashMap<String, IUpdate>();

  private PrintItAction printItAction;
  private InspectItAction inspectItAction;

  private IMemento memento;

  private HistoryList<ObjectInspectorHistoryItem> historyList = new HistoryList<ObjectInspectorHistoryItem>();

  static Object EXPRESSION_EVAL_JOB_FAMILY = new Object();

  private static final String SASH_WEIGHTS = "sashWeights";

  private static final String COLUMN_WIDTHS = "columnWidths";

  public ObjectInspectorView() {

  }

  @Override
  public void createPartControl(Composite parent) {
    DebugPlugin.getDefault().addDebugEventListener(this);
    getDebugContextService().addDebugContextListener(this);

    sashForm = new SashForm(parent, SWT.VERTICAL);

    treeViewer = new TreeViewer(sashForm, SWT.SINGLE | SWT.V_SCROLL | SWT.FULL_SELECTION
        | SWT.BORDER);
    treeViewer.setAutoExpandLevel(2);
    treeViewer.setLabelProvider(new NameLabelProvider());
    treeViewer.setContentProvider(new ObjectInspectorContentProvider());
    treeViewer.getTree().setHeaderVisible(true);
    treeViewer.getTree().setLinesVisible(true);
    treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        ISelection sel = event.getSelection();
        Object object = SelectionUtil.getSingleElement(sel);

        if (object instanceof IVariable) {
          IVariable variable = (IVariable) object;

          try {
            presentation.computeDetail(variable.getValue(), new IValueDetailListener() {
              @Override
              public void detailComputed(IValue value, String result) {
                updateStatusLine(result);
              }
            });
          } catch (DebugException e) {
            DartDebugUIPlugin.logError(e);
          }
        } else {
          clearStatusLine();
        }
      }
    });
    treeViewer.addDoubleClickListener(new IDoubleClickListener() {
      @Override
      public void doubleClick(DoubleClickEvent event) {
        toggleExpansion(event.getSelection());
      }
    });
    getSite().setSelectionProvider(treeViewer);

    TreeViewerColumn nameColumn = new TreeViewerColumn(treeViewer, SWT.LEFT);
    nameColumn.setLabelProvider(new NameLabelProvider());
    nameColumn.getColumn().setText("Name");
    nameColumn.getColumn().setWidth(120);
    nameColumn.getColumn().setResizable(true);

    TreeViewerColumn valueColumn = new TreeViewerColumn(treeViewer, SWT.LEFT);
    valueColumn.setLabelProvider(new ValueLabelProvider());
    valueColumn.getColumn().setText("Value");
    valueColumn.getColumn().setWidth(140);
    valueColumn.getColumn().setResizable(true);

    Composite sourceContainer = new Composite(sashForm, SWT.NONE);
    GridLayoutFactory.fillDefaults().applyTo(sourceContainer);
    Label label = new Label(sourceContainer, SWT.NONE);
    label.setText("Enter expression to evaluate:");
    GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(label);

    sourceViewer = new SourceViewer(sourceContainer, null, SWT.V_SCROLL | SWT.WRAP | SWT.BORDER);
    sourceViewer.configure(getSourceViewerConfiguration());
    sourceViewer.setDocument(createDocument(), new AnnotationModel());
    sourceViewer.setUndoManager(new TextViewerUndoManager(100));
    sourceViewer.getTextWidget().setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
    sourceViewer.getTextWidget().setTabs(2);
    sourceViewer.getTextWidget().addVerifyKeyListener(new VerifyKeyListener() {
      @Override
      public void verifyKey(VerifyEvent e) {
        // If they hit enter, but were not holding down ctrl or shift, then evaluate.
        if (e.character == SWT.CR && ((e.stateMask & (SWT.CTRL | SWT.SHIFT)) == 0)) {
          // Cancel the return char.
          e.doit = false;

          if (getCurrentSelection().isEmpty()) {
            evaluateAndPrint(getCurrentLine());
          } else {
            evaluateAndPrint(getCurrentSelection());
          }
        }
      }
    });
    GridDataFactory.fillDefaults().grab(true, true).align(SWT.FILL, SWT.FILL).applyTo(
        sourceViewer.getControl());
    sourceViewer.getDocument().addDocumentListener(new IDocumentListener() {
      @Override
      public void documentAboutToBeChanged(DocumentEvent event) {

      }

      @Override
      public void documentChanged(DocumentEvent event) {
        updateActions();
      }
    });
    sourceViewer.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        updateActions();
      }
    });

    sourceViewer.activatePlugins();

    createActions();
    createGlobalActionHandlers();
    hookContextMenu();
    updateActions();

    configureToolBar(getViewSite().getActionBars().getToolBarManager());

    sashForm.setWeights(new int[] {60, 40});
    sashForm.addControlListener(new ControlAdapter() {
      @Override
      public void controlResized(ControlEvent e) {
        updateSashOrientation(sashForm);
      }
    });

    restoreState(memento);

    updateSashOrientation(sashForm);

    historyList.addListener(new HistoryListListener<ObjectInspectorHistoryItem>() {
      @Override
      public void historyAboutToChange(final ObjectInspectorHistoryItem current) {
        Display.getDefault().syncExec(new Runnable() {
          @Override
          public void run() {
            saveSourceViewerInfo(current);
          }
        });
      }

      @Override
      public void historyChanged(ObjectInspectorHistoryItem current) {
        Display.getDefault().asyncExec(new Runnable() {
          @Override
          public void run() {
            ObjectInspectorHistoryItem item = historyList.getCurrent();

            inspectValueImpl((item != null && item.getValue() != null) ? item.getValue() : null);

            restoreSourceViewerInfo(item);
          }
        });
      }
    });
  }

  @Override
  public void debugContextChanged(DebugContextEvent event) {
    syncDebugContext();
  }

  @Override
  public void dispose() {
    DebugPlugin.getDefault().removeDebugEventListener(this);
    getDebugContextService().removeDebugContextListener(this);

    super.dispose();
  }

  @Override
  public void handleDebugEvents(DebugEvent[] events) {
    for (DebugEvent event : events) {
      if (event.getKind() == DebugEvent.TERMINATE && event.getSource() instanceof IDebugTarget) {
        syncDebugContext();

        handleDebugTargetTerminated((IDebugTarget) event.getSource());
      }
    }
  }

  @Override
  public void init(IViewSite site, IMemento memento) throws PartInitException {
    super.init(site, memento);

    this.memento = memento;

    IWorkbenchSiteProgressService progressService = (IWorkbenchSiteProgressService) getViewSite().getAdapter(
        IWorkbenchSiteProgressService.class);

    if (progressService != null) {
      initProgressService(progressService);
    }
  }

  public void restoreState(IMemento memento) {
    if (memento != null) {
      if (memento.getString(SASH_WEIGHTS) != null) {
        String[] strs = memento.getString(SASH_WEIGHTS).split(",");

        if (strs.length == 2) {
          try {
            sashForm.setWeights(new int[] {Integer.parseInt(strs[0]), Integer.parseInt(strs[1])});
          } catch (NumberFormatException ex) {

          }
        }
      }

      if (memento.getString(COLUMN_WIDTHS) != null) {
        String[] strs = memento.getString(COLUMN_WIDTHS).split(",");

        for (int i = 0; i < strs.length; i++) {
          try {
            treeViewer.getTree().getColumn(i).setWidth(Integer.parseInt(strs[i]));
          } catch (NumberFormatException ex) {

          }
        }
      }
    }
  }

  @Override
  public void saveState(IMemento memento) {
    super.saveState(memento);

    // Save the sash weights.
    int[] weights = sashForm.getWeights();
    memento.putString(SASH_WEIGHTS, weights[0] + "," + weights[1]);

    // Save the table columns.
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < treeViewer.getTree().getColumnCount(); i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(treeViewer.getTree().getColumn(i).getWidth());
    }
    memento.putString(COLUMN_WIDTHS, builder.toString());
  }

  @Override
  public void setFocus() {
    sourceViewer.getControl().setFocus();
  }

  protected void addTextAction(ActionFactory actionFactory, int textOperation) {
    IWorkbenchWindow window = getViewSite().getWorkbenchWindow();
    IWorkbenchAction globalAction = actionFactory.create(window);

    // Create our text action.
    TextViewerAction textAction = new TextViewerAction(textOperation);

    textActions.put(actionFactory.getId(), textAction);

    // Copy its properties from the global action.
    textAction.setText(globalAction.getText());
    textAction.setToolTipText(globalAction.getToolTipText());
    textAction.setDescription(globalAction.getDescription());
    textAction.setImageDescriptor(globalAction.getImageDescriptor());
    textAction.setDisabledImageDescriptor(globalAction.getDisabledImageDescriptor());
    textAction.setAccelerator(globalAction.getAccelerator());

    // Make sure it's up to date.
    textAction.update();

    // Register our text action with the global action handler.
    IActionBars actionBars = getViewSite().getActionBars();

    actionBars.setGlobalActionHandler(actionFactory.getId(), textAction);
  }

  protected void clearStatusLine() {
    setStatusLine(null);
  }

  protected void configureToolBar(IToolBarManager manager) {
    manager.add(HistoryAction.createForwardAction(historyList));
    manager.add(HistoryAction.createBackAction(historyList));
    manager.add(new Separator());
    manager.add(printItAction);
    manager.add(inspectItAction);

    manager.update(true);
  }

  protected void evaluateAndPrint(String expression) {
    IDartDebugValue value = getValue();

    if (value == null) {
      return;
    }

    Job job = new ExpressionEvaluateJob(getValue(), expression, new ExpressionListener() {
      @Override
      public void watchEvaluationFinished(IWatchExpressionResult result, String stringValue) {
        if (result.hasErrors()) {
          displayError(result);
        } else {
          displayResult(stringValue);
          resetCurrentValue();
        }
      }
    });

    job.schedule();
  }

  protected void fillContextMenu(IMenuManager manager) {
    manager.add(printItAction);
    manager.add(inspectItAction);
  }

  protected IAction getInspectItAction() {
    return inspectItAction;
  }

  protected IAction getPrintItAction() {
    return printItAction;
  }

  protected String getTextToEval() {
    if (getCurrentSelection().isEmpty()) {
      return getCurrentLine();
    } else {
      return getCurrentSelection();
    }
  }

  protected boolean hasActiveConnection() {
    IDartDebugValue value = getValue();

    if (value == null) {
      return false;
    }

    return !value.getDebugTarget().isTerminated();
  }

  protected void initProgressService(IWorkbenchSiteProgressService progressService) {
    progressService.showBusyForFamily(EXPRESSION_EVAL_JOB_FAMILY);
  }

  protected void inspectValue(IValue value) {
    historyList.add(new ObjectInspectorHistoryItem(value));
  }

  protected void inspectValueImpl(IValue value) {
    if (value == null) {
      treeViewer.setInput(new Object[] {});
    } else {
      presentation.computeDetail(value, new IValueDetailListener() {
        @Override
        public void detailComputed(final IValue value, final String result) {
          Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
              try {
                String typeName = value.getReferenceTypeName();

                treeViewer.setInput(new Object[] {new InspectorVariable(typeName, value)});
              } catch (DebugException e) {

              }
            }
          });
        }
      });
    }
  }

  protected void performEvaulation() {
    printItAction.run();
  }

  protected void removeTerminated() {
    historyList.removeMatching(new HistoryListMatcher<ObjectInspectorHistoryItem>() {
      @Override
      public boolean matches(ObjectInspectorHistoryItem value) {
        return value.getValue().getDebugTarget().isTerminated();
      }
    });
  }

  protected void setStatusLine(String message) {
    getViewSite().getActionBars().getStatusLineManager().setMessage(message);
  }

  protected void toggleExpansion(ISelection selection) {
    if (selection instanceof IStructuredSelection) {
      Object sel = ((IStructuredSelection) selection).getFirstElement();

      boolean expanded = treeViewer.getExpandedState(sel);

      if (expanded) {
        treeViewer.collapseToLevel(sel, 1);
      } else {
        treeViewer.expandToLevel(sel, 1);
      }
    }
  }

  void updateSashOrientation(SashForm sash) {
    int orientation = SWT.VERTICAL;

    if (sash.getOrientation() != orientation) {
      sash.setOrientation(orientation);
    }
  }

  private void createActions() {
    printItAction = new PrintItAction();
    inspectItAction = new InspectItAction();

    textActions.put(printItAction.getId(), printItAction);
    textActions.put(inspectItAction.getId(), inspectItAction);
  }

  private IDocument createDocument() {
    IDocument document = new Document();
    IDocumentSetupParticipant setupParticipant = new DartDocumentSetupParticipant();

    setupParticipant.setup(document);

    return document;
  }

  private void createGlobalActionHandlers() {
    undoContext = ((IUndoManagerExtension) sourceViewer.getUndoManager()).getUndoContext();

    // set up action handlers that operate on the current context
    undoAction = new UndoActionHandler(getSite(), undoContext);
    redoAction = new RedoActionHandler(getSite(), undoContext);

    IActionBars actionBars = getViewSite().getActionBars();

    actionBars.setGlobalActionHandler(ActionFactory.UNDO.getId(), undoAction);
    actionBars.setGlobalActionHandler(ActionFactory.REDO.getId(), redoAction);

    // Install the standard text actions.
    addTextAction(ActionFactory.CUT, ITextOperationTarget.CUT);
    addTextAction(ActionFactory.COPY, ITextOperationTarget.COPY);
    addTextAction(ActionFactory.PASTE, ITextOperationTarget.PASTE);
    addTextAction(ActionFactory.DELETE, ITextOperationTarget.DELETE);
    addTextAction(ActionFactory.SELECT_ALL, ITextOperationTarget.SELECT_ALL);
  }

  private void displayError(final IWatchExpressionResult result) {
    displayResult(result.getErrorMessages()[0]);
  }

  private void displayResult(final String result) {
    Display.getDefault().asyncExec(new Runnable() {
      @Override
      public void run() {
        IDocument document = sourceViewer.getDocument();

        String insert = result;

        if (insert == null) {
          insert = "null";
        }

        try {
          String current = document.get();

          insert = "  " + insert;

          if (!current.endsWith("\n")) {
            insert = "\n" + insert;
          }

          if (!insert.endsWith("\n")) {
            insert += "\n";
          }

          document.replace(document.getLength(), 0, insert);
          sourceViewer.setSelection(new TextSelection(document.getLength(), 0), true);
        } catch (BadLocationException e) {
          DartDebugUIPlugin.logError(e);
        }
      }
    });
  }

  private String getCurrentLine() {
    try {
      Point sel = sourceViewer.getSelectedRange();
      IDocument document = sourceViewer.getDocument();

      int line = document.getLineOfOffset(sel.x);

      int startOffset = document.getLineOffset(line);
      int lineLength = document.getLineLength(line);

      String text = document.get(startOffset, lineLength);

      return text.trim();
    } catch (BadLocationException ble) {
      return null;
    }
  }

  private String getCurrentSelection() {
    return ((ITextSelection) sourceViewer.getSelection()).getText();
  }

  private IDebugContextService getDebugContextService() {
    return DebugUITools.getDebugContextManager().getContextService(getSite().getWorkbenchWindow());
  }

  private SourceViewerConfiguration getSourceViewerConfiguration() {
    DartTextTools textTools = DartToolsPlugin.getDefault().getDartTextTools();

    return new DartSourceViewerConfiguration(
        textTools.getColorManager(),
        DartToolsPlugin.getDefault().getCombinedPreferenceStore(),
        null,
        DartPartitions.DART_PARTITIONING) {
    };
  }

  private IDartDebugValue getValue() {
    Object input = treeViewer.getInput();

    if (input instanceof Object[]) {
      try {
        Object[] inputArray = (Object[]) input;

        if (inputArray.length > 0) {
          IVariable variable = (IVariable) inputArray[0];

          return (IDartDebugValue) variable.getValue();
        }
      } catch (DebugException e) {

      }
    }

    return null;
  }

  private void handleDebugTargetTerminated(IDebugTarget target) {
    removeTerminated();
  }

  private void hookContextMenu() {
    // treeViewer context menu
    MenuManager treeMenuManager = new MenuManager("#PopupMenu");
    treeMenuManager.add(new Separator("additions1"));
    treeMenuManager.add(new Separator("additions2"));
    treeMenuManager.add(new Separator("additions3"));

    //treeMenuManager.setRemoveAllWhenShown(true);

    Menu menu = treeMenuManager.createContextMenu(treeViewer.getControl());
    treeViewer.getControl().setMenu(menu);
    getSite().registerContextMenu(treeMenuManager, treeViewer);

    // treeViewer context menu
    MenuManager textMenuManager = new MenuManager("#SourcePopupMenu", "#SourcePopupMenu");

    textMenuManager.setRemoveAllWhenShown(true);
    textMenuManager.addMenuListener(new IMenuListener() {
      @Override
      public void menuAboutToShow(IMenuManager manager) {
        ObjectInspectorView.this.fillContextMenu(manager);
      }
    });

    menu = textMenuManager.createContextMenu(sourceViewer.getControl());
    sourceViewer.getControl().setMenu(menu);
    getSite().registerContextMenu(textMenuManager.getId(), textMenuManager, sourceViewer);
  }

  private void resetCurrentValue() {
    IDartDebugValue value = getValue();

    if (value != null) {
      value.reset();

      inspectValueImpl(value);
    }
  }

  private void restoreSourceViewerInfo(ObjectInspectorHistoryItem item) {
    sourceViewer.getDocument().set(item == null ? "" : item.getText());

    if (item != null) {
      if (item.getSelection() != null) {
        sourceViewer.setSelection(item.getSelection());
      }
      sourceViewer.setTopIndex(item.getTopIndex());
    }
  }

  private void saveSourceViewerInfo(ObjectInspectorHistoryItem item) {
    item.setText(sourceViewer.getDocument().get());
    item.setSelection(sourceViewer.getSelection());
    item.setTopIndex(sourceViewer.getTopIndex());
  }

  private void syncDebugContext() {
    Display display = Display.getDefault();

    if (display != null) {
      display.asyncExec(new Runnable() {
        @Override
        public void run() {
          Object context = null;
          ISelection sel = getDebugContextService().getActiveContext();

          if (sel instanceof IStructuredSelection) {
            context = ((IStructuredSelection) sel).getFirstElement();
          }

          @SuppressWarnings("unused")
          IThread isolate = AdapterUtilities.getAdapter(context, IThread.class);

          updateActions();
        }
      });
    }
  }

  private void updateActions() {
    for (IUpdate action : textActions.values()) {
      action.update();
    }
  }

  private void updateStatusLine(final String message) {
    Display display = Display.getDefault();

    if (display != null) {
      display.asyncExec(new Runnable() {
        @Override
        public void run() {
          setStatusLine(message);
        }
      });
    }
  }

}

// Copyright (c) 2014, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

library trydart.interaction_manager;

import 'dart:html';

import 'dart:async' show
    Timer;

import 'dart:convert' show
    JSON;

import 'dart:math' show
    max,
    min;

import '../../../sdk/lib/_internal/compiler/implementation/scanner/scannerlib.dart'
  show
    EOF_TOKEN,
    StringScanner,
    Token;

import '../../../sdk/lib/_internal/compiler/implementation/source_file.dart' show
    StringSourceFile;

import 'compilation.dart' show
    scheduleCompilation;

import 'ui.dart' show
    applyingSettings,
    currentTheme,
    hackDiv,
    inputPre,
    observer,
    outputDiv;

import 'decoration.dart' show
    CodeCompletionDecoration,
    Decoration,
    DiagnosticDecoration,
    error,
    info,
    warning;

import 'editor.dart' as editor;

import 'mock.dart' as mock;

/**
 * UI interaction manager for the entire application.
 */
abstract class InteractionManager {
  // Design note: All UI interactions go through one instance of this
  // class. This is by design.
  //
  // Simplicity in UI is in the eye of the beholder, not the implementor. Great
  // 'natural UI' is usually achieved with substantial implementation
  // complexity that doesn't modularise well and has nasty complicated state
  // dependencies.
  //
  // In rare cases, some UI components can be independent of this state
  // machine. For example, animation and auto-save loops.

  // Implementation note: The state machine is actually implemented by
  // [InteractionContext], this class represents public event handlers.

  factory InteractionManager() => new InteractionContext();

  InteractionManager.internal();

  void onInput(Event event);

  void onKeyUp(KeyboardEvent event);

  void onMutation(List<MutationRecord> mutations, MutationObserver observer);

  void onSelectionChange(Event event);
}

/**
 * State machine for UI interactions.
 */
class InteractionContext extends InteractionManager {
  InteractionState state;

  InteractionContext()
      : super.internal() {
    state = new InitialState(this);
  }

  void onInput(Event event) => state.onInput(event);

  void onKeyUp(KeyboardEvent event) => state.onKeyUp(event);

  void onMutation(List<MutationRecord> mutations, MutationObserver observer) {
    return state.onMutation(mutations, observer);
  }

  void onSelectionChange(Event event) => state.onSelectionChange(event);
}

abstract class InteractionState implements InteractionManager {
  void onStateChanged(InteractionState previous) {
    print('State change ${previous.runtimeType} -> ${runtimeType}.');
  }
}

class InitialState extends InteractionState {
  final InteractionContext context;
  bool requestCodeCompletion = false;

  InitialState(this.context);

  void set state(InteractionState state) {
    InteractionState previous = context.state;
    if (previous != state) {
      context.state = state;
      state.onStateChanged(previous);
    }
  }

  void onInput(Event event) {
    state = new PendingInputState(context);
  }

  void onKeyUp(KeyboardEvent event) {
    print('onKeyUp');
    switch (event.keyCode) {
      case KeyCode.ENTER: {
        event.preventDefault();
        Selection selection = window.getSelection();
        if (selection.isCollapsed && selection.anchorNode is Text) {
          Text text = selection.anchorNode;
          int offset = selection.anchorOffset;
          text.insertData(offset, '\n');
          selection.collapse(text, offset + 1);
        }
        break;
      }
    }

    // editor.scheduleRemoveCodeCompletion();

    // This is a hack to get Safari (iOS) to send mutation events on
    // contenteditable.
    // TODO(ahe): Move to onInput?
    var newDiv = new DivElement();
    hackDiv.replaceWith(newDiv);
    hackDiv = newDiv;
  }

  // TODO(ahe): This method should be cleaned up. It is too large.
  void onMutation(List<MutationRecord> mutations, MutationObserver observer) {
    print('onMutation');

    for (Element element in inputPre.queryAll('a.diagnostic>span')) {
      element.remove();
    }
    for (Element element in inputPre.queryAll('.dart-code-completion')) {
      element.remove();
    }
    for (Element element in inputPre.queryAll('.hazed-suggestion')) {
      element.remove();
    }

    // Discard clean-up mutations.
    observer.takeRecords();

    Selection selection = window.getSelection();

    while (!mutations.isEmpty) {
      for (MutationRecord record in mutations) {
        String type = record.type;
        switch (type) {

          case 'characterData':

            bool hasSelection = false;
            int offset = selection.anchorOffset;
            if (selection.isCollapsed && selection.anchorNode == record.target) {
              hasSelection = true;
            }
            var parent = record.target.parentNode;
            if (parent != inputPre) {
              editor.inlineChildren(parent);
            }
            if (hasSelection) {
              selection.collapse(record.target, offset);
            }
            break;

          default:
            if (!record.addedNodes.isEmpty) {
              for (var node in record.addedNodes) {

                if (node.nodeType != Node.ELEMENT_NODE) continue;

                if (node is BRElement) {
                  if (selection.anchorNode != node) {
                    node.replaceWith(new Text('\n'));
                  }
                } else {
                  var parent = node.parentNode;
                  if (parent == null) continue;
                  var nodes = new List.from(node.nodes);
                  var style = node.getComputedStyle();
                  if (style.display != 'inline') {
                    var previous = node.previousNode;
                    if (previous is Text) {
                      previous.appendData('\n');
                    } else {
                      parent.insertBefore(new Text('\n'), node);
                    }
                  }
                  for (Node child in nodes) {
                    child.remove();
                    parent.insertBefore(child, node);
                  }
                  node.remove();
                }
              }
            }
        }
      }
      mutations = observer.takeRecords();
    }

    if (!inputPre.nodes.isEmpty && inputPre.nodes.last is Text) {
      Text text = inputPre.nodes.last;
      if (!text.text.endsWith('\n')) {
        text.appendData('\n');
      }
    }

    int offset = 0;
    int anchorOffset = 0;
    bool hasSelection = false;
    Node anchorNode = selection.anchorNode;
    // TODO(ahe): Try to share walk4 methods.
    void walk4(Node node) {
      // TODO(ahe): Use TreeWalker when that is exposed.
      // function textNodesUnder(root){
      //   var n, a=[], walk=document.createTreeWalker(
      //       root,NodeFilter.SHOW_TEXT,null,false);
      //   while(n=walk.nextNode()) a.push(n);
      //   return a;
      // }
      int type = node.nodeType;
      if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
        CharacterData text = node;
        if (anchorNode == node) {
          hasSelection = true;
          anchorOffset = selection.anchorOffset + offset;
          return;
        }
        offset += text.length;
      }

      var child = node.firstChild;
      while (child != null) {
        walk4(child);
        if (hasSelection) return;
        child = child.nextNode;
      }
    }
    if (selection.isCollapsed) {
      walk4(inputPre);
    }

    editor.currentSource = inputPre.text;
    inputPre.nodes.clear();
    inputPre.appendText(editor.currentSource);
    if (hasSelection) {
      selection.collapse(inputPre.firstChild, anchorOffset);
    }

    editor.isMalformedInput = false;
    for (var n in new List.from(inputPre.nodes)) {
      if (n is! Text) continue;
      Text node = n;
      String text = node.text;

      Token token = new StringScanner(
          new StringSourceFile('', text), includeComments: true).tokenize();
      int offset = 0;
      editor.seenIdentifiers = new Set<String>.from(mock.identifiers);
      for (;token.kind != EOF_TOKEN; token = token.next) {
        Decoration decoration = editor.getDecoration(token);
        if (decoration == null) continue;
        bool hasSelection = false;
        int selectionOffset = selection.anchorOffset;

        if (selection.isCollapsed && selection.anchorNode == node) {
          hasSelection = true;
          selectionOffset = selection.anchorOffset;
        }
        int splitPoint = token.charOffset - offset;
        Text str = node.splitText(splitPoint);
        Text after = str.splitText(token.charCount);
        offset += splitPoint + token.charCount;
        inputPre.insertBefore(after, node.nextNode);
        inputPre.insertBefore(decoration.applyTo(str), after);

        if (hasSelection && selectionOffset > node.length) {
          selectionOffset -= node.length;
          if (selectionOffset > str.length) {
            selectionOffset -= str.length;
            selection.collapse(after, selectionOffset);
          } else {
            selection.collapse(str, selectionOffset);
          }
        }
        node = after;
      }
    }

    window.localStorage['currentSource'] = editor.currentSource;

    // Discard highlighting mutations.
    observer.takeRecords();
  }

  void onSelectionChange(Event event) {
  }

  void showCodeCompletion(Element parent, Element ui) {
  }
}

class PendingInputState extends InitialState {
  PendingInputState(InteractionContext context)
      : super(context);

  void onInput(Event event) {
    // Do nothing.
  }

  void onMutation(List<MutationRecord> mutations, MutationObserver observer) {
    super.onMutation(mutations, observer);

    InteractionState nextState = new InitialState(context);

    Element parent = editor.getElementAtSelection();
    Element ui;
    if (parent != null) {
      ui = parent.query('.dart-code-completion');
      if (ui != null) {
        nextState = new CodeCompletionState(context, parent, ui);
      }
    }
    state = nextState;
  }
}

class CodeCompletionState extends InitialState {
  final Element activeCompletion;
  final Element ui;
  int minWidth = 0;
  DivElement staticResults;
  SpanElement inline;
  DivElement serverResults;
  String inlineSuggestion;

  CodeCompletionState(InteractionContext context,
                      this.activeCompletion,
                      this.ui)
      : super(context);

  void onInput(Event event) {
    // Do nothing.
  }

  void onKeyUp(KeyboardEvent event) {
    switch (event.keyCode) {
      case KeyCode.DOWN:
        event.preventDefault();
        editor.moveActive(1);
        return;

      case KeyCode.UP:
        event.preventDefault();
        editor.moveActive(-1);
        return;

      case KeyCode.ESC:
        event.preventDefault();
        return endCompletion();

      case KeyCode.TAB:
      case KeyCode.RIGHT:
      case KeyCode.ENTER:
        event.preventDefault();
        return endCompletion(acceptSuggestion: true);
    }
  }

  void endCompletion({bool acceptSuggestion: false}) {
    if (acceptSuggestion) {
      suggestionAccepted();
    }
    editor.removeCodeCompletion();
    state = new InitialState(context);
  }

  void suggestionAccepted() {
    if (inlineSuggestion != null) {
      Text text = new Text(inlineSuggestion);
      activeCompletion.replaceWith(text);
      window.getSelection().collapse(text, inlineSuggestion.length);
    }
  }

  void onMutation(List<MutationRecord> mutations, MutationObserver observer) {
    for (MutationRecord record in mutations) {
      if (!activeCompletion.contains(record.target)) {
        endCompletion();
        return super.onMutation(mutations, observer);
      }
    }

    var text = activeCompletion.firstChild;
    if (text is! Text) return endCompletion();
    updateSuggestions(text.data.trim());
  }

  void onStateChanged(InteractionState previous) {
    super.onStateChanged(previous);
    displayCodeCompletion();
  }

  void displayCodeCompletion() {
    Selection selection = window.getSelection();
    if (selection.anchorNode is! Text) {
      return endCompletion();
    }
    Text text = selection.anchorNode;
    if (!activeCompletion.contains(text)) {
      return endCompletion();
    }

    int anchorOffset = selection.anchorOffset;

    String prefix = text.data.substring(0, anchorOffset).trim();
    if (prefix.isEmpty) {
      return endCompletion();
    }

    num height = activeCompletion.getBoundingClientRect().height;
    activeCompletion.classes.add('active');
    ui.nodes.clear();

    inline = new SpanElement()
        ..classes.add('hazed-suggestion');
    Text rest = text.splitText(anchorOffset);
    text.parentNode.insertBefore(inline, text.nextNode);
    activeCompletion.parentNode.insertBefore(
        rest, activeCompletion.nextNode);

    staticResults = new DivElement()
        ..classes.addAll(['dart-static', 'dart-limited-height']);
    serverResults = new DivElement()
        ..style.display = 'none'
        ..classes.add('dart-server');
    ui.nodes.addAll([staticResults, serverResults]);
    ui.style.top = '${height}px';

    staticResults.nodes.add(buildCompletionEntry(prefix));

    updateSuggestions(prefix);
  }

  void updateInlineSuggestion(String prefix, String suggestion) {
    inlineSuggestion = suggestion;

    minWidth = max(minWidth, activeCompletion.getBoundingClientRect().width);

    activeCompletion.style
        ..display = 'inline-block'
        ..minWidth = '${minWidth}px';

    inline
        ..nodes.clear()
        ..appendText(suggestion.substring(prefix.length))
        ..style.display = '';

    observer.takeRecords(); // Discard mutations.
  }

  void updateSuggestions(String prefix) {
    if (prefix.isEmpty) {
      return endCompletion();
    }

    var borderHeight = 2; // 1 pixel border top & bottom.
    num height = ui.getBoundingClientRect().height - borderHeight;
    ui.style.minHeight = '${height}px';

    minWidth =
        max(minWidth, activeCompletion.getBoundingClientRect().width);

    staticResults.nodes.clear();
    serverResults.nodes.clear();

    if (inlineSuggestion != null && inlineSuggestion.startsWith(prefix)) {
      inline
          ..nodes.clear()
          ..appendText(inlineSuggestion.substring(prefix.length));
    }

    List<String> results = editor.seenIdentifiers.where(
        (String identifier) => identifier != prefix && identifier.startsWith(prefix))
        .toList(growable: false)
        ..sort();
    if (results.isEmpty) results = <String>[prefix];

    results.forEach((String completion) {
      staticResults.nodes.add(buildCompletionEntry(completion));
    });

    String encodedArg0 = Uri.encodeComponent('"$prefix"');
    String mindQuery =
        'http://dart-mind.appspot.com/rpc'
        '?action=GetExportingPubCompletions'
        '&arg0=$encodedArg0';
    try {
      var serverWatch = new Stopwatch()..start();
      HttpRequest.getString(mindQuery).then((String responseText) {
        serverWatch.stop();
        List<String> serverSuggestions = JSON.decode(responseText);
        if (!serverSuggestions.isEmpty) {
          updateInlineSuggestion(prefix, serverSuggestions.first);
        }
        for (int i = 1; i < serverSuggestions.length; i++) {
          String completion = serverSuggestions[i];
          DivElement where = staticResults;
          int index = results.indexOf(completion);
          if (index != -1) {
            List<Element> entries =
                document.querySelectorAll('.dart-static>.dart-entry');
            entries[index].classes.add('doubleplusgood');
          } else {
            if (results.length > 3) {
              serverResults.style.display = 'block';
              where = serverResults;
            }
            where.nodes.add(buildCompletionEntry(completion));
          }
        }
        serverResults.appendHtml('<div>${serverWatch.elapsedMilliseconds}ms</div>');
        // Discard mutations.
        observer.takeRecords();
      }).catchError((error, stack) {
        window.console.dir(error);
        window.console.error('$stack');
      });
    } catch (error, stack) {
      window.console.dir(error);
      window.console.error('$stack');
    }
    // Discard mutations.
    observer.takeRecords();



  }

  Element buildCompletionEntry(String completion) {
    return new DivElement()
        ..classes.add('dart-entry')
        ..appendText(completion);
  }
}

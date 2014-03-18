// Copyright (c) 2013, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

library trydart.editor;

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

import 'mock.dart' as mock;

const String INDENT = '\u{a0}\u{a0}';

Set<String> seenIdentifiers;

Element moveActive(int distance) {
  List<Element> entries = document.querySelectorAll('.dart-static>.dart-entry');
  int activeIndex = -1;
  for (var i = 0; i < entries.length; i++) {
    if (entries[i].classes.contains('activeEntry')) {
      activeIndex = i;
      break;
    }
  }
  int newIndex = activeIndex + distance;
  Element currentEntry;
  if (0 <= newIndex && newIndex < entries.length) {
    currentEntry = entries[newIndex];
  }
  if (currentEntry == null) return null;
  if (0 <= newIndex && activeIndex != -1) {
    entries[activeIndex].classes.remove('activeEntry');
  }
  Element staticNode = document.querySelector('.dart-static');
  String visibility = computeVisibility(currentEntry, staticNode);
  print(visibility);
  var serverResults = document.querySelectorAll('.dart-server>.dart-entry');
  var serverResultCount = serverResults.length;
  if (serverResultCount > 0) {
    switch (visibility) {
      case obscured:
      case hidden: {
        Rectangle cr = currentEntry.getBoundingClientRect();
        Rectangle sr = staticNode.getBoundingClientRect();
        Element entry = serverResults[0];
        entry.remove();
        currentEntry.parentNode.insertBefore(entry, currentEntry);
        currentEntry = entry;
        serverResultCount--;

        staticNode.style.maxHeight = '${sr.boundingBox(cr).height}px';
        // .style.maxHeight =
    //       "${cr.bottom - sr.top}px";

      }
    }
    // var cr = currentEntry.getBoundingClientRect();
    // var sr = document.querySelector('.dart-static').getBoundingClientRect();
    // if (cr.bottom - sr.top > sr.height) {
    //   if (cr.top - sr.top >= sr.height) {
    //     var entry = serverResults[0];
    //     entry.remove();
    //     currentEntry.parentNode.insertBefore(entry, currentEntry);
    //     currentEntry = entry;
    //     serverResultCount--;
    //   } else {
    //     window.console.log(cr.bottom - sr.top);
    //   }
    //   document.querySelector('.dart-static').style.maxHeight =
    //       "${cr.bottom - sr.top}px";
    // } else {
    //   currentEntry.scrollIntoView(ScrollAlignment.BOTTOM);
    // }
  } else {
    currentEntry.scrollIntoView(ScrollAlignment.BOTTOM);
  }
  if (serverResultCount == 0) {
    // document.querySelector('.dart-static').classes.remove('dart-limited-height');
    // document.querySelector('.dart-static').style.height = '';
    document.querySelector('.dart-server').style.display = 'none';
  }
  if (currentEntry != null) {
    currentEntry.classes.add('activeEntry');
  }
  // Discard mutations.
  observer.takeRecords();
  return currentEntry;
}

const visible = 'visible';
const obscured = 'obscured';
const hidden = 'hidden';

String computeVisibility(Element node, [Element parent]) {
  Rectangle nr = node.getBoundingClientRect();
  if (parent == null) parent = node.parentNode;
  Rectangle pr = parent.getBoundingClientRect();

  if (pr.containsRectangle(nr)) return visible;

  if (pr.intersects(nr)) return obscured;

  return hidden;
}

void removeCodeCompletion() {
  if (activeCompletion != null) {
    activeCompletion.classes.remove('active');
    activeCompletion = null;
  }
  inputPre.querySelectorAll('.hazed-suggestion').forEach((e) => e.remove());
}


var activeCompletion;
num minSuggestionWidth = 0;

/// Returns the [Element] which encloses the current collapsed selection, if it
/// exists.
Element getElementAtSelection() {
  Selection selection = window.getSelection();
  if (!selection.isCollapsed) return null;
  var anchorNode = selection.anchorNode;
  if (!inputPre.contains(anchorNode)) return null;
  if (inputPre == anchorNode) return null;
  int type = anchorNode.nodeType;
  if (type != Node.TEXT_NODE) return null;
  Text text = anchorNode;
  var parent = text.parent;
  if (parent is! Element) return null;
  if (inputPre == parent) return null;
  return parent;
}

void displayCodeCompletion() {
  throw 'broken';
  if (activeCompletion != null) {
    activeCompletion.classes.remove('active');
    activeCompletion = null;
  }
  Element parent = getElementAtSelection();
  if (parent == null) return;
  Selection selection = window.getSelection();
  Text text = selection.anchorNode;
  int anchorOffset = selection.anchorOffset;
  parent.classes.add('active');
  var ui = parent.query('.dart-code-completion');
  if (ui == null) return;

  String prefix = text.data.substring(0, anchorOffset).trim();
  print(prefix);
  if (prefix.isEmpty) {
    // Discard mutations.
    observer.takeRecords();
    return;
  }

  activeCompletion = parent;
  //observer.disconnect();
  ui.nodes.clear();

  SpanElement inline = new SpanElement()
      ..style.minWidth = '${minSuggestionWidth}px'
      ..classes.add('hazed-suggestion');
  if (minSuggestionWidth == 0) {
    inline.style.display = 'none';
  }
  Text rest = text.splitText(anchorOffset);
  text.parentNode.insertAllBefore([inline, rest], text.nextNode);

  DivElement staticResults = new DivElement()
      ..classes.addAll(['dart-static', 'dart-limited-height']);
  DivElement serverResults = new DivElement()
      ..style.display = 'none'
      ..classes.add('dart-server');
  ui.nodes.addAll([staticResults, serverResults]);

  List<String> results = seenIdentifiers.where(
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
        String suggestion = serverSuggestions.first.substring(prefix.length);
        if (!suggestion.isEmpty) {
          inline
              ..appendText(suggestion)
              ..style.display = ''
              ..style.minWidth = '${minSuggestionWidth}px';
          minSuggestionWidth =
              max(minSuggestionWidth, inline.getBoundingClientRect().width);
          print('minSuggestionWidth: $minSuggestionWidth');
        }
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

  //observer.observe(inputPre, childList: true, characterData: true, subtree: true);
}

bool isMalformedInput = false;
String currentSource = "";

addDiagnostic(String kind, String message, int begin, int end) {
  observer.disconnect();
  Selection selection = window.getSelection();
  int offset = 0;
  int anchorOffset = 0;
  bool hasSelection = false;
  Node anchorNode = selection.anchorNode;
  bool foundNode = false;
  void walk4(Node node) {
    // TODO(ahe): Use TreeWalker when that is exposed.
    int type = node.nodeType;
    if (type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE) {
      CharacterData cdata = node;
      // print('walking: ${node.data}');
      if (anchorNode == node) {
        hasSelection = true;
        anchorOffset = selection.anchorOffset + offset;
      }
      int newOffset = offset + cdata.length;
      if (offset <= begin && begin < newOffset) {
        hasSelection = node == anchorNode;
        anchorOffset = selection.anchorOffset;
        Node marker = new Text("");
        node.replaceWith(marker);
        // TODO(ahe): Don't highlight everything in the node.  Find the
        // relevant token (works for now as we create a node for each token,
        // which is probably not great for performance).
        if (kind == 'error') {
          marker.replaceWith(diagnostic(node, error(message)));
        } else if (kind == 'warning') {
          marker.replaceWith(diagnostic(node, warning(message)));
        } else {
          marker.replaceWith(diagnostic(node, info(message)));
        }
        if (hasSelection) {
          selection.collapse(node, anchorOffset);
        }
        foundNode = true;
        return;
      }
      offset = newOffset;
    } else if (type == Node.ELEMENT_NODE) {
      Element element = node;
      if (element.classes.contains('alert')) return;
    }

    var child = node.firstChild;
    while(child != null && !foundNode) {
      walk4(child);
      child = child.nextNode;
    }
  }
  walk4(inputPre);

  if (!foundNode) {
    outputDiv.appendText('$message\n');
  }

  observer.takeRecords();
  observer.observe(
      inputPre, childList: true, characterData: true, subtree: true);
}

void inlineChildren(Element element) {
  if (element == null) return;
  var parent = element.parentNode;
  if (parent == null) return;
  for (Node child in new List.from(element.nodes)) {
    child.remove();
    parent.insertBefore(child, element);
  }
  element.remove();
}

Decoration getDecoration(Token token) {
  String tokenValue = token.value;
  String tokenInfo = token.info.value;
  if (tokenInfo == 'string') return currentTheme.string;
  if (tokenInfo == 'identifier') {
    seenIdentifiers.add(tokenValue);
    return CodeCompletionDecoration.from(currentTheme.foreground);
  }
  if (tokenInfo == 'keyword') return currentTheme.keyword;
  if (tokenInfo == 'comment') return currentTheme.singleLineComment;
  if (tokenInfo == 'malformed input') {
    isMalformedInput = true;
    return new DiagnosticDecoration('error', tokenValue);
  }
  return currentTheme.foreground;
}

diagnostic(text, tip) {
  if (text is String) {
    text = new Text(text);
  }
  return new AnchorElement()
      ..classes.add('diagnostic')
      ..append(text)
      ..append(tip);
}

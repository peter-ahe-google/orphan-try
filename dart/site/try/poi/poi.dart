// Copyright (c) 2014, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

library trydart.poi;

import 'dart:async' show
    Future;

import 'package:dart2js_incremental/dart2js_incremental.dart' show
    reuseCompiler;

import 'package:dart2js_incremental/library_updater.dart' show
    LibraryUpdater;

import 'package:compiler/compiler.dart' as api;

import 'package:compiler/implementation/dart2jslib.dart' show
    Compiler,
    CompilerTask,
    Enqueuer,
    QueueFilter,
    WorkItem;

import 'package:compiler/implementation/elements/visitor.dart' show
    ElementVisitor;

import 'package:compiler/implementation/elements/elements.dart' show
    AbstractFieldElement,
    AstElement,
    ClassElement,
    CompilationUnitElement,
    Element,
    ElementCategory,
    FunctionElement,
    LibraryElement,
    ScopeContainerElement;

import 'package:compiler/implementation/elements/modelx.dart' as modelx;

import 'package:compiler/implementation/elements/modelx.dart' show
    DeclarationSite;

import 'package:compiler/implementation/dart_types.dart' show
    DartType;

import 'package:compiler/implementation/scanner/scannerlib.dart' show
    EOF_TOKEN,
    IDENTIFIER_TOKEN,
    KEYWORD_TOKEN,
    PartialClassElement,
    PartialElement,
    Token;

import 'package:compiler/implementation/js/js.dart' show
    js;

import 'package:compiler/implementation/tree/tree.dart' show
    Identifier,
    Node,
    Send,
    Visitor;

import 'package:compiler/implementation/resolution/resolution.dart' show
    TreeElements;

/// If file is missing, generate using:
///  ./sdk/bin/dart -Dlist_all_libraries=true sdk/lib/_internal/compiler/samples/jsonify/jsonify.dart site/try/poi/_generated_sdk.dart
import '_generated_sdk.dart' show
    SDK_SOURCES;

/// Enabled by the option --enable-dart-mind.  Controls if this program should
/// be querying Dart Mind.
bool isDartMindEnabled = false;

/// Iterator over lines from standard input (or the argument array).
Iterator<String> stdin;

/// Enabled by the option --simulate-mutation. When true, this program will
/// only prompt for one file name, and subsequent runs will read
/// FILENAME.N.dart, where N starts at 1, and is increased on each iteration.
/// For example, if the program is invoked as:
///
///   dart poi.dart --simulate-mutation test.dart 11 22 33 44
///
/// The program will first read the file 'test.dart' and compute scope
/// information about position 11, then position 22 in test.dart.1.dart, then
/// position 33 in test.dart.2.dart, and finally position 44 in
/// test.dart.3.dart.
bool isSimulateMutationEnabled = false;

/// Counts the number of times [runPoi] has been invoked.
int poiCount;

int globalCounter = 0;

/// Enabled by the option --verbose (or -v). Prints more information than you
/// really need.
bool isVerbose = false;

/// Enabled by the option --compile. Also compiles the program after analyzing
/// the POI.
bool isCompiler = false;

/// Enabled by the option --minify. Passes the same option to the compiler to
/// generate minified output.
bool enableMinification = false;

/// When true (the default value) print serialized scope information at the
/// provided position.
const bool PRINT_SCOPE_INFO =
    const bool.fromEnvironment('PRINT_SCOPE_INFO', defaultValue: true);

Stopwatch wallClock = new Stopwatch();

PoiTask poiTask;

Compiler cachedCompiler;

printFormattedTime(message, int us) {
  String m = '$message${" " * 65}'.substring(0, 60);
  String i = '${" " * 10}${(us/1000).toStringAsFixed(3)}';
  i = i.substring(i.length - 10);
  print('$m ${i}ms');
}

printWallClock(message) {
  if (!isVerbose) return;
  if (wallClock.isRunning) {
    print('$message');
    printFormattedTime('--->>>', wallClock.elapsedMicroseconds);
    wallClock.reset();
  } else {
    print(message);
  }
}

printVerbose(message) {
  if (!isVerbose) return;
  print(message);
}

/// Find the token corresponding to [position] in [element].  The method only
/// works for instances of [PartialElement] or [LibraryElement].  Support for
/// [LibraryElement] is currently limited, and works only for named libraries.
Token findToken(modelx.ElementX element, int position) {
  Token beginToken;
  DeclarationSite site = element.declarationSite;
  if (site is PartialElement) {
    beginToken = site.beginToken;
  } else if (element.isLibrary) {
    // TODO(ahe): Generalize support for library elements (and update above
    // documentation).
    modelx.LibraryElementX lib = element;
    var tag = lib.libraryTag;
    if (tag != null) {
      beginToken = tag.libraryKeyword;
    }
  } else {
    beginToken = element.position;
  }
  if (beginToken == null) return null;
  for (Token token = beginToken; token.kind != EOF_TOKEN; token = token.next) {
    if (token.charOffset < position && position <= token.next.charOffset) {
      return token;
    }
  }
  return null;
}

Future<Element> runPoi(
    Uri script,
    int position,
    api.CompilerInputProvider inputProvider,
    api.DiagnosticHandler handler,
    Uri libraryRoot,
    Uri packageRoot) {
  Stopwatch sw = new Stopwatch()..start();
  if (libraryRoot == null) {
    libraryRoot = Uri.base.resolve('sdk/');
  }

  var options = [
      '--analyze-main',
      '--no-source-maps',
      '--verbose',
      '--categories=Client,Server',
      '--incremental-support',
      '--disable-type-inference',
  ];

  if (!isCompiler) {
    options.add('--analyze-only');
  }

  if (enableMinification) {
    options.add('--minify');
  }

  LibraryUpdater updater =
      new LibraryUpdater(
          cachedCompiler, inputProvider, script, printWallClock, printVerbose);
  Future<bool> reuseLibrary(LibraryElement library) {
    return poiTask.measure(() => updater.reuseLibrary(library));
  }

  return reuseCompiler(
      diagnosticHandler: handler,
      inputProvider: inputProvider,
      options: options,
      cachedCompiler: cachedCompiler,
      libraryRoot: libraryRoot,
      packageRoot: packageRoot,
      packagesAreImmutable: true,
      reuseLibrary: reuseLibrary).then((Compiler newCompiler) {
    if (!isCompiler) {
      newCompiler.enqueuerFilter = new ScriptOnlyFilter(script);
    }
    return runPoiInternal(newCompiler, sw, updater, position);
  });
}

Future<Element> runPoiInternal(
    Compiler newCompiler,
    Stopwatch sw,
    LibraryUpdater updater,
    int position) {
  bool isFullCompile = cachedCompiler != newCompiler;
  cachedCompiler = newCompiler;
  if (poiTask == null || poiTask.compiler != cachedCompiler) {
    poiTask = new PoiTask(cachedCompiler);
    cachedCompiler.tasks.add(poiTask);
  }

  if (!isFullCompile) {
    printFormattedTime(
        'Analyzing changes and updating elements took', sw.elapsedMicroseconds);
  }
  sw.reset();

  Future<bool> compilation;

  if (updater.hasPendingUpdates) {
    compilation = new Future(() {
      if (isCompiler) {
        var node = js.statement(
            r'var $dart_patch = #',
            js.escapedString(updater.computeUpdateJs()));
        print(updater.prettyPrintJs(node));
      } else {
        updater.analyzeUpdates();
      }
      return !cachedCompiler.compilationFailed;
    });
  } else {
    compilation = cachedCompiler.run(updater.uri);
  }

  return compilation.then((success) {
    printVerbose('Compiler queue processed in ${sw.elapsedMicroseconds}us');
    if (isVerbose) {
      for (final task in cachedCompiler.tasks) {
        int time = task.timingMicroseconds;
        if (time != 0) {
          printFormattedTime('${task.name} took', time);
        }
      }
    }

    if (poiCount != null) poiCount++;
    if (success != true) {
      // throw 'Compilation failed';
    }
    return findPosition(position, cachedCompiler.mainApp);
  });
}

Element findPosition(int position, Element element) {
  FindPositionVisitor visitor = new FindPositionVisitor(position, element);
  element.accept(visitor);
  return visitor.element;
}

String scopeInformation(Element element, int position) {
  ScopeInformationVisitor visitor =
      new ScopeInformationVisitor(element, position);
  element.accept(visitor);
  return '${visitor.buffer}';
}

class FindPositionVisitor extends ElementVisitor {
  final int position;
  Element element;

  FindPositionVisitor(this.position, this.element);

  visitElement(modelx.ElementX e) {
    DeclarationSite site = e.declarationSite;
    if (site is PartialElement) {
      if (site.beginToken.charOffset <= position &&
          position < site.endToken.next.charOffset) {
        element = e;
      }
    }
  }

  visitClassElement(ClassElement e) {
    if (e is PartialClassElement) {
      if (e.beginToken.charOffset <= position &&
          position < e.endToken.next.charOffset) {
        element = e;
        visitScopeContainerElement(e);
      }
    }
  }

  visitScopeContainerElement(ScopeContainerElement e) {
    e.forEachLocalMember((Element element) => element.accept(this));
  }
}

class ScriptOnlyFilter implements QueueFilter {
  final Uri script;

  ScriptOnlyFilter(this.script);

  bool checkNoEnqueuedInvokedInstanceMethods(Enqueuer enqueuer) => true;

  void processWorkItem(void f(WorkItem work), WorkItem work) {
    if (work.element.library.canonicalUri == script) {
      f(work);
      printWallClock('Processed ${work.element}.');
    } else {
      printWallClock('Skipped ${work.element}.');
    }
  }
}

/**
 * Serializes scope information about an element. This is accomplished by
 * calling the [serialize] method on each element. Some elements need special
 * treatment, as their enclosing scope must also be serialized.
 */
class ScopeInformationVisitor extends ElementVisitor/* <void> */ {
  // TODO(ahe): Include function parameters and local variables.

  final Element currentElement;
  final int position;
  final StringBuffer buffer = new StringBuffer();
  int indentationLevel = 0;
  ClassElement currentClass;

  ScopeInformationVisitor(this.currentElement, this.position);

  String get indentation => '  ' * indentationLevel;

  StringBuffer get indented => buffer..write(indentation);

  void visitElement(Element e) {
    serialize(e, omitEnclosing: false);
  }

  void visitLibraryElement(LibraryElement e) {
    bool isFirst = true;
    forEach(Element member) {
      if (!isFirst) {
        buffer.write(',');
      }
      buffer.write('\n');
      indented;
      serialize(member);
      isFirst = false;
    }
    serialize(
        e,
        // TODO(ahe): We omit the import scope if there is no current
        // class. That's wrong.
        omitEnclosing: currentClass == null,
        name: e.getLibraryName(),
        serializeEnclosing: () {
          // The enclosing scope of a library is a scope which contains all the
          // imported names.
          isFirst = true;
          buffer.write('{\n');
          indentationLevel++;
          indented.write('"kind": "imports",\n');
          indented.write('"members": [');
          indentationLevel++;
          importScope(e).importScope.values.forEach(forEach);
          indentationLevel--;
          buffer.write('\n');
          indented.write('],\n');
          // The enclosing scope of the imported names scope is the superclass
          // scope of the current class.
          indented.write('"enclosing": ');
          serializeClassSide(
              currentClass.superclass, isStatic: false, includeSuper: true);
          buffer.write('\n');
          indentationLevel--;
          indented.write('}');
        },
        serializeMembers: () {
          isFirst = true;
          localScope(e).values.forEach(forEach);
        });
  }

  void visitClassElement(ClassElement e) {
    currentClass = e;
    serializeClassSide(e, isStatic: true);
  }

  /// Serializes one of the "sides" a class. The sides of a class are "instance
  /// side" and "class side". These terms are from Smalltalk. The instance side
  /// is all the local instance members of the class (the members of the
  /// mixin), and the class side is the equivalent for static members and
  /// constructors.
  /// The scope chain is ordered so that the "class side" is searched before
  /// the "instance side".
  void serializeClassSide(
      ClassElement e,
      {bool isStatic: false,
       bool omitEnclosing: false,
       bool includeSuper: false}) {
    bool isFirst = true;
    var serializeEnclosing;
    String kind;
    if (isStatic) {
      kind = 'class side';
      serializeEnclosing = () {
        serializeClassSide(e, isStatic: false, omitEnclosing: omitEnclosing);
      };
    } else {
      kind = 'instance side';
    }
    if (includeSuper) {
      assert(!omitEnclosing && !isStatic);
      if (e.superclass == null) {
        omitEnclosing = true;
      } else {
        // Members of the superclass are represented as a separate scope.
        serializeEnclosing = () {
          serializeClassSide(
              e.superclass, isStatic: false, omitEnclosing: false,
              includeSuper: true);
        };
      }
    }
    serialize(
        e, omitEnclosing: omitEnclosing, serializeEnclosing: serializeEnclosing,
        kind: kind, serializeMembers: () {
      e.forEachLocalMember((Element member) {
        // Filter out members that don't belong to this "side".
        if (member.isConstructor) {
          // In dart2js, some constructors aren't static, but that isn't
          // convenient here.
          if (!isStatic) return;
        } else if (member.isStatic != isStatic) {
          return;
        }
        if (!isFirst) {
          buffer.write(',');
        }
        buffer.write('\n');
        indented;
        serialize(member);
        isFirst = false;
      });
    });
  }

  void visitScopeContainerElement(ScopeContainerElement e) {
    bool isFirst = true;
    serialize(e, omitEnclosing: false, serializeMembers: () {
      e.forEachLocalMember((Element member) {
        if (!isFirst) {
          buffer.write(',');
        }
        buffer.write('\n');
        indented;
        serialize(member);
        isFirst = false;
      });
    });
  }

  void visitCompilationUnitElement(CompilationUnitElement e) {
    e.enclosingElement.accept(this);
  }

  void visitAbstractFieldElement(AbstractFieldElement e) {
    throw new UnsupportedError('AbstractFieldElement cannot be serialized.');
  }

  void serialize(
      Element element,
      {bool omitEnclosing: true,
       void serializeMembers(),
       void serializeEnclosing(),
       String kind,
       String name}) {
    if (element.isAbstractField) {
      AbstractFieldElement field = element;
      FunctionElement getter = field.getter;
      FunctionElement setter = field.setter;
      if (getter != null) {
        serialize(
            getter,
            omitEnclosing: omitEnclosing,
            serializeMembers: serializeMembers,
            serializeEnclosing: serializeEnclosing,
            kind: kind,
            name: name);
      }
      if (setter != null) {
        if (getter != null) {
          buffer.write(',\n');
          indented;
        }
        serialize(
            getter,
            omitEnclosing: omitEnclosing,
            serializeMembers: serializeMembers,
            serializeEnclosing: serializeEnclosing,
            kind: kind,
            name: name);
      }
      return;
    }
    DartType type;
    int category = element.kind.category;
    if (category == ElementCategory.FUNCTION ||
        category == ElementCategory.VARIABLE ||
        element.isConstructor) {
      type = element.computeType(cachedCompiler);
    }
    if (name == null) {
      name = element.name;
    }
    if (kind == null) {
      kind = '${element.kind}';
    }
    buffer.write('{\n');
    indentationLevel++;
    if (name != '') {
      indented
          ..write('"name": "')
          ..write(name)
          ..write('",\n');
    }
    indented
        ..write('"kind": "')
        ..write(kind)
        ..write('"');
    if (type != null) {
      buffer.write(',\n');
      indented
          ..write('"type": "')
          ..write(type)
          ..write('"');
    }
    if (serializeMembers != null) {
      buffer.write(',\n');
      indented.write('"members": [');
      indentationLevel++;
      serializeMembers();
      indentationLevel--;
      buffer.write('\n');
      indented.write(']');
    }
    if (!omitEnclosing) {
      buffer.write(',\n');
      indented.write('"enclosing": ');
      if (serializeEnclosing != null) {
        serializeEnclosing();
      } else {
        element.enclosingElement.accept(this);
      }
    }
    indentationLevel--;
    buffer.write('\n');
    indented.write('}');
  }
}

modelx.ScopeX localScope(modelx.LibraryElementX element) => element.localScope;

modelx.ImportScope importScope(modelx.LibraryElementX element) {
  return element.importScope;
}

class PoiTask extends CompilerTask {
  PoiTask(Compiler compiler) : super(compiler);

  String get name => 'POI';
}

ExpressionInfo findNode(AstElement element, Token token) {
  Node node = element.node;
  FindNodeVisitor visitor = new FindNodeVisitor(token);
  node.accept(visitor);

  Send send = visitor.foundSend;
  var receiverType;
  var type;
  if (send != null) {
    TreeElements mapping = element.treeElements;
    type = cachedCompiler.recordedTypes[send];
    // type = mapping.getType(send);
    if (send.receiver != null) {
      receiverType = mapping.getType(send.receiver);
      receiverType = cachedCompiler.recordedTypes[send.receiver];
    }
  }

  return new ExpressionInfo(
      visitor.foundIdentifier,
      visitor.foundSend,
      type,
      receiverType,
      visitor.parentSend);
}

class ExpressionInfo {
  final Identifier identifier;

  final Send send;

  final DartType type;

  final DartType receiverType;

  final Send parent;

  ExpressionInfo(
      this.identifier, this.send, this.type, this.receiverType, this.parent);

  String toString() {
    return 'ExpressionInfo('
        'identifier: $identifier, send: $send, type: $type, '
        'receiverType: $receiverType, parent: $parent)';
  }
}

class FindNodeVisitor extends Visitor {
  final Token soughtToken;

  Send currentSend;

  Identifier foundIdentifier;
  Send foundSend;
  Send parentSend;

  FindNodeVisitor(this.soughtToken);

  void visitNode(Node node) {
    if (soughtToken != null) {
      node.visitChildren(this);
    }
  }

  void visitSend(Send node) {
    Send enclosingSend = currentSend;
    currentSend = node;
    super.visitSend(node);
    currentSend = enclosingSend;
    if (foundSend == node) {
      parentSend = currentSend;
    }
  }

  void visitIdentifier(Identifier node) {
    if (node.token == soughtToken) {
      foundIdentifier = node;
      foundSend = currentSend;
    }
  }
}

Future<Info> analyzeCode(
    Map<Uri, String> codeMap,
    Uri uri,
    int offset) {
  bool lastDiagnosticWasError = false;
  List<Message> errors = <Message>[];
  List<Message> otherMessages = <Message>[];

  void handler(
      Uri uri,
      int begin,
      int end,
      String text,
      api.Diagnostic kind) {
    Message message = new Message(uri, begin, end, text, kind);
    print(message);

    bool isError =
        kind == api.Diagnostic.ERROR ||
        kind == api.Diagnostic.CRASH;

    List<Message> messages;

    if (kind == api.Diagnostic.INFO) {
      messages = lastDiagnosticWasError ? errors : otherMessages;
    } else {
      lastDiagnosticWasError = isError;
      messages = isError ? errors : otherMessages;
    }

    messages.add(message);
  }

  Future<String> inputProvider(Uri uri) {
    if (uri.scheme == 'sdk') {
      String source = SDK_SOURCES['$uri'];
      if (source == null) {
        return new Future.error("SDK source not found: '${uri.path}'.");
      } else {
        return new Future.value(source);
      }
    } else {
      String source = codeMap[uri];
      if (source == null) {
        return new Future.error("User source not found: '${uri}'.");
      } else {
        return new Future.value(source);
      }
    }
  }

  Info computeInfo(Element element) {
    String scope = scopeInformation(element, offset);
    Token token = findToken(element, offset);
    String prefix;
    ExpressionInfo expression;
    if (token != null) {
      expression = findNode(element, token);
    }

    return new Info(scope, errors, otherMessages, expression);
  }

  Uri libraryRoot = Uri.parse('sdk:/sdk/');
  Uri packageRoot = Uri.base.resolve('packages/');

  return runPoi(uri, offset, inputProvider, handler, libraryRoot, packageRoot)
      .then(computeInfo);
}

class Info {
  /// A JSON string of scope information.
  final String scope;

  final List<Message> errors;

  final List<Message> otherMessages;

  final ExpressionInfo expression;

  Info(this.scope, this.errors, this.otherMessages, this.expression);

  String toString() {
    return '''
Info(
  scope: $scope,
  errors: $errors,
  otherMessages: $otherMessages,
  expression: $expression)''';
  }
}

class Message {
  Uri uri;
  int begin;
  int end;
  String text;
  api.Diagnostic kind;

  Message(this.uri, this.begin, this.end, this.text, this.kind);

  Map toJson() {
    return {
      'uri': '$uri',
      'begin': begin,
      'end': end,
      'text': text,
      'kind': kind.name,
    };
  }

  String toString() {
    if (uri == null) {
      return '[$kind] $text';
    }
    return '$uri@$begin+$end: [$kind] $text';
  }
}

void main() {
  Uri uri = Uri.parse('org-trydart-poi:/main.dart');
  Future.forEach([TEST1, TEST2], (List codes) {
    cachedCompiler = null;
    return Future.forEach(codes, (String code) {
      return analyzeCode({uri: code}, uri, TEST_OFFSET).then((Info info) {
        print(info);
      });
    });
  });
}

const TEST1 = const [GOOD_TEST_CODE1, BROKEN_TEST_CODE1];

const TEST2 = const [GOOD_TEST_CODE2, BROKEN_TEST_CODE2, GOOD_TEST_CODE2];

const String GOOD_TEST_CODE1 = '''
class B {
  int b() => 0;
}

class C {
  B c() => new B();
}

main() {
  C c = new C();
  c.c();
  foo();
  bar();
  baz();
}
''';

const String BROKEN_TEST_CODE1 = '''
import 'dart:math' show Random;

class B {
  int b() => 0;
}

class C {
  B c() => new B();
}

main() {
  C c = new C();
  c.c(
  foo();
  bar();
  baz();
}
''';

const String GOOD_TEST_CODE2 = r'''
import 'dart:math' show Random;

void main() {
  String str = "";
  Random rand = new Random();

  for (int i=0; i < 2; i++) {
      str = str + "${rand.nextInt(10)},";
     print (str);
  }
}
''';

const String BROKEN_TEST_CODE2 = r'''
import 'dart:math' show Random;

void main() {
  String str = "";
  Random rand = new Random();

  for (int i=0; i < ; i++) {
      str = str + "${rand.nextInt(10)},";
     print (str);
  }
}
''';

const int TEST_OFFSET = 93;

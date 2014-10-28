// Copyright (c) 2013, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import "package:expect/expect.dart";
import 'dart:async';
import "package:async_helper/async_helper.dart";
import 'memory_compiler.dart' show compilerFor;
import 'package:compiler/implementation/apiimpl.dart' show
    Compiler;
import 'package:compiler/implementation/tree/tree.dart' show
    Node;
import 'package:compiler/implementation/dart_backend/dart_backend.dart';
import 'package:compiler/implementation/mirror_renamer/mirror_renamer.dart';

main() {
  testWithMirrorHelperLibrary(minify: true);
  testWithMirrorHelperLibrary(minify: false);
  testWithoutMirrorHelperLibrary(minify: true);
  testWithoutMirrorHelperLibrary(minify: false);
}

Future<Compiler> runCompiler({useMirrorHelperLibrary: false, minify: false}) {
  List<String> options = ['--output-type=dart'];
  if (minify) {
    options.add('--minify');
  }
  Compiler compiler = compilerFor(MEMORY_SOURCE_FILES, options: options);
  DartBackend backend = compiler.backend;
  backend.useMirrorHelperLibrary = useMirrorHelperLibrary;
  return
      compiler.runCompiler(Uri.parse('memory:main.dart')).then((_) => compiler);
}

void testWithMirrorHelperLibrary({bool minify}) {
  asyncTest(() => runCompiler(useMirrorHelperLibrary: true, minify: minify).
      then((Compiler compiler) {
    DartBackend backend = compiler.backend;
    MirrorRenamerImpl mirrorRenamer = backend.mirrorRenamer;
    Map<Node, String> renames = backend.placeholderRenamer.renames;
    Map<String, String> symbols = mirrorRenamer.symbols;

    Expect.isFalse(null == mirrorRenamer.helperLibrary);
    Expect.isFalse(null == mirrorRenamer.getNameFunction);

    for (Node n in renames.keys) {
      if (symbols.containsKey(renames[n])) {
        if(n.toString() == 'getName') {
          Expect.equals(
              MirrorRenamerImpl.MIRROR_HELPER_GET_NAME_FUNCTION,
              symbols[renames[n]]);
        } else {
          Expect.equals(n.toString(), symbols[renames[n]]);
        }
      }
    }

    String output = compiler.assembledCode;
    String getNameMatch = MirrorRenamerImpl.MIRROR_HELPER_GET_NAME_FUNCTION;
    Iterable i = getNameMatch.allMatches(output);
    print(output);
    if (minify) {
      Expect.equals(0, i.length);
    } else {
      // Appears twice in code (defined & called).
      Expect.equals(2, i.length);
    }

    RegExp mapMatch = new RegExp('const<String,( )?String>');
    i = mapMatch.allMatches(output);
    Expect.equals(1, i.length);
  }));
}

void testWithoutMirrorHelperLibrary({bool minify}) {
  asyncTest(() => runCompiler(useMirrorHelperLibrary: false, minify: minify).
      then((Compiler compiler) {
    DartBackend backend = compiler.backend;
    MirrorRenamer mirrorRenamer = backend.mirrorRenamer;

    Expect.equals(null, mirrorRenamer.helperLibrary);
    Expect.equals(null, mirrorRenamer.getNameFunction);
  }));
}

const MEMORY_SOURCE_FILES = const <String, String> {
  'main.dart': """
import 'dart:mirrors';

class Foo {
  noSuchMethod(Invocation invocation) {
    MirrorSystem.getName(invocation.memberName);
  }
}

void main() {
  new Foo().fisk();
}
"""};
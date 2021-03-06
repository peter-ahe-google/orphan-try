// Copyright (c) 2012, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import 'package:expect/expect.dart';
import "package:async_helper/async_helper.dart";
import 'dart:async';
import 'mirror_system_helper.dart';

void validateDeclarationComment(String code,
                                String text,
                                String trimmedText,
                                bool isDocComment,
                                List<Symbol> declarationNames) {
  asyncTest(() => createMirrorSystem(code).then((mirrors) {
    LibraryMirror library = mirrors.libraries[SOURCE_URI];
    Expect.isNotNull(library);
    for (Symbol declarationName in declarationNames) {
      DeclarationMirror declaration = library.declarations[declarationName];
      Expect.isNotNull(declaration);
      List<InstanceMirror> metadata = declaration.metadata;
      Expect.isNotNull(metadata);
      Expect.equals(1, metadata.length);
      Expect.isTrue(metadata[0] is CommentInstanceMirror);
      CommentInstanceMirror commentMetadata = metadata[0];
      Expect.equals(text, commentMetadata.text);
      Expect.equals(trimmedText, commentMetadata.trimmedText);
      Expect.equals(isDocComment, commentMetadata.isDocComment);
    }
  }));
}

void testDeclarationComment(String declaration, List<Symbol> declarationNames) {
  String text = 'Single line comment';
  String comment = '// $text';
  String code = '$comment\n$declaration';
  validateDeclarationComment(code, comment, text, false, declarationNames);

  comment = '/// $text';
  code = '$comment\n$declaration';
  validateDeclarationComment(code, comment, text, true, declarationNames);

  text = 'Multiline comment';
  comment = '/* $text*/';
  code = '$comment$declaration';
  validateDeclarationComment(code, comment, text, false, declarationNames);

  comment = '/** $text*/';
  code = '$comment$declaration';
  validateDeclarationComment(code, comment, text, true, declarationNames);
}

void main() {
  testDeclarationComment('var field;', [#field]);
  testDeclarationComment('int field;', [#field]);
  testDeclarationComment('int field = 0;', [#field]);
  testDeclarationComment('int field1, field2;', [#field1, #field2]);
  testDeclarationComment('final field = 0;', [#field]);
  testDeclarationComment('final int field = 0;', [#field]);
  testDeclarationComment('final field1 = 0, field2 = 0;', [#field1, #field2]);
  testDeclarationComment('final int field1 = 0, field2 = 0;',
                         [#field1, #field2]);
  testDeclarationComment('const field = 0;', [#field]);
  testDeclarationComment('const int field = 0;', [#field]);
  testDeclarationComment('const field1 = 0, field2 = 0;', [#field1, #field2]);
  testDeclarationComment('const int field1 = 0, field2 = 0;',
                         [#field1, #field2]);
}

// Copyright (c) 2013, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

library trydart.compilationUnit;

class CompilationUnitData {
  final String name;
  String content;

  CompilationUnitData(this.name, this.content);
}

class CompilationUnit extends CompilationUnitData {
  static StreamController<CompilationUnit> controller =
      new StreamController<CompilationUnit>(sync: false);

  static Stream<CompilationUnit> get onChanged => controller.stream;

  CompilationUnit(String name, String content)
      : super(name, content);

  void set content(String newContent) {
    if (content != newContent) {
      super.content = newContent;
      controller.add(this);
    }
  }
}

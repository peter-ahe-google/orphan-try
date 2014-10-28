// Copyright (c) 2014, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

library trydart.poi.main;

import 'dart:async' show
    Completer,
    Future;

import 'dart:io' as io;

import 'dart:convert' show
    UTF8;

import 'package:compiler/implementation/source_file_provider.dart' show
    FormattingDiagnosticHandler;

import 'package:compiler/compiler.dart' as api;

import 'package:compiler/implementation/elements/elements.dart' show
    Element;

import 'package:compiler/implementation/scanner/scannerlib.dart' show
    IDENTIFIER_TOKEN,
    KEYWORD_TOKEN,
    Token;

import 'poi.dart';

/// Iterator for reading lines from [io.stdin].
class StdinIterator implements Iterator<String> {
  String current;

  bool moveNext() {
    current = io.stdin.readLineSync();
    return true;
  }
}

/// Create an input provider that implements the behavior documented at
/// [simulateMutation].
api.CompilerInputProvider simulateMutation(
    String fileName,
    api.CompilerInputProvider inputProvider) {
  Uri script = Uri.base.resolveUri(new Uri.file(fileName));
  int count = poiCount;
  Future cache;
  String cachedFileName = script.toFilePath();
  int counter = ++globalCounter;
  return (Uri uri) {
    if (counter != globalCounter) throw 'Using old provider';
    printVerbose('fake inputProvider#$counter($uri): $poiCount $count');
    if (uri == script) {
      if (poiCount == count) {
        cachedFileName = uri.toFilePath();
        if (count != 0) {
          cachedFileName = '$cachedFileName.$count.dart';
        }
        printVerbose('Not using cached version of $cachedFileName');
        cache = new io.File(cachedFileName).readAsBytes().then((data) {
          printVerbose(
              'Read file $cachedFileName: '
              '${UTF8.decode(data.take(100).toList(), allowMalformed: true)}...');
          return data;
        });
        count++;
      } else {
        printVerbose('Using cached version of $cachedFileName');
      }
      return cache;
    } else {
      printVerbose('Using original provider for $uri');
      return inputProvider(uri);
    }
  };
}

Future<String> prompt(message) {
  if (stdin is StdinIterator) {
    io.stdout.write(message);
  }
  return io.stdout.flush().then((_) {
    stdin.moveNext();
    return stdin.current;
  });
}

Future queryDartMind(String prefix, String info) {
  // TODO(lukechurch): Use [info] for something.
  String encodedArg0 = Uri.encodeComponent('"$prefix"');
  String mindQuery =
      'http://dart-mind.appspot.com/rpc'
      '?action=GetExportingPubCompletions'
      '&arg0=$encodedArg0';
  Uri uri = Uri.parse(mindQuery);

  io.HttpClient client = new io.HttpClient();
  return client.getUrl(uri).then((io.HttpClientRequest request) {
    return request.close();
  }).then((io.HttpClientResponse response) {
    Completer<String> completer = new Completer<String>();
    response.transform(UTF8.decoder).listen((contents) {
      completer.complete(contents);
    });
    return completer.future;
  });
}

Future parseUserInput(
    String fileName,
    String positionString,
    api.CompilerInputProvider inputProvider,
    api.DiagnosticHandler handler) {
  Future repeat() {
    printFormattedTime('--->>>', wallClock.elapsedMicroseconds);
    wallClock.reset();

    return prompt('Position: ').then((String positionString) {
      wallClock.reset();
      return parseUserInput(fileName, positionString, inputProvider, handler);
    });
  }

  printWallClock("\n\n\nparseUserInput('$fileName', '$positionString')");

  Uri script = Uri.base.resolveUri(new Uri.file(fileName));
  if (positionString == null) return null;
  int position = int.parse(
      positionString, onError: (_) { print('Please enter an integer.'); });
  if (position == null) return repeat();

  inputProvider(script);
  if (isVerbose) {
    handler(
        script, position, position + 1,
        'Point of interest. '
        'Cursor is immediately before highlighted character.',
        api.Diagnostic.HINT);
  }

  Stopwatch sw = new Stopwatch()..start();

  Uri packageRoot = Uri.base.resolveUri(
      new Uri.file('${io.Platform.packageRoot}/'));

  Future future =
      runPoi(script, position, inputProvider, handler, null, packageRoot);
  return future.then((Element element) {
    if (isVerbose) {
      printFormattedTime('Resolving took', sw.elapsedMicroseconds);
    }
    sw.reset();
    String info = scopeInformation(element, position);
    sw.stop();
    if (PRINT_SCOPE_INFO) {
      print(info);
    }
    printVerbose('Scope information took ${sw.elapsedMicroseconds}us.');
    sw..reset()..start();
    Token token = findToken(element, position);
    String prefix;
    if (token != null) {
      if (token.charOffset + token.charCount < position) {
        // After the token; in whitespace, or in the beginning of another token.
        prefix = "";
      } else if (token.kind == IDENTIFIER_TOKEN ||
                 token.kind == KEYWORD_TOKEN) {
        prefix = token.value.substring(0, position - token.charOffset);
      }
      findNode(element, token);
    }
    sw.stop();
    printVerbose('Find token took ${sw.elapsedMicroseconds}us.');
    if (isDartMindEnabled && prefix != null) {
      sw..reset()..start();
      return queryDartMind(prefix, info).then((String dartMindSuggestion) {
        sw.stop();
        print('Dart Mind ($prefix): $dartMindSuggestion.');
        printVerbose('Dart Mind took ${sw.elapsedMicroseconds}us.');
        return repeat();
      });
    } else {
      if (isDartMindEnabled) {
        print("Didn't talk to Dart Mind, no identifier at POI ($token).");
      } else if (prefix != null) {
        print("Prefix at POI: $prefix");
      }
      return repeat();
    }
  });
}

main(List<String> arguments) {
  poiCount = 0;
  wallClock.start();
  List<String> nonOptionArguments = [];
  for (String argument in arguments) {
    if (argument.startsWith('-')) {
      switch (argument) {
        case '--simulate-mutation':
          isSimulateMutationEnabled = true;
          break;
        case '--enable-dart-mind':
          isDartMindEnabled = true;
          break;
        case '-v':
        case '--verbose':
          isVerbose = true;
          break;
        case '--compile':
          isCompiler = true;
          break;
        case '--minify':
          enableMinification = true;
          break;
        default:
          throw 'Unknown option: $argument.';
      }
    } else {
      nonOptionArguments.add(argument);
    }
  }
  if (nonOptionArguments.isEmpty) {
    stdin = new StdinIterator();
  } else {
    stdin = nonOptionArguments.iterator;
  }

  FormattingDiagnosticHandler handler = new FormattingDiagnosticHandler();
  handler
      ..verbose = false
      ..enableColors = true;
  api.CompilerInputProvider inputProvider = handler.provider;

  return prompt('Dart file: ').then((String fileName) {
    if (isSimulateMutationEnabled) {
      inputProvider = simulateMutation(fileName, inputProvider);
    }
    return prompt('Position: ').then((String position) {
      return parseUserInput(fileName, position, inputProvider, handler);
    });
  });
}

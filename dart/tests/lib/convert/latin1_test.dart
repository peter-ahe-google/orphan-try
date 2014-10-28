// Copyright (c) 2012, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import "package:expect/expect.dart";
import 'dart:convert';

var latin1Strings = [
  "pure ascii",
  "blåbærgrød",
  "\x00 edge cases \xff"
];

var nonLatin1Strings = [
  "Edge case \u{100}",
  "Edge case super-BMP \u{10000}"
];

void main() {
  // Build longer versions of the example strings.
  for (int i = 0, n = latin1Strings.length; i < n ; i++) {
    var string = latin1Strings[i];
    while (string.length < 1024) {
      string += string;
    }
    latin1Strings.add(string);
  }
  for (int i = 0, n = nonLatin1Strings.length; i < n ; i++) {
    var string = nonLatin1Strings[i];
    while (string.length < 1024) {
      string += string;
    }
    nonLatin1Strings.add(string);
  }
  testDirectConversions();
  testChunkedConversions();
}

void testDirectConversions() {
  for (var codec in [LATIN1, new Latin1Codec()]) {
    for (var latin1String in latin1Strings) {
      List bytes = codec.encoder.convert(latin1String);
      Expect.listEquals(latin1String.codeUnits.toList(), bytes, latin1String);
      String roundTripString = codec.decoder.convert(bytes);
      Expect.equals(latin1String, roundTripString);
      roundTripString = codec.decode(bytes);
      Expect.equals(latin1String, roundTripString);
    }

    for (var nonLatin1String in nonLatin1Strings) {
      Expect.throws(() {
        print(codec.encoder.convert(nonLatin1String));
      }, null, nonLatin1String);
    }
  }

  var allowInvalidCodec = new Latin1Codec(allowInvalid: true);
  var invalidBytes = [0, 1, 0xff, 0xdead, 0];
  String decoded = allowInvalidCodec.decode(invalidBytes);
  Expect.equals("\x00\x01\xFF\uFFFD\x00", decoded);
  decoded = allowInvalidCodec.decoder.convert(invalidBytes);
  Expect.equals("\x00\x01\xFF\uFFFD\x00", decoded);
  decoded = LATIN1.decode(invalidBytes, allowInvalid: true);
  Expect.equals("\x00\x01\xFF\uFFFD\x00", decoded);
}

List<int> encode(String str, int chunkSize,
                 Converter<String, List<int>> converter) {
  List<int> bytes = <int>[];
  ChunkedConversionSink byteSink =
      new ByteConversionSink.withCallback(bytes.addAll);
  var stringConversionSink = converter.startChunkedConversion(byteSink);
  for (int i = 0; i < str.length; i += chunkSize) {
    if (i + chunkSize <= str.length) {
      stringConversionSink.add(str.substring(i, i + chunkSize));
    } else {
      stringConversionSink.add(str.substring(i));
    }
  }
  stringConversionSink.close();
  return bytes;
}

String decode(List<int> bytes, int chunkSize,
              Converter<List<int>, String> converter) {
  StringBuffer buf = new StringBuffer();
  var stringSink =
      new StringConversionSink.fromStringSink(buf);
  var byteConversionSink = converter.startChunkedConversion(stringSink);
  for (int i = 0; i < bytes.length; i += chunkSize) {
    if (i + chunkSize <= bytes.length) {
      byteConversionSink.add(bytes.sublist(i, i + chunkSize));
    } else {
      byteConversionSink.add(bytes.sublist(i));
    }
  }
  byteConversionSink.close();
  return buf.toString();
}

void testChunkedConversions() {
  // Check encoding.
  for (var converter in [LATIN1.encoder,
                         new Latin1Codec().encoder,
                         new Latin1Encoder()]) {
    for (int chunkSize in [1, 2, 5, 50]) {
      for (var latin1String in latin1Strings) {
        var units = latin1String.codeUnits.toList();
        List bytes = encode(latin1String, chunkSize, converter);
        Expect.listEquals(units, bytes);
      }
      for (var nonLatin1String in nonLatin1Strings) {
        Expect.throws(() {
          encode(nonLatin1Strings, chunkSize, converter);
        });
      }
    }
  }
  // Check decoding.
  for (var converter in [LATIN1.decoder,
                         new Latin1Codec().decoder,
                         new Latin1Decoder()]) {
    for (int chunkSize in [1, 2, 5, 50]) {
      for (var latin1String in latin1Strings) {
        var units = latin1String.codeUnits.toList();
        Expect.equals(latin1String, decode(units, chunkSize, converter));
      }
      for (var nonLatin1String in nonLatin1Strings) {
        var units = nonLatin1String.codeUnits.toList();
        Expect.throws(() {
          decode(units, chunkSize, converter);
        });
      }
    }
  }
}

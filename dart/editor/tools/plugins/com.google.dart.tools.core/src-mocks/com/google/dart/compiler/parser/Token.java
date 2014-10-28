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
package com.google.dart.compiler.parser;

import java.util.HashMap;
import java.util.Map;

public enum Token {
  /* End-of-stream. */
  EOS(null, 0),

  /* Punctuators. */
  AT("@", 0),
  LPAREN("(", 0),
  RPAREN(")", 0),
  LBRACK("[", 0),
  RBRACK("]", 0),
  LBRACE("{", 0),
  RBRACE("}", 0),
  COLON(":", 0),
  SEMICOLON(";", 0),
  PERIOD(".", 0),
  CASCADE("..", 2),
  ELLIPSIS("...", 0),
  COMMA(",", 0),
  CONDITIONAL("?", 3),
  ARROW("=>", 0),

  /* Assignment operators. */
  ASSIGN("=", 1),
  ASSIGN_BIT_OR("|=", 1),
  ASSIGN_BIT_XOR("^=", 1),
  ASSIGN_BIT_AND("&=", 1),
  ASSIGN_SHL("<<=", 1),
  ASSIGN_SAR(">>=", 1),
  ASSIGN_ADD("+=", 1),
  ASSIGN_SUB("-=", 1),
  ASSIGN_MUL("*=", 1),
  ASSIGN_DIV("/=", 1),
  ASSIGN_MOD("%=", 1),
  ASSIGN_TRUNC("~/=", 1),

  /* Binary operators sorted by precedence. */
  OR("||", 4),
  AND("&&", 5),
  BIT_OR("|", 6),
  BIT_XOR("^", 7),
  BIT_AND("&", 8),
  SHL("<<", 11),
  SAR(">>", 11),
  ADD("+", 12),
  SUB("-", 12),
  MUL("*", 13),
  DIV("/", 13),
  TRUNC("~/", 13),
  MOD("%", 13),

  /* Compare operators sorted by precedence. */
  EQ("==", 9),
  NE("!=", 9),
  EQ_STRICT("===", 9),
  NE_STRICT("!==", 9),
  LT("<", 10),
  GT(">", 10),
  LTE("<=", 10),
  GTE(">=", 10),
  AS("as", 10),
  IS("is", 10),

  /* Unary operators. */
  NOT("!", 0),
  BIT_NOT("~", 0),

  /* Count operators (also unary). */
  INC("++", 0),
  DEC("--", 0),

  /* [] operator overloading. */
  INDEX("[]", 0),
  ASSIGN_INDEX("[]=", 0),

  /* Keywords. */
  ASSERT("assert", 0),
  BREAK("break", 0),
  CASE("case", 0),
  CATCH("catch", 0),
  CLASS("class", 0),
  CONST("const", 0),
  CONTINUE("continue", 0),
  DEFAULT("default", 0),
  DO("do", 0),
  ELSE("else", 0),
  EXTENDS("extends", 0),
  FINAL("final", 0),
  FINALLY("finally", 0),
  FOR("for", 0),
  IF("if", 0),
  IN("in", 0),
  NEW("new", 0),
  RETHROW("rethrow", 0),
  RETURN("return", 0),
  SUPER("super", 0),
  SWITCH("switch", 0),
  THIS("this", 0),
  THROW("throw", 0),
  TRY("try", 0),
  VAR("var", 0),
  VOID("void", 0),
  WHILE("while", 0),
  WITH("with", 0),

  /* Literals. */
  NULL_LITERAL("null", 0),
  TRUE_LITERAL("true", 0),
  FALSE_LITERAL("false", 0),
  HEX_LITERAL(null, 0),
  INTEGER_LITERAL(null, 0),
  DOUBLE_LITERAL(null, 0),
  STRING(null, 0),

  /** String interpolation and string templates. */
  STRING_SEGMENT(null, 0),
  STRING_LAST_SEGMENT(null, 0),
  // STRING_EMBED_EXP_START does not have a unique string representation in the code:
  //   "$id" yields the token STRING_EMBED_EXP_START after the '$', and similarly
  //   "${id}" yield the same token for '${'.
  STRING_EMBED_EXP_START(null, 0),
  STRING_EMBED_EXP_END(null, 0),

  // Note: STRING_EMBED_EXP_END uses the same symbol as RBRACE, but it is
  // recognized by the scanner when closing embedded expressions in string
  // interpolation and string templates.

  /* Directives */
  LIBRARY("#library", 0),
  IMPORT("#import", 0),
  SOURCE("#source", 0),
  RESOURCE("#resource", 0),
  NATIVE("#native", 0),

  /* Identifiers (not keywords). */
  IDENTIFIER(null, 0),
  WHITESPACE(null, 0),

  /* Pseudo tokens. */
  // If you add another pseudo token, don't forget to update the predicate below.
  ILLEGAL(null, 0),
  COMMENT(null, 0),

  NON_TOKEN(null, 0);

  private static Map<String, Token> tokens = new HashMap<String, Token>();

  static {
    for (Token tok : Token.values()) {
      if (tok.syntax_ != null) {
        tokens.put(tok.syntax_, tok);
      }
    }
  }

  public static Token lookup(String syntax) {
    Token token = tokens.get(syntax);
    if ("as".equals(syntax)) {
      return IDENTIFIER;
    }
    if (token == null) {
      return IDENTIFIER;
    }
    return token;
  }

  private final String syntax_;
  private final int precedence_;

  Token(String syntax, int precedence) {
    syntax_ = syntax;
    precedence_ = precedence;
  }

  public Token asBinaryOperator() {
    int ordinal = ordinal() - ASSIGN_BIT_OR.ordinal() + BIT_OR.ordinal();
    return values()[ordinal];
  }

  public int getPrecedence() {
    return precedence_;
  }

  public String getSyntax() {
    return syntax_;
  }

  public boolean isAssignmentOperator() {
    int ordinal = ordinal();
    return ASSIGN.ordinal() <= ordinal && ordinal <= ASSIGN_TRUNC.ordinal();
  }

  public boolean isBinaryOperator() {
    int ordinal = ordinal();
    return (ASSIGN.ordinal() <= ordinal && ordinal <= IS.ordinal()) || (ordinal == COMMA.ordinal());
  }

  public boolean isCountOperator() {
    int ordinal = ordinal();
    return INC.ordinal() <= ordinal && ordinal <= DEC.ordinal();
  }

  public boolean isEqualityOperator() {
    int ordinal = ordinal();
    return EQ.ordinal() <= ordinal && ordinal <= NE_STRICT.ordinal();
  }

  public boolean isRelationalOperator() {
    int ordinal = ordinal();
    return LT.ordinal() <= ordinal && ordinal <= GTE.ordinal();
  }

  public boolean isUnaryOperator() {
    int ordinal = ordinal();
    return NOT.ordinal() <= ordinal && ordinal <= DEC.ordinal();
  }

  public boolean isUserDefinableOperator() {
    int ordinal = ordinal();
    return ((BIT_OR.ordinal() <= ordinal && ordinal <= GTE.ordinal()) || this == BIT_NOT
        || this == INDEX || this == ASSIGN_INDEX)
        && this != NE && this != EQ_STRICT && this != NE_STRICT;
  }

}

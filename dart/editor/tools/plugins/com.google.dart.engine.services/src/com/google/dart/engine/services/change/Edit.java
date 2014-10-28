/*
 * Copyright (c) 2012, the Dart project authors.
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
package com.google.dart.engine.services.change;

import com.google.dart.engine.source.Source;
import com.google.dart.engine.utilities.source.SourceRange;
import com.google.dart.engine.utilities.translation.DartName;

/**
 * Describes a text edit. Edits are executed by applying them to a {@link Source}.
 */
public class Edit {
  /**
   * The offset at which to apply the edit.
   */
  private final int offset;

  /**
   * The length of the text interval to replace.
   */
  private final int length;

  /**
   * The replacement text.
   */
  private final String replacement;

  /**
   * Create an edit.
   * 
   * @param offset the offset at which to apply the edit
   * @param length the length of the text interval replace
   * @param replacement the replacement text
   */
  public Edit(int offset, int length, String replacement) {
    this.offset = offset;
    this.length = length;
    this.replacement = replacement;
  }

  /**
   * Create an edit.
   * 
   * @param range the {@link SourceRange} to replace
   * @param replacement the replacement text
   */
  @DartName("range")
  public Edit(SourceRange range, String replacement) {
    this(range.getOffset(), range.getLength(), replacement);
  }

  /**
   * Returns the length of the text interval to replace.
   */
  public int getLength() {
    return length;
  }

  /**
   * Returns the offset at which to apply the edit.
   */
  public int getOffset() {
    return offset;
  }

  /**
   * Returns the replacement text.
   */
  public String getReplacement() {
    return replacement;
  }

  @Override
  public String toString() {
    return (offset < 0 ? "(" : "X(") + "offset: " + offset + ", length " + length + ", replacement :>" + replacement + "<:)"; //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$
  }

}

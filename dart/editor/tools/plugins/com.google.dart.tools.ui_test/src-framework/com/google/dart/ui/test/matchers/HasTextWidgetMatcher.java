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

package com.google.dart.ui.test.matchers;

import com.google.dart.ui.test.util.UiContext;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.widgets.Widget;

class HasTextWidgetMatcher implements WidgetMatcher {
  /**
   * Normalizes given {@link String} by removing special characters.
   */
  private static String normalizeWidgetText(String s) {
    s = s.trim();
    s = StringUtils.remove(s, '&');
    s = StringUtils.substringBefore(s, "\t");
    return s;
  }

  private final String textOrPattern;

  public HasTextWidgetMatcher(String textOrPattern) {
    this.textOrPattern = textOrPattern;
  }

  @Override
  public boolean matches(Widget widget) {
    String text = UiContext.getText(widget);
    if (text == null) {
      return false;
    }
    text = normalizeWidgetText(text);
    if (text.equals(textOrPattern)) {
      return true;
    }
    return text.matches(textOrPattern);
  }
}

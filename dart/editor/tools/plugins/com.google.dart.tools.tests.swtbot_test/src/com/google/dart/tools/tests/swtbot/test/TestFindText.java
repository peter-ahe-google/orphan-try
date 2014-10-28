/*
 * Copyright 2014 Dart project authors.
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
package com.google.dart.tools.tests.swtbot.test;

import com.google.dart.tools.tests.swtbot.harness.EditorTestHarness;
import com.google.dart.tools.tests.swtbot.model.EditorBotWindow;
import com.google.dart.tools.tests.swtbot.model.FilesBotView;
import com.google.dart.tools.tests.swtbot.model.FindTextBotView;
import com.google.dart.tools.tests.swtbot.model.TextBotEditor;
import com.google.dart.tools.tests.swtbot.model.WelcomePageEditor;

import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestFindText extends EditorTestHarness {

  private static TextBotEditor editor;

  @BeforeClass
  public static void setUpTest() {
    assertNotNull(bot); // initialized in superclass
    EditorBotWindow main = new EditorBotWindow(bot);
    FilesBotView files = main.filesView();
    files.deleteExistingProject("pop_pop_win");
    final WelcomePageEditor page = main.openWelcomePage();
    page.createPopPopWin();
    SWTBotTreeItem item;
    item = files.select("pop_pop_win", "web", "platform_web.dart [pop_pop_win.platform_web]");
    item.doubleClick();
    page.waitForAnalysis();
    page.waitForAsyncDrain();
    editor = new TextBotEditor(bot, "platform_web.dart");
  }

  @AfterClass
  public static void tearDownTest() {
    EditorBotWindow main = new EditorBotWindow(bot);
    FilesBotView files = main.filesView();
    files.deleteProject("pop_pop_win");
    main.menu("File").menu("Close All").click();
  }

  @Test
  public void test1() throws Exception {
    editor.select("about", 3);
    FindTextBotView searcher = editor.findText("about");
    assertEquals("3 / 13", searcher.getSearchStatus());
    searcher.findNext();
    assertEquals("4 / 13", searcher.getSearchStatus());
    searcher.findPrevious();
    assertEquals("3 / 13", searcher.getSearchStatus());
    searcher.setRegexpSearch(true);
    assertEquals("13", searcher.getSearchStatus());
    searcher.setCaseSensitiveSearch(true);
    assertEquals("6", searcher.getSearchStatus());
    searcher.setWordPrefixSearch(true);
    assertEquals("6", searcher.getSearchStatus());
    searcher.setRegexpSearch(false);
    searcher.setCaseSensitiveSearch(false);
    searcher.setWordPrefixSearch(false);
    searcher.dismiss();
  }
}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.java.JavaFileElement
import com.intellij.psi.impl.source.tree.java.MethodElement
import com.intellij.testFramework.LeakHunter
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Processor

/**
 * @author peter
 */
class AstLeaksTest extends LightCodeInsightFixtureTestCase {

  public void "_test AST should be on a soft reference, for changed files as well"() {
    def file = myFixture.addClass('class Foo {}').containingFile
    assert file.findElementAt(0) instanceof PsiKeyword
    LeakHunter.checkLeak(file, JavaFileElement)

    ApplicationManager.application.runWriteAction {
      file.viewProvider.document.insertString(0, ' ')
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      assert file.findElementAt(0) instanceof PsiWhiteSpace
    }

    LeakHunter.checkLeak(file, JavaFileElement)
  }

  public void "test super methods held via their signatures in class user data"() {
    def superClass = myFixture.addClass('class Super { void foo() {} }')
    superClass.text // load AST

    def file = myFixture.addFileToProject('Main.java', 'class Main extends Super { void foo() { System.out.println("hello"); } }')
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.doHighlighting()

    def mainClass = ((PsiJavaFile) file).classes[0]

    LeakHunter.checkLeak(mainClass, MethodElement, { MethodElement node ->
      superClass == node.psi.parent
    } as Processor<MethodElement>)
  }

  public void "test no hard refs to AST after highlighting"() {
    def sup = myFixture.addFileToProject('sup.java', 'class Super { Super() {} }')
    assert sup.findElementAt(0) // load AST
    assert !((PsiFileImpl)sup).stub
    LeakHunter.checkLeak(sup, MethodElement)

    def foo = myFixture.addFileToProject('a.java', 'class Foo extends Super { void bar() { bar(); } }')
    myFixture.configureFromExistingVirtualFile(foo.virtualFile)
    myFixture.doHighlighting()

    assert !((PsiFileImpl)foo).stub
    assert ((PsiFileImpl)foo).treeElement

    LeakHunter.checkLeak(foo, MethodElement)
    LeakHunter.checkLeak(sup, MethodElement)
  }

}

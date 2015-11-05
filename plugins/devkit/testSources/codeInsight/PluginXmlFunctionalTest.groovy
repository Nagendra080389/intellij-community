/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.codeInsight
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.xml.DeprecatedClassUsageInspection
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.ui.components.JBList
import com.intellij.usageView.UsageViewNodeTextLocation
import com.intellij.usageView.UsageViewTypeLocation
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.AbstractCollection
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.PluginXmlDomInspection
import org.jetbrains.idea.devkit.util.PsiUtil

@TestDataPath("\$CONTENT_ROOT/testData/codeInsight")
public class PluginXmlFunctionalTest extends JavaCodeInsightFixtureTestCase {

  private TempDirTestFixture myTempDirFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myFixture.enableInspections(new PluginXmlDomInspection());
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/codeInsight";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String pathForClass = PathUtil.getJarPathForClass(AbstractCollection.class);
    moduleBuilder.addLibrary("util", pathForClass);
    String platformApiJar = PathUtil.getJarPathForClass(JBList.class)
    moduleBuilder.addLibrary("platform-api", platformApiJar);
  }

  public void testExtensionsHighlighting() throws Throwable {
    final String root = "idea_core";
    addPluginXml(root, """
        <id>com.intellij</id>
        <extensionPoints>
            <extensionPoint name="completion.contributor"/>
        </extensionPoints>
    """);
    addPluginXml("indirect", """
        <id>com.intellij.indirect</id>
        <extensionPoints>
            <extensionPoint name="indirect"/>
        </extensionPoints>
    """);
    addPluginXml("custom", """
        <id>com.intellij.custom</id>
        <depends>com.intellij.indirect</depends>
        <extensionPoints>
            <extensionPoint name="custom"/>
        </extensionPoints>
    """);
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}");
    myFixture.addClass("package foo; @Deprecated public abstract class MyDeprecatedEP {}");
    myFixture.addClass("package foo; public class MyDeprecatedEPImpl extends foo.MyDeprecatedEP {}");

    configureByFile();
    myFixture.checkHighlighting(true, false, false);
  }

  public void testDependsHighlighting() throws Throwable {
    final String root = "idea_core";
    addPluginXml(root, """
        <id>com.intellij</id>
        <module value="com.intellij.modules.vcs"/>
    """);
    addPluginXml("custom", "<id>com.intellij.custom</id>");

    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/optional.xml")
    configureByFile();
    myFixture.checkHighlighting(false, false, false);
  }

  public void testDependsConfigFileCompletion() {
    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/used.xml")
    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/optional.xml")
    myFixture.copyFileToProject("deprecatedAttributes.xml", "META-INF/optional2.xml")
    configureByFile()

    myFixture.completeBasic()
    assertSameElements(myFixture.getLookupElementStrings(), "optional.xml", "optional2.xml")
  }

  public void testDependsCompletion() throws Throwable {
    addPluginXml("platform", """
        <id>com.intellij</id>
        <module value="com.intellij.modules.vcs"/>
    """);
    addPluginXml("lang", """
        <id>com.intellij</id>
        <module value="com.intellij.modules.lang"/>
        <module value="com.intellij.modules.lang.another"/>
    """);
    addPluginXml("custom", "<id>com.intellij.custom</id>");
    configureByFile();

    myFixture.completeBasic()
    assertSameElements(myFixture.lookupElementStrings,
                       'com.intellij.modules.vcs',
                       'com.intellij.modules.lang', 'com.intellij.modules.lang.another',
                       'com.intellij.custom')
  }

  private void configureByFile() {
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".xml", "META-INF/plugin.xml"));
  }

  public void testExtensionQualifiedName() throws Throwable {
    myFixture.addClass("package foo; public class MyRunnable implements java.lang.Runnable {}");
    configureByFile();
    myFixture.checkHighlighting(false, false, false);
  }

  public void testInnerClassCompletion() throws Throwable {
    myFixture.addClass("package foo; public class Foo { public static class Fubar {} }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testResolveExtensionsFromDependentDescriptor() throws Throwable {
    addPluginXml("xxx", """
        <id>com.intellij.xxx</id>
        <extensionPoints>
            <extensionPoint name="completion.contributor"/>
        </extensionPoints>
    """);

    myFixture.copyFileToProject(getTestName(false) + "_main.xml", "META-INF/plugin.xml");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + "_dependent.xml", "META-INF/dep.xml"));
    ApplicationManager.application.runWriteAction { PsiTestUtil.addSourceContentToRoots(myModule, myTempDirFixture.getFile("")) }

    myFixture.checkHighlighting(false, false, false);
  }

  private void addPluginXml(final String root, @Language("HTML") final String text) throws IOException {
    myTempDirFixture.createFile(root + "/META-INF/plugin.xml", "<idea-plugin>$text</idea-plugin>");
    ApplicationManager.application.runWriteAction { PsiTestUtil.addSourceContentToRoots(myModule, myTempDirFixture.getFile(root)) }
  }

  public void testNoWordCompletionInClassPlaces() throws Throwable {
    myFixture.addClass("package foo; public class FooFooFooFooFoo { }");
    myFixture.addClass("package foo; public interface ExtIntf { }");

    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.type('\'');
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testNoClassCompletionOutsideJavaReferences() throws Throwable {
    myFixture.addClass("package foo; public class FooFooFooFooFoo { }");

    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(false) + "_after.xml");
  }

  public void testShowPackagesInActionClass() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }");
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.completeBasic();
    assert myFixture.lookupElementStrings == ['bar', 'goo']
    assert myFixture.lookup.advertisements.find { it.contains('to see inheritors of com.intellij.openapi.actionSystem.AnAction') }
  }

  public void testShowAnActionInheritorsOnSmartCompletion() {
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }");
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.addClass("package foo.goo; public class GooAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.addClass("package another.goo; public class AnotherAction extends com.intellij.openapi.actionSystem.AnAction { }");
    myFixture.configureByFile(getTestName(false) + ".xml");
    myFixture.complete(CompletionType.SMART);
    assert myFixture.lookupElementStrings == ['foo.bar.BarAction', 'foo.goo.GooAction']
    assert !myFixture.lookup.advertisements.find { it.contains('to see inheritors of com.intellij.openapi.actionSystem.AnAction') }
  }

  public void testExtensionsSpecifyDefaultExtensionNs() {
    myFixture.testHighlighting("extensionsSpecifyDefaultExtensionNs.xml")
  }

  public void testDeprecatedExtensionAttribute() {
    myFixture.enableInspections(DeprecatedClassUsageInspection.class);
    myFixture.testHighlighting("deprecatedExtensionAttribute.xml", "MyExtBean.java");
  }

  public void testDeprecatedAttributes() {
    myFixture.testHighlighting("deprecatedAttributes.xml")
  }

  public void testExtensionAttributeDeclaredUsingAccessors() {
    myFixture.testHighlighting("extensionAttributeWithAccessors.xml", "ExtBeanWithAccessors.java");
  }

  public void testExtensionWithInnerTags() {
    myFixture.testHighlighting("extensionWithInnerTags.xml", "ExtBeanWithInnerTags.java");
  }

  public void testLanguageAttributeHighlighting() {
    configureLanguageAttributeTest()
    myFixture.testHighlighting("languageAttribute.xml", "MyLanguageAttributeEPBean.java")
  }
  
  public void testLanguageAttributeCompletion() {
    configureLanguageAttributeTest()
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguageAttributeEPBean.java"));
    myFixture.configureByFile("languageAttribute.xml")


    def lookupElements = myFixture.complete(CompletionType.BASIC).sort { it.lookupString }
    assertLookupElement(lookupElements[0], "MyAnonymousLanguageID", "MyLanguage.MySubLanguage")
    assertLookupElement(lookupElements[1], "MyAnonymousLanguageWithNameFromBundleID", "MyLanguage")
    assertLookupElement(lookupElements[2], "MyLanguageID", "MyLanguage")
  }
  
  private static void assertLookupElement(LookupElement element, String lookupString, String typeText) {
    def presentation = new LookupElementPresentation()
    element.renderElement(presentation)
    assert presentation.itemText == lookupString
    assert presentation.typeText == typeText
  }

  private void configureLanguageAttributeTest() {
    myFixture.addClass("package com.intellij.lang; " +
                       "public class Language { " +
                       "  protected Language(String id) {}" +
                       "}")
    myFixture.addClass("package org.jetbrains.annotations;\n" +
                       "import java.lang.annotation.Documented;\n" +
                       "import java.lang.annotation.ElementType;\n" +
                       "import java.lang.annotation.Retention;\n" +
                       "import java.lang.annotation.RetentionPolicy;\n" +
                       "import java.lang.annotation.Target;\n" +
                       "\n" +
                       "@Documented\n" +
                       "@Retention(RetentionPolicy.CLASS)\n" +
                       "@Target({ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.FIELD})\n" +
                       "public @interface PropertyKey {\n" +
                       "    String resourceBundle();\n" +
                       "}")
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyLanguage.java"))
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyBundle.java"))
    myFixture.allowTreeAccessForFile(myFixture.copyFileToProject("MyBundle.properties"))
  }

  public void testIconAttribute() {
    myFixture.addClass("package icons; " +
                       "public class MyIcons {" +
                       "  public static final javax.swing.Icon MyCustomIcon = null; " +
                       "}")
    myFixture.testHighlighting("iconAttribute.xml",
                               "MyIconAttributeEPBean.java")
  }

  public void testPluginModule() throws Throwable {
    myFixture.testHighlighting("pluginWithModules.xml");
  }

  public void testPluginWithModules() throws Throwable {
    myFixture.testHighlighting("pluginWithModules.xml");
  }

  public void testPluginWithXInclude() throws Throwable {
    myFixture.testHighlighting("pluginWithXInclude.xml", "pluginWithXInclude-extensionPoints.xml");
  }

  public void testPluginXmlInIdeaProjectWithoutVendor() {
    testHighlightingInIdeaProject("pluginWithoutVendor.xml")
  }

  public void testPluginXmlInIdeaProjectWithThirdPartyVendor() {
    testHighlightingInIdeaProject("pluginWithThirdPartyVendor.xml")
  }

  public void testPluginWithJetBrainsAsVendor() {
    testHighlightingInIdeaProject("pluginWithJetBrainsAsVendor.xml")
  }

  public void testPluginWithJetBrainsAndMeAsVendor() {
    testHighlightingInIdeaProject("pluginWithJetBrainsAndMeAsVendor.xml")
  }

  public void testSpecifyJetBrainsAsVendorQuickFix() {
    myFixture.enableInspections(PluginXmlDomInspection.class)
    PsiUtil.markAsIdeaProject(project, true)
    try {
      myFixture.configureByFile("pluginWithoutVendor_before.xml")
      def fix = myFixture.findSingleIntention("Specify JetBrains")
      myFixture.launchAction(fix)
      myFixture.checkResultByFile("pluginWithoutVendor_after.xml")
    }
    finally {
      PsiUtil.markAsIdeaProject(project, false)
    }
  }

  private void testHighlightingInIdeaProject(String path) {
    myFixture.enableInspections(PluginXmlDomInspection.class)
    PsiUtil.markAsIdeaProject(project, true)
    try {
      myFixture.testHighlighting(path);
    }
    finally {
      PsiUtil.markAsIdeaProject(project, false)
    }
  }

  public void testExtensionPointPresentation() {
    myFixture.configureByFile(getTestName(true) + ".xml");
    final PsiElement element =
      TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assert element != null;
    assertEquals("Extension Point", ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE));
    assertEquals("Extension Point bar", ElementDescriptionUtil.getElementDescription(element, UsageViewNodeTextLocation.INSTANCE));
  }

  public void testLoadForDefaultProject() throws Exception {
    configureByFile();
    myFixture.testHighlighting(true, true, true);
  }

  public void testCreateRequiredAttribute() {
    myFixture.configureByFile(getTestName(true) + ".xml")
    myFixture.launchAction(myFixture.findSingleIntention("Define class attribute"))
    myFixture.checkResultByFile(getTestName(true) + "_after.xml")
  }

  public void testActionHighlighting() {
    configureByFile()
    myFixture.addClass("package com.intellij.openapi.actionSystem; public class AnAction { }");
    myFixture.addClass("package foo.bar; public class BarAction extends com.intellij.openapi.actionSystem.AnAction { }");

    myFixture.addClass("package com.intellij.openapi.actionSystem; public class ActionGroup { }")
    myFixture.addClass("package foo.bar; public class BarGroup extends com.intellij.openapi.actionSystem.ActionGroup { }");
    myFixture.testHighlighting()
  }
}

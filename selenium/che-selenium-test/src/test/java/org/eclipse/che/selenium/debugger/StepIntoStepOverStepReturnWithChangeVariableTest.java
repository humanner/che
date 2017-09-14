/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.selenium.debugger;

import static org.testng.Assert.assertTrue;

import com.google.inject.Inject;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import org.eclipse.che.commons.lang.NameGenerator;
import org.eclipse.che.selenium.core.SeleniumWebDriver;
import org.eclipse.che.selenium.core.client.TestCommandServiceClient;
import org.eclipse.che.selenium.core.client.TestProjectServiceClient;
import org.eclipse.che.selenium.core.client.TestWorkspaceServiceClient;
import org.eclipse.che.selenium.core.constant.TestBuildConstants;
import org.eclipse.che.selenium.core.constant.TestCommandsConstants;
import org.eclipse.che.selenium.core.constant.TestMenuCommandsConstants;
import org.eclipse.che.selenium.core.project.ProjectTemplates;
import org.eclipse.che.selenium.core.workspace.TestWorkspace;
import org.eclipse.che.selenium.pageobject.CodenvyEditor;
import org.eclipse.che.selenium.pageobject.Consoles;
import org.eclipse.che.selenium.pageobject.Ide;
import org.eclipse.che.selenium.pageobject.Loader;
import org.eclipse.che.selenium.pageobject.Menu;
import org.eclipse.che.selenium.pageobject.ProjectExplorer;
import org.eclipse.che.selenium.pageobject.debug.DebugPanel;
import org.eclipse.che.selenium.pageobject.debug.JavaDebugConfig;
import org.eclipse.che.selenium.pageobject.intelligent.CommandsPalette;
import org.openqa.selenium.Keys;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** @author Musienko Maxim */
public class StepIntoStepOverStepReturnWithChangeVariableTest {
  private static final String PROJECT = NameGenerator.generate("project", 4);
  private static final String START_DEBUG = "startDebug";
  private static final String CLEAN_TOMCAT = "cleanTomcat";
  private static final String BUILD = "build";

  private DebuggerUtils debugUtils = new DebuggerUtils();

  @Inject private TestWorkspace ws;
  @Inject private Ide ide;

  @Inject private ProjectExplorer projectExplorer;
  @Inject private Consoles consoles;
  @Inject private CodenvyEditor editor;
  @Inject private Menu menu;
  @Inject private DebugPanel debugPanel;
  @Inject private JavaDebugConfig debugConfig;
  @Inject private TestCommandServiceClient testCommandServiceClient;
  @Inject private TestWorkspaceServiceClient workspaceServiceClient;
  @Inject private TestProjectServiceClient testProjectServiceClient;
  @Inject private Loader loader;
  @Inject private CommandsPalette commandsPalette;
  @Inject private SeleniumWebDriver seleniumWebDriver;

  @BeforeClass
  public void prepare() throws Exception {
    testProjectServiceClient.importProject(
        ws.getId(),
        Paths.get(getClass().getResource("/projects/debugStepInto").toURI()),
        PROJECT,
        ProjectTemplates.MAVEN_SPRING);

    testCommandServiceClient.createCommand(
        "cp /projects/"
            + PROJECT
            + "/target/qa-spring-sample-1.0-SNAPSHOT.war /home/user/tomcat8/webapps/ROOT.war"
            + " && "
            + "/home/user/tomcat8/bin/catalina.sh jpda run",
        START_DEBUG,
        TestCommandsConstants.CUSTOM,
        ws.getId());

    testCommandServiceClient.createCommand(
        "mvn clean install -f /projects/" + PROJECT,
        BUILD,
        TestCommandsConstants.CUSTOM,
        ws.getId());

    testCommandServiceClient.createCommand(
        "/home/user/tomcat8/bin/shutdown.sh && rm -rf /home/user/tomcat8/webapps/*",
        CLEAN_TOMCAT,
        TestCommandsConstants.CUSTOM,
        ws.getId());

    ide.open(ws);
  }

  @AfterMethod
  public void shutDownTomCatAndCleanWebApp() {
    editor.closeAllTabs();
    debugPanel.stopDebuggerWithUiAndCleanUpTomcat(CLEAN_TOMCAT);
    projectExplorer.clickOnProjectExplorerTabInTheLeftPanel();
  }

  @Test
  public void changeVariableTest() throws Exception {
    buildProjectAndOpenMainClass();
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByDoubleClick(START_DEBUG);
    consoles.waitExpectedTextIntoConsole(" Server startup in");
    editor.setCursorToLine(34);
    editor.setInactiveBreakpoint(34);
    menu.runCommand(
        TestMenuCommandsConstants.Run.RUN_MENU,
        TestMenuCommandsConstants.Run.EDIT_DEBUG_CONFIGURATION);
    debugConfig.createConfig(PROJECT);
    menu.runCommand(
        TestMenuCommandsConstants.Run.RUN_MENU,
        TestMenuCommandsConstants.Run.DEBUG,
        TestMenuCommandsConstants.Run.DEBUG + "/" + PROJECT);
    editor.waitAcitveBreakpoint(34);
    String appUrl =
        "http"
            + "://"
            + workspaceServiceClient.getServerAddressByPort(ws.getId(), 8080)
            + "/spring/guess";
    String requestMess = "6";
    CompletableFuture<String> instToRequestThread =
        debugUtils.gotoDebugAppAndSendRequest(appUrl, requestMess);
    editor.waitAcitveBreakpoint(34);
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_OVER);
    debugPanel.waitDebugHighlightedText("AdditonalClass.check();");
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_INTO);
    editor.waitTabFileWithSavedStatus("AdditonalClass");
    debugPanel.waitDebugHighlightedText(" someStr.toLowerCase();");
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_OVER);
    debugPanel.waitDebugHighlightedText("Operation.valueOf(\"SUBTRACT\").toString();");
    debugPanel.waitTextInVariablesPanel("someStr=\"hello Cdenvy\"");
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.STEP_OUT);
    debugPanel.waitTextInVariablesPanel("secretNum=");
    debugPanel.selectVarInVariablePanel("numGuessByUser=\"6\"");
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.CHANGE_VARIABLE);
    debugPanel.typeAndChangeVariable("\"7\"");
    debugPanel.waitTextInVariablesPanel("numGuessByUser=\"7\"");
    debugPanel.clickOnButton(DebugPanel.DebuggerButtonsPanel.RESUME_BTN_ID);
    assertTrue(instToRequestThread.get().contains("<html>"));
  }

  @Test(priority = 1)
  public void shouldOpenDebuggingFile() {
    buildProjectAndOpenMainClass();
    commandsPalette.openCommandPalette();
    commandsPalette.startCommandByDoubleClick(START_DEBUG);
    consoles.waitExpectedTextIntoConsole(" Server startup in");
    editor.setInactiveBreakpoint(26);
    seleniumWebDriver
        .switchTo()
        .activeElement()
        .sendKeys(Keys.SHIFT.toString() + Keys.F9.toString());
    editor.waitAcitveBreakpoint(26);
  }

  private void buildProjectAndOpenMainClass() {
    String absPathToClass = PROJECT + "/src/main/java/org/eclipse/qa/examples/AppController.java";
    projectExplorer.waitItem(PROJECT);
    loader.waitOnClosed();
    projectExplorer.selectItem(PROJECT);
    projectExplorer.invokeCommandWithContextMenu(
        ProjectExplorer.CommandsGoal.COMMON, PROJECT, BUILD);
    consoles.waitExpectedTextIntoConsole(TestBuildConstants.BUILD_SUCCESS);
    projectExplorer.quickRevealToItemWithJavaScript(absPathToClass);
    projectExplorer.openItemByPath(absPathToClass);
    editor.waitActiveEditor();
  }
}

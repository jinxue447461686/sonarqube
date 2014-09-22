/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.application;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.sonar.process.MinimumViableSystem;
import org.sonar.process.ProcessCommands;
import org.sonar.process.ProcessLogging;
import org.sonar.process.Props;
import org.sonar.process.StopWatcher;
import org.sonar.process.Stoppable;
import org.sonar.process.monitor.JavaCommand;
import org.sonar.process.monitor.Monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Entry-point of process that starts and monitors elasticsearch and web servers
 */
public class App implements Stoppable {

  private final Monitor monitor;

  public App() {
    this(Monitor.create());
  }

  App(Monitor monitor) {
    this.monitor = monitor;
  }

  public void start(Props props) {
    if (props.valueAsBoolean("sonar.enableStopCommand", false)) {
      // stop application when file <temp>/app.stop is created
      File tempDir = props.nonNullValueAsFile("sonar.path.temp");
      ProcessCommands commands = new ProcessCommands(tempDir, "app");
      new StopWatcher(commands, this).start();
    }
    monitor.start(createCommands(props));
    monitor.awaitTermination();
  }

  List<JavaCommand> createCommands(Props props) {
    List<JavaCommand> commands = new ArrayList<JavaCommand>();
    File homeDir = props.nonNullValueAsFile("sonar.path.home");
    File tempDir = props.nonNullValueAsFile("sonar.path.temp");
    JavaCommand elasticsearch = new JavaCommand("search");
    elasticsearch
      .setWorkDir(homeDir)
      .addJavaOptions(props.value(DefaultSettings.SEARCH_JAVA_OPTS))
      .setTempDir(tempDir.getAbsoluteFile())
      .setClassName("org.sonar.search.SearchServer")
      .setArguments(props.rawProperties())
      .addClasspath("./lib/common/*")
      .addClasspath("./lib/search/*");
    commands.add(elasticsearch);

    // do not yet start SQ in cluster mode. See SONAR-5483 & SONAR-5391
    if (StringUtils.isEmpty(props.value(DefaultSettings.CLUSTER_MASTER))) {
      JavaCommand webServer = new JavaCommand("web")
        .setWorkDir(homeDir)
        .addJavaOptions(props.nonNullValue(DefaultSettings.WEB_JAVA_OPTS))
        .setTempDir(tempDir.getAbsoluteFile())
        // required for logback tomcat valve
        .setEnvVariable("sonar.path.logs", props.nonNullValue("sonar.path.logs"))
        .setClassName("org.sonar.server.app.WebServer")
        .setArguments(props.rawProperties())
        .addClasspath("./lib/common/*")
        .addClasspath("./lib/server/*");
      String driverPath = props.value(JdbcSettings.PROPERTY_DRIVER_PATH);
      if (driverPath != null) {
        webServer.addClasspath(driverPath);
      }
      commands.add(webServer);
    }
    return commands;
  }

  static String starPath(File homeDir, String relativePath) {
    File dir = new File(homeDir, relativePath);
    return FilenameUtils.concat(dir.getAbsolutePath(), "*");
  }

  public static void main(String[] args) throws Exception {
    new MinimumViableSystem().check();
    CommandLineParser cli = new CommandLineParser();
    Properties rawProperties = cli.parseArguments(args);
    Props props = new PropsBuilder(rawProperties, new JdbcSettings()).build();
    new ProcessLogging().configure(props, "/org/sonar/application/logback.xml");

    App app = new App();
    app.start(props);
  }

  @Override
  public void stopAsync() {
    monitor.stopAsync();
  }
}

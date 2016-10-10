/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.user;

import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_GATE_ADMIN;
import static org.sonar.core.permission.GlobalPermissions.QUALITY_PROFILE_ADMIN;
import static org.sonar.core.permission.GlobalPermissions.SYSTEM_ADMIN;
import static org.sonar.db.user.UserTesting.newUserDto;
import static org.sonar.server.user.ServerUserSession.createForAnonymous;
import static org.sonar.server.user.ServerUserSession.createForUser;

public class ServerUserSessionTest {
  private static final String LOGIN = "marius";
  private static final String PROJECT_UUID = "ABCD";
  private static final String FILE_KEY = "com.foo:Bar:BarFile.xoo";
  private static final String FILE_UUID = "BCDE";

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private DbClient dbClient = db.getDbClient();
  private UserDto userDto;
  private ComponentDto project;
  private ComponentDto file;

  @Before
  public void setUp() throws Exception {
    project = db.components().insertComponent(ComponentTesting.newProjectDto(PROJECT_UUID));
    file = db.components().insertComponent(ComponentTesting.newFileDto(project, null, FILE_UUID).setKey(FILE_KEY));
    userDto = db.users().insertUser(newUserDto().setLogin(LOGIN));
  }

  @Test
  public void has_global_permission() {
    addGlobalPermissions("admin", "profileadmin");
    UserSession session = newUserSession(userDto);

    assertThat(session.hasPermission(QUALITY_PROFILE_ADMIN)).isTrue();
    assertThat(session.hasPermission(SYSTEM_ADMIN)).isTrue();
    assertThat(session.hasPermission(QUALITY_GATE_ADMIN)).isFalse();
  }

  @Test
  public void check_global_Permission_ok() {
    addGlobalPermissions("admin", "profileadmin");
    UserSession session = newUserSession(userDto);

    session.checkPermission(QUALITY_PROFILE_ADMIN);
  }

  @Test
  public void check_global_Permission_ko() {
    addGlobalPermissions("admin", "profileadmin");
    UserSession session = newUserSession(userDto);

    expectedException.expect(ForbiddenException.class);
    session.checkPermission(QUALITY_GATE_ADMIN);
  }

  @Test
  public void has_component_permission() {
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    assertThat(session.hasComponentPermission(UserRole.USER, FILE_KEY)).isTrue();
    assertThat(session.hasComponentPermission(UserRole.CODEVIEWER, FILE_KEY)).isFalse();
    assertThat(session.hasComponentPermission(UserRole.ADMIN, FILE_KEY)).isFalse();
  }

  @Test
  public void has_component_uuid_permission() {
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    assertThat(session.hasComponentUuidPermission(UserRole.USER, FILE_UUID)).isTrue();
    assertThat(session.hasComponentUuidPermission(UserRole.CODEVIEWER, FILE_UUID)).isFalse();
    assertThat(session.hasComponentUuidPermission(UserRole.ADMIN, FILE_UUID)).isFalse();
  }

  @Test
  public void has_component_permission_with_only_global_permission() {
    addGlobalPermissions(UserRole.USER);
    UserSession session = newUserSession(userDto);

    assertThat(session.hasComponentPermission(UserRole.USER, FILE_KEY)).isTrue();
    assertThat(session.hasComponentPermission(UserRole.CODEVIEWER, FILE_KEY)).isFalse();
    assertThat(session.hasComponentPermission(UserRole.ADMIN, FILE_KEY)).isFalse();
  }

  @Test
  public void has_component_uuid_permission_with_only_global_permission() {
    addGlobalPermissions(UserRole.USER);
    UserSession session = newUserSession(userDto);

    assertThat(session.hasComponentUuidPermission(UserRole.USER, FILE_UUID)).isTrue();
    assertThat(session.hasComponentUuidPermission(UserRole.CODEVIEWER, FILE_UUID)).isFalse();
    assertThat(session.hasComponentUuidPermission(UserRole.ADMIN, FILE_UUID)).isFalse();
  }

  @Test
  public void check_component_key_permission_ok() {
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    session.checkComponentPermission(UserRole.USER, FILE_KEY);
  }

  @Test
  public void check_component_key_permission_with_only_global_permission_ok() {
    addGlobalPermissions(UserRole.USER);
    UserSession session = newUserSession(userDto);

    session.checkComponentPermission(UserRole.USER, FILE_KEY);
  }

  @Test
  public void check_component_key_permission_ko() {
    ComponentDto project2 = db.components().insertComponent(ComponentTesting.newProjectDto());
    ComponentDto file2 = db.components().insertComponent(ComponentTesting.newFileDto(project2, null));
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    expectedException.expect(ForbiddenException.class);
    session.checkComponentPermission(UserRole.USER, file2.getKey());
  }

  @Test
  public void check_component_uuid_permission_ok() {
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    session.checkComponentUuidPermission(UserRole.USER, FILE_UUID);
  }

  @Test
  public void check_component_uuid_permission_ko() {
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    expectedException.expect(ForbiddenException.class);
    session.checkComponentUuidPermission(UserRole.USER, "another-uuid");
  }

  @Test
  public void check_component_key_permission_when_project_not_found() {
    ComponentDto project2 = db.components().insertComponent(ComponentTesting.newProjectDto());
    ComponentDto file2 = db.components().insertComponent(ComponentTesting.newFileDto(project2, null)
      // Simulate file is linked to an invalid project
      .setProjectUuid("INVALID"));
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    expectedException.expect(ForbiddenException.class);
    session.checkComponentPermission(UserRole.USER, file2.getKey());
  }

  @Test
  public void check_component_dto_permission_ko() {
    addProjectPermissions(project, UserRole.USER);
    UserSession session = newUserSession(userDto);

    expectedException.expect(ForbiddenException.class);
    session.checkComponentPermission(UserRole.USER, "another");
  }

  @Test
  public void deprecated_has_global_permission() throws Exception {
    addGlobalPermissions("profileadmin", "admin");
    UserSession session = newUserSession(userDto);

    assertThat(session.hasGlobalPermission(QUALITY_PROFILE_ADMIN)).isTrue();
    assertThat(session.hasGlobalPermission(SYSTEM_ADMIN)).isTrue();
    assertThat(session.hasGlobalPermission(QUALITY_GATE_ADMIN)).isFalse();
  }

  @Test
  public void deprecated_check_global_permission() throws Exception {
    addGlobalPermissions("profileadmin", "admin");
    UserSession session = newUserSession(userDto);

    session.checkGlobalPermission(QUALITY_PROFILE_ADMIN);
  }

  @Test
  public void fail_if_user_dto_is_null() throws Exception {
    expectedException.expect(NullPointerException.class);
    newUserSession(null);
  }

  @Test
  public void anonymous_user() throws Exception {
    UserSession session = newAnonymousSession();

    assertThat(session.getLogin()).isNull();
    assertThat(session.isLoggedIn()).isFalse();
  }

  @Test
  public void has_global_permission_for_anonymous() throws Exception {
    addAnyonePermissions(null, "profileadmin", "admin");
    UserSession session = newAnonymousSession();

    assertThat(session.getLogin()).isNull();
    assertThat(session.isLoggedIn()).isFalse();

    assertThat(session.hasPermission(GlobalPermissions.QUALITY_PROFILE_ADMIN)).isTrue();
    assertThat(session.hasPermission(GlobalPermissions.SYSTEM_ADMIN)).isTrue();
    assertThat(session.hasPermission(GlobalPermissions.QUALITY_GATE_ADMIN)).isFalse();
  }

  @Test
  public void has_project_permission_for_anonymous() throws Exception {
    addAnyonePermissions(project, UserRole.USER);
    UserSession session = newAnonymousSession();

    assertThat(session.hasComponentPermission(UserRole.USER, FILE_KEY)).isTrue();
    assertThat(session.hasComponentPermission(UserRole.CODEVIEWER, FILE_KEY)).isFalse();
    assertThat(session.hasComponentPermission(UserRole.ADMIN, FILE_KEY)).isFalse();
  }

  private ServerUserSession newUserSession(UserDto userDto) {
    return createForUser(dbClient, userDto);
  }

  private ServerUserSession newAnonymousSession() {
    return createForAnonymous(dbClient);
  }

  private void addGlobalPermissions(String... permissions) {
    addPermissions(null, permissions);
  }

  private void addProjectPermissions(ComponentDto component, String... permissions) {
    addPermissions(component, permissions);
  }

  private void addPermissions(@Nullable ComponentDto component, String... permissions) {
    for (String permission : permissions) {
      if (component == null) {
        db.users().insertPermissionOnUser(userDto, permission);
      } else {
        db.users().insertProjectPermissionOnUser(userDto, permission, component);
      }
    }
  }

  private void addAnyonePermissions(@Nullable ComponentDto component, String... permissions) {
    for (String permission : permissions) {
      if (component == null) {
        db.users().insertPermissionOnAnyone(permission);
      } else {
        db.users().insertProjectPermissionOnAnyone(permission, component);
      }
    }
  }

}

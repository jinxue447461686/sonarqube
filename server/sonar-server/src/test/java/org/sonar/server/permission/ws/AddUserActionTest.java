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
package org.sonar.server.permission.ws;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.web.UserRole;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.ServerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.core.permission.GlobalPermissions.SYSTEM_ADMIN;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonar.db.component.ComponentTesting.newView;
import static org.sonar.server.permission.ws.AddUserAction.ACTION;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.CONTROLLER;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PERMISSION;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PROJECT_ID;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PROJECT_KEY;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_USER_LOGIN;

public class AddUserActionTest extends BasePermissionWsTest<AddUserAction> {

  private static final String A_PROJECT_UUID = "project-uuid";
  private static final String A_PROJECT_KEY = "project-key";

  private UserDto user;

  @Before
  public void setUp() {
    user = db.users().insertUser("ray.bradbury");
  }

  @Override
  protected AddUserAction buildWsAction() {
    return new AddUserAction(db.getDbClient(), newPermissionUpdater(), newPermissionWsSupport());
  }

  @Test
  public void add_permission_to_user() throws Exception {
    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();

    assertThat(db.users().selectUserPermissions(user, null)).containsOnly(SYSTEM_ADMIN);
  }

  @Test
  public void add_permission_to_project_referenced_by_its_id() throws Exception {
    ComponentDto project = db.components().insertComponent(newProjectDto("project-uuid").setKey("project-key"));

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, project.uuid())
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();

    assertThat(db.users().selectUserPermissions(user, null)).isEmpty();
    assertThat(db.users().selectUserPermissions(user, project)).containsOnly(SYSTEM_ADMIN);
  }

  @Test
  public void add_permission_to_project_referenced_by_its_key() throws Exception {
    ComponentDto project = db.components().insertComponent(newProjectDto("project-uuid").setKey("project-key"));

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_KEY, project.getKey())
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();

    assertThat(db.users().selectUserPermissions(user, null)).isEmpty();
    assertThat(db.users().selectUserPermissions(user, project)).containsOnly(SYSTEM_ADMIN);
  }

  @Test
  public void add_permission_to_view() throws Exception {
    ComponentDto view = db.components().insertComponent(newView("view-uuid").setKey("view-key"));

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, view.uuid())
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();

    assertThat(db.users().selectUserPermissions(user, null)).isEmpty();
    assertThat(db.users().selectUserPermissions(user, view)).containsOnly(SYSTEM_ADMIN);
  }

  @Test
  public void fail_when_project_uuid_is_unknown() throws Exception {
    expectedException.expect(NotFoundException.class);

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, "unknown-project-uuid")
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();
  }

  @Test
  public void fail_when_project_permission_without_project() throws Exception {
    expectedException.expect(BadRequestException.class);

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, UserRole.ISSUE_ADMIN)
      .execute();
  }

  @Test
  public void fail_when_component_is_not_a_project() throws Exception {
    db.components().insertComponent(newFileDto(newProjectDto("project-uuid"), null, "file-uuid"));

    expectedException.expect(BadRequestException.class);

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, "file-uuid")
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();
  }

  @Test
  public void fail_when_get_request() throws Exception {
    expectedException.expect(ServerException.class);

    loginAsAdmin();
    wsTester.newGetRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, "george.orwell")
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();
  }

  @Test
  public void fail_when_user_login_is_missing() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .execute();
  }

  @Test
  public void fail_when_permission_is_missing() throws Exception {
    expectedException.expect(NotFoundException.class);

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, "jrr.tolkien")
      .execute();
  }

  @Test
  public void fail_when_project_uuid_and_project_key_are_provided() throws Exception {
    db.components().insertComponent(newProjectDto(A_PROJECT_UUID).setKey(A_PROJECT_KEY));

    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Project id or project key can be provided, not both.");

    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_PERMISSION, SYSTEM_ADMIN)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PROJECT_ID, "project-uuid")
      .setParam(PARAM_PROJECT_KEY, "project-key")
      .execute();
  }

  private void loginAsAdmin() {
    userSession.login("admin").setGlobalPermissions(SYSTEM_ADMIN);
  }
}

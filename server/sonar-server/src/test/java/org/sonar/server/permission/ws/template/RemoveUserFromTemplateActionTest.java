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
package org.sonar.server.permission.ws.template;

import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.permission.PermissionQuery;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.permission.ws.BasePermissionWsTest;
import org.sonar.server.ws.WsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.web.UserRole.CODEVIEWER;
import static org.sonar.api.web.UserRole.ISSUE_ADMIN;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.CONTROLLER;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PERMISSION;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_TEMPLATE_NAME;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_USER_LOGIN;

public class RemoveUserFromTemplateActionTest extends BasePermissionWsTest<RemoveUserFromTemplateAction> {

  private static final String DEFAULT_PERMISSION = CODEVIEWER;
  private static final String ACTION = "remove_user_from_template";

  private UserDto user;
  private PermissionTemplateDto template;

  @Override
  protected RemoveUserFromTemplateAction buildWsAction() {
    return new RemoveUserFromTemplateAction(db.getDbClient(), newPermissionWsSupport(), userSession);
  }

  @Before
  public void setUp() {
    user = userTester.insertUser("user-login");
    template = insertTemplate();
    addUserToTemplate(user, template, DEFAULT_PERMISSION);
  }

  @Test
  public void remove_user_from_template() throws Exception {
    loginAsAdmin();
    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);

    assertThat(getLoginsInTemplateAndPermission(template.getId(), DEFAULT_PERMISSION)).isEmpty();
  }

  @Test
  public void remove_user_from_template_by_name_case_insensitive() throws Exception {
    loginAsAdmin();
    wsTester.newPostRequest(CONTROLLER, ACTION)
      .setParam(PARAM_USER_LOGIN, user.getLogin())
      .setParam(PARAM_PERMISSION, DEFAULT_PERMISSION)
      .setParam(PARAM_TEMPLATE_NAME, template.getName().toUpperCase())
      .execute();

    assertThat(getLoginsInTemplateAndPermission(template.getId(), DEFAULT_PERMISSION)).isEmpty();
  }

  @Test
  public void remove_user_from_template_twice_without_failing() throws Exception {
    loginAsAdmin();
    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);
    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);

    assertThat(getLoginsInTemplateAndPermission(template.getId(), DEFAULT_PERMISSION)).isEmpty();
  }

  @Test
  public void keep_user_permission_not_removed() throws Exception {
    addUserToTemplate(user, template, ISSUE_ADMIN);

    loginAsAdmin();
    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);

    assertThat(getLoginsInTemplateAndPermission(template.getId(), DEFAULT_PERMISSION)).isEmpty();
    assertThat(getLoginsInTemplateAndPermission(template.getId(), ISSUE_ADMIN)).containsExactly(user.getLogin());
  }

  @Test
  public void keep_other_users_when_one_user_removed() throws Exception {
    UserDto newUser = userTester.insertUser("new-login");
    addUserToTemplate(newUser, template, DEFAULT_PERMISSION);

    loginAsAdmin();
    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);

    assertThat(getLoginsInTemplateAndPermission(template.getId(), DEFAULT_PERMISSION)).containsExactly("new-login");
  }

  @Test
  public void fail_if_not_a_project_permission() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest(user.getLogin(), template.getUuid(), GlobalPermissions.PROVISIONING);
  }

  @Test
  public void fail_if_insufficient_privileges() throws Exception {
    expectedException.expect(ForbiddenException.class);
    userSession.login("john").setGlobalPermissions(GlobalPermissions.QUALITY_PROFILE_ADMIN);

    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);
  }

  @Test
  public void fail_if_not_logged_in() throws Exception {
    expectedException.expect(UnauthorizedException.class);
    userSession.anonymous();

    newRequest(user.getLogin(), template.getUuid(), DEFAULT_PERMISSION);
  }

  @Test
  public void fail_if_user_missing() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest(null, template.getUuid(), DEFAULT_PERMISSION);
  }

  @Test
  public void fail_if_permission_missing() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    loginAsAdmin();
    newRequest(user.getLogin(), template.getUuid(), null);
  }

  @Test
  public void fail_if_template_missing() throws Exception {
    expectedException.expect(BadRequestException.class);

    loginAsAdmin();
    newRequest(user.getLogin(), null, DEFAULT_PERMISSION);
  }

  @Test
  public void fail_if_user_does_not_exist() throws Exception {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("User with login 'unknown-login' is not found");

    loginAsAdmin();
    newRequest("unknown-login", template.getUuid(), DEFAULT_PERMISSION);
  }

  @Test
  public void fail_if_template_key_does_not_exist() throws Exception {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("Permission template with id 'unknown-key' is not found");

    loginAsAdmin();
    newRequest(user.getLogin(), "unknown-key", DEFAULT_PERMISSION);
  }

  private void newRequest(@Nullable String userLogin, @Nullable String templateKey, @Nullable String permission) throws Exception {
    WsTester.TestRequest request = wsTester.newPostRequest(CONTROLLER, ACTION);
    if (userLogin != null) {
      request.setParam(PARAM_USER_LOGIN, userLogin);
    }
    if (templateKey != null) {
      request.setParam(org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_TEMPLATE_ID, templateKey);
    }
    if (permission != null) {
      request.setParam(PARAM_PERMISSION, permission);
    }

    request.execute();
  }

  private List<String> getLoginsInTemplateAndPermission(long templateId, String permission) {
    PermissionQuery permissionQuery = PermissionQuery.builder().setPermission(permission).build();
    return db.getDbClient().permissionTemplateDao()
      .selectUserLoginsByQueryAndTemplate(db.getSession(), permissionQuery, templateId);
  }

  private void addUserToTemplate(UserDto user, PermissionTemplateDto template, String permission) {
    db.getDbClient().permissionTemplateDao().insertUserPermission(db.getSession(), template.getId(), user.getId(), permission);
    db.commit();
  }

  private void loginAsAdmin() {
    userSession.login().setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  }
}

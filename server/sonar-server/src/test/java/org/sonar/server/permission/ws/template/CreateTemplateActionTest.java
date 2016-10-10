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

import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.permission.ws.BasePermissionWsTest;
import org.sonar.server.ws.WsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.test.JsonAssert.assertJson;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.CONTROLLER;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_DESCRIPTION;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_NAME;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PROJECT_KEY_PATTERN;

public class CreateTemplateActionTest extends BasePermissionWsTest<CreateTemplateAction> {

  private System2 system = mock(System2.class);

  @Override
  protected CreateTemplateAction buildWsAction() {
    return new CreateTemplateAction(db.getDbClient(), userSession, system);
  }

  @Before
  public void setUp() {
    userSession.login().setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
    when(system.now()).thenReturn(1440512328743L);
  }

  @Test
  public void create_full_permission_template() throws Exception {
    WsTester.Result result = newRequest("Finance", "Permissions for financially related projects", ".*\\.finance\\..*");

    assertJson(result.outputAsString())
      .ignoreFields("id")
      .isSimilarTo(getClass().getResource("create_template-example.json"));
    PermissionTemplateDto finance = db.getDbClient().permissionTemplateDao().selectByName(db.getSession(), "Finance");
    assertThat(finance.getName()).isEqualTo("Finance");
    assertThat(finance.getDescription()).isEqualTo("Permissions for financially related projects");
    assertThat(finance.getKeyPattern()).isEqualTo(".*\\.finance\\..*");
    assertThat(finance.getUuid()).isNotEmpty();
    assertThat(finance.getCreatedAt().getTime()).isEqualTo(1440512328743L);
    assertThat(finance.getUpdatedAt().getTime()).isEqualTo(1440512328743L);
  }

  @Test
  public void create_minimalist_permission_template() throws Exception {
    newRequest("Finance", null, null);

    PermissionTemplateDto finance = db.getDbClient().permissionTemplateDao().selectByName(db.getSession(), "Finance");
    assertThat(finance.getName()).isEqualTo("Finance");
    assertThat(finance.getDescription()).isNullOrEmpty();
    assertThat(finance.getKeyPattern()).isNullOrEmpty();
    assertThat(finance.getUuid()).isNotEmpty();
    assertThat(finance.getCreatedAt().getTime()).isEqualTo(1440512328743L);
    assertThat(finance.getUpdatedAt().getTime()).isEqualTo(1440512328743L);
  }

  @Test
  public void fail_if_name_not_provided() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    newRequest(null, null, null);
  }

  @Test
  public void fail_if_name_empty() throws Exception {
    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("The template name must not be blank");

    newRequest("", null, null);
  }

  @Test
  public void fail_if_regexp_if_not_valid() throws Exception {
    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("The 'projectKeyPattern' parameter must be a valid Java regular expression. '[azerty' was passed");

    newRequest("Finance", null, "[azerty");
  }

  @Test
  public void fail_if_name_already_exists_in_database_case_insensitive() throws Exception {
    PermissionTemplateDto template = insertTemplate();

    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("A template with the name '" + template.getName() + "' already exists (case insensitive).");

    newRequest(template.getName(), null, null);
  }

  @Test
  public void fail_if_not_logged_in() throws Exception {
    expectedException.expect(UnauthorizedException.class);
    userSession.anonymous();

    newRequest("Finance", null, null);
  }

  @Test
  public void fail_if_not_admin() throws Exception {
    expectedException.expect(ForbiddenException.class);
    userSession.setGlobalPermissions(GlobalPermissions.QUALITY_PROFILE_ADMIN);

    newRequest("Finance", null, null);
  }

  private WsTester.Result newRequest(@Nullable String name, @Nullable String description, @Nullable String projectPattern) throws Exception {
    WsTester.TestRequest request = wsTester.newPostRequest(CONTROLLER, "create_template");
    if (name != null) {
      request.setParam(PARAM_NAME, name);
    }
    if (description != null) {
      request.setParam(PARAM_DESCRIPTION, description);
    }
    if (projectPattern != null) {
      request.setParam(PARAM_PROJECT_KEY_PATTERN, projectPattern);
    }

    return request.execute();
  }
}

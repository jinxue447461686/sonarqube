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

import javax.annotation.Nullable;
import org.junit.Test;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.permission.template.PermissionTemplateGroupDto;
import org.sonar.db.user.GroupDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.permission.ws.template.TemplateGroupsAction;
import org.sonar.server.ws.WsTester;
import org.sonarqube.ws.WsPermissions.WsGroupsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.api.server.ws.WebService.Param.PAGE;
import static org.sonar.api.server.ws.WebService.Param.PAGE_SIZE;
import static org.sonar.api.server.ws.WebService.Param.TEXT_QUERY;
import static org.sonar.api.web.UserRole.ADMIN;
import static org.sonar.api.web.UserRole.CODEVIEWER;
import static org.sonar.api.web.UserRole.ISSUE_ADMIN;
import static org.sonar.api.web.UserRole.USER;
import static org.sonar.db.permission.template.PermissionTemplateTesting.newPermissionTemplateDto;
import static org.sonar.db.permission.template.PermissionTemplateTesting.newPermissionTemplateGroupDto;
import static org.sonar.db.user.GroupTesting.newGroupDto;
import static org.sonar.test.JsonAssert.assertJson;
import static org.sonarqube.ws.MediaTypes.PROTOBUF;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.CONTROLLER;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_PERMISSION;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_TEMPLATE_ID;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_TEMPLATE_NAME;

public class TemplateGroupsActionTest extends BasePermissionWsTest<TemplateGroupsAction> {

  @Override
  protected TemplateGroupsAction buildWsAction() {
    return new TemplateGroupsAction(db.getDbClient(), userSession, newPermissionWsSupport());
  }

  @Test
  public void template_groups_of_json_example() throws Exception {
    GroupDto adminGroup = insertGroupOnDefaultOrganization("sonar-administrators", "System administrators");
    GroupDto userGroup = insertGroupOnDefaultOrganization("sonar-users", "Any new users created will automatically join this group");

    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));
    addGroupToTemplate(newPermissionTemplateGroup(ISSUE_ADMIN, template.getId(), adminGroup.getId()));
    addGroupToTemplate(newPermissionTemplateGroup(ISSUE_ADMIN, template.getId(), userGroup.getId()));
    // Anyone group
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), null));
    addGroupToTemplate(newPermissionTemplateGroup(ISSUE_ADMIN, template.getId(), null));
    commit();

    loginAsAdmin();
    String response = newRequest()
      .setParam(PARAM_PERMISSION, ISSUE_ADMIN)
      .setParam(PARAM_TEMPLATE_ID, template.getUuid())
      .execute().outputAsString();

    assertJson(response)
      .ignoreFields("id")
      .withStrictArrayOrder()
      .isSimilarTo(getClass().getResource("template_groups-example.json"));
  }

  @Test
  public void return_all_permissions_of_matching_groups() throws Exception {
    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));

    GroupDto group1 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-1-name");
    addGroupToTemplate(newPermissionTemplateGroup(CODEVIEWER, template.getId(), group1.getId()));
    addGroupToTemplate(newPermissionTemplateGroup(ADMIN, template.getId(), group1.getId()));

    GroupDto group2 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-2-name");
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group2.getId()));
    addGroupToTemplate(newPermissionTemplateGroup(ADMIN, template.getId(), group2.getId()));

    GroupDto group3 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-3-name");

    // Anyone
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), null));
    addGroupToTemplate(newPermissionTemplateGroup(ISSUE_ADMIN, template.getId(), null));

    PermissionTemplateDto anotherTemplate = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-2"));
    addGroupToTemplate(newPermissionTemplateGroup(ADMIN, anotherTemplate.getId(), group3.getId()));
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_TEMPLATE_ID, template.getUuid())
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("Anyone", "group-1-name", "group-2-name");
    assertThat(response.getGroups(0).getPermissionsList()).containsOnly("user", "issueadmin");
    assertThat(response.getGroups(1).getPermissionsList()).containsOnly("codeviewer", "admin");
    assertThat(response.getGroups(2).getPermissionsList()).containsOnly("user", "admin");
  }

  @Test
  public void search_by_permission() throws Exception {
    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));

    GroupDto group1 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-1-name");
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group1.getId()));
    addGroupToTemplate(newPermissionTemplateGroup(CODEVIEWER, template.getId(), group1.getId()));

    GroupDto group2 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-2-name");
    addGroupToTemplate(newPermissionTemplateGroup(ADMIN, template.getId(), group2.getId()));

    GroupDto group3 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-3-name");

    // Anyone
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), null));

    PermissionTemplateDto anotherTemplate = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-2"));
    addGroupToTemplate(newPermissionTemplateGroup(ADMIN, anotherTemplate.getId(), group3.getId()));
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_PERMISSION, USER)
      .setParam(PARAM_TEMPLATE_ID, template.getUuid())
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("Anyone", "group-1-name");
    assertThat(response.getGroups(0).getPermissionsList()).containsOnly("user");
    assertThat(response.getGroups(1).getPermissionsList()).containsOnly("user", "codeviewer");
  }

  @Test
  public void search_by_template_name() throws Exception {
    GroupDto group1 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-1-name");
    GroupDto group2 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-2-name");
    GroupDto group3 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-3-name");

    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group1.getId()));
    addGroupToTemplate(newPermissionTemplateGroup(ADMIN, template.getId(), group2.getId()));
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), null));

    PermissionTemplateDto anotherTemplate = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-2"));
    addGroupToTemplate(newPermissionTemplateGroup(USER, anotherTemplate.getId(), group1.getId()));
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_TEMPLATE_NAME, template.getName())
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("Anyone", "group-1-name", "group-2-name");
  }

  @Test
  public void search_with_pagination() throws Exception {
    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));
    GroupDto group1 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-1-name");
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group1.getId()));
    GroupDto group2 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-2-name");
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group2.getId()));
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_PERMISSION, USER)
      .setParam(PARAM_TEMPLATE_NAME, template.getName())
      .setParam(PAGE, "2")
      .setParam(PAGE_SIZE, "1")
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("group-2-name");
  }

  @Test
  public void search_with_text_query() throws Exception {
    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));
    GroupDto group1 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-1-name");
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group1.getId()));
    GroupDto group2 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-2-name");
    GroupDto group3 = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-3");
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_TEMPLATE_NAME, template.getName())
      .setParam(TEXT_QUERY, "-nam")
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("group-1-name", "group-2-name");
  }

  @Test
  public void search_with_text_query_return_all_groups_even_when_no_permission_set() throws Exception {
    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));
    db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-1-name");
    db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-2-name");
    db.users().insertGroup(defaultOrganizationProvider.getDto(), "group-3-name");
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_TEMPLATE_ID, template.getUuid())
      .setParam(TEXT_QUERY, "-name")
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("group-1-name", "group-2-name", "group-3-name");
    assertThat(response.getGroups(0).getPermissionsList()).isEmpty();
    assertThat(response.getGroups(1).getPermissionsList()).isEmpty();
    assertThat(response.getGroups(2).getPermissionsList()).isEmpty();
  }

  @Test
  public void search_with_text_query_return_anyone_group_even_when_no_permission_set() throws Exception {
    PermissionTemplateDto template = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));
    GroupDto group = db.users().insertGroup(defaultOrganizationProvider.getDto(), "group");
    addGroupToTemplate(newPermissionTemplateGroup(USER, template.getId(), group.getId()));
    commit();

    loginAsAdmin();
    byte[] output = newRequest()
      .setMediaType(PROTOBUF)
      .setParam(PARAM_TEMPLATE_ID, template.getUuid())
      .setParam(TEXT_QUERY, "nyo")
      .execute()
      .output();
    WsGroupsResponse response = WsGroupsResponse.parseFrom(output);

    assertThat(response.getGroupsList()).extracting("name").containsExactly("Anyone");
    assertThat(response.getGroups(0).getPermissionsList()).isEmpty();
  }

  @Test
  public void fail_if_not_logged_in() throws Exception {
    userSession.anonymous();

    PermissionTemplateDto template1 = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));

    expectedException.expect(UnauthorizedException.class);
    newRequest()
      .setParam(PARAM_PERMISSION, USER)
      .setParam(PARAM_TEMPLATE_ID, template1.getUuid())
      .execute();
  }

  @Test
  public void fail_if_insufficient_privileges() throws Exception {
    userSession.login();

    PermissionTemplateDto template1 = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));

    expectedException.expect(ForbiddenException.class);
    newRequest()
      .setParam(PARAM_PERMISSION, USER)
      .setParam(PARAM_TEMPLATE_ID, template1.getUuid())
      .execute();
  }

  @Test
  public void fail_if_template_uuid_and_name_provided() throws Exception {
    loginAsAdmin();

    PermissionTemplateDto template1 = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));

    expectedException.expect(BadRequestException.class);
    newRequest()
      .setParam(PARAM_PERMISSION, USER)
      .setParam(PARAM_TEMPLATE_ID, template1.getUuid())
      .setParam(PARAM_TEMPLATE_NAME, template1.getName())
      .execute();
  }

  @Test
  public void fail_if_template_uuid_nor_name_provided() throws Exception {
    loginAsAdmin();

    expectedException.expect(BadRequestException.class);
    newRequest()
      .setParam(PARAM_PERMISSION, USER)
      .execute();
  }

  @Test
  public void fail_if_template_is_not_found() throws Exception {
    loginAsAdmin();

    expectedException.expect(NotFoundException.class);
    newRequest()
      .setParam(PARAM_PERMISSION, USER)
      .setParam(PARAM_TEMPLATE_ID, "unknown-uuid")
      .execute();
  }

  @Test
  public void fail_if_not_a_project_permission() throws Exception {
    loginAsAdmin();

    PermissionTemplateDto template1 = db.getDbClient().permissionTemplateDao().insert(db.getSession(), newPermissionTemplateDto().setUuid("template-uuid-1"));

    expectedException.expect(IllegalArgumentException.class);
    newRequest()
      .setParam(PARAM_PERMISSION, GlobalPermissions.QUALITY_GATE_ADMIN)
      .setParam(PARAM_TEMPLATE_ID, template1.getUuid())
      .execute();
  }

  private GroupDto insertGroupOnDefaultOrganization(String name, String description) {
    return db.users().insertGroup(newGroupDto().setName(name).setDescription(description).setOrganizationUuid(defaultOrganizationProvider.get().getUuid()));
  }

  private void addGroupToTemplate(PermissionTemplateGroupDto permissionTemplateGroup) {
    db.getDbClient().permissionTemplateDao().insertGroupPermission(db.getSession(), permissionTemplateGroup);
  }

  private static PermissionTemplateGroupDto newPermissionTemplateGroup(String permission, long templateId, @Nullable Long groupId) {
    return newPermissionTemplateGroupDto()
      .setPermission(permission)
      .setTemplateId(templateId)
      .setGroupId(groupId);
  }

  private void commit() {
    db.commit();
  }

  private WsTester.TestRequest newRequest() {
    return wsTester.newPostRequest(CONTROLLER, "template_groups");
  }

  private void loginAsAdmin() {
    userSession.login("login").setGlobalPermissions(ADMIN);
  }
}

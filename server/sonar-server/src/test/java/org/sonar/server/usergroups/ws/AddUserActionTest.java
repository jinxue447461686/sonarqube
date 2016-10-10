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
package org.sonar.server.usergroups.ws;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbTester;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationTesting;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.UserDto;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.organization.DefaultOrganizationProviderRule;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.db.organization.OrganizationTesting.newOrganizationDto;
import static org.sonar.server.usergroups.ws.GroupWsSupport.PARAM_GROUP_NAME;
import static org.sonar.server.usergroups.ws.GroupWsSupport.PARAM_LOGIN;
import static org.sonar.server.usergroups.ws.GroupWsSupport.PARAM_ORGANIZATION_KEY;

public class AddUserActionTest {

  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private DefaultOrganizationProviderRule defaultOrganizationProvider = DefaultOrganizationProviderRule.create(db);
  private WsTester ws;

  @Before
  public void setUp() {
    ws = new WsTester(new UserGroupsWs(new AddUserAction(db.getDbClient(), userSession, newGroupWsSupport())));
  }

  @Test
  public void add_user_to_group_referenced_by_its_id() throws Exception {
    GroupDto group = db.users().insertGroup(defaultOrganizationProvider.getDto(), "admins");
    UserDto user = db.users().insertUser("my-admin");

    loginAsAdmin();
    newRequest()
      .setParam("id", group.getId().toString())
      .setParam("login", user.getLogin())
      .execute()
      .assertNoContent();

    assertThat(db.users().selectGroupIdsOfUser(user)).containsOnly(group.getId());
  }

  @Test
  public void add_user_to_group_referenced_by_its_name() throws Exception {
    GroupDto group = db.users().insertGroup(defaultOrganizationProvider.getDto(), "a-group");
    UserDto user = db.users().insertUser("user_login");

    loginAsAdmin();
    newRequest()
      .setParam(PARAM_GROUP_NAME, group.getName())
      .setParam(PARAM_LOGIN, user.getLogin())
      .execute()
      .assertNoContent();

    assertThat(db.users().selectGroupIdsOfUser(user)).containsOnly(group.getId());
  }

  @Test
  public void add_user_to_group_referenced_by_its_name_and_organization() throws Exception {
    OrganizationDto org = OrganizationTesting.insert(db, newOrganizationDto());
    GroupDto group = db.users().insertGroup(org, "a-group");
    UserDto user = db.users().insertUser("user_login");

    loginAsAdmin();
    newRequest()
      .setParam(PARAM_ORGANIZATION_KEY, org.getKey())
      .setParam(PARAM_GROUP_NAME, group.getName())
      .setParam(PARAM_LOGIN, user.getLogin())
      .execute()
      .assertNoContent();

    assertThat(db.users().selectGroupIdsOfUser(user)).containsOnly(group.getId());
  }

  @Test
  public void add_user_to_another_group() throws Exception {
    GroupDto admins = db.users().insertGroup(defaultOrganizationProvider.getDto(), "admins");
    GroupDto users = db.users().insertGroup(defaultOrganizationProvider.getDto(), "users");
    UserDto user = db.users().insertUser("my-admin");
    db.users().insertMember(users, user);

    loginAsAdmin();
    newRequest()
      .setParam("id", admins.getId().toString())
      .setParam("login", user.getLogin())
      .execute()
      .assertNoContent();

    assertThat(db.users().selectGroupIdsOfUser(user)).containsOnly(admins.getId(), users.getId());
  }

  @Test
  public void user_is_already_member_of_group() throws Exception {
    GroupDto users = db.users().insertGroup(defaultOrganizationProvider.getDto(), "users");
    UserDto user = db.users().insertUser("my-admin");
    db.users().insertMember(users, user);

    loginAsAdmin();
    newRequest()
      .setParam("id", users.getId().toString())
      .setParam("login", user.getLogin())
      .execute()
      .assertNoContent();

    // do not insert duplicated row
    assertThat(db.users().selectGroupIdsOfUser(user)).hasSize(1).containsOnly(users.getId());
  }

  @Test
  public void group_has_multiple_members() throws Exception {
    GroupDto users = db.users().insertGroup(defaultOrganizationProvider.getDto(), "user");
    UserDto user1 = db.users().insertUser("user1");
    UserDto user2 = db.users().insertUser("user2");
    db.users().insertMember(users, user1);

    loginAsAdmin();
    newRequest()
      .setParam("id", users.getId().toString())
      .setParam("login", user2.getLogin())
      .execute()
      .assertNoContent();

    assertThat(db.users().selectGroupIdsOfUser(user1)).containsOnly(users.getId());
    assertThat(db.users().selectGroupIdsOfUser(user2)).containsOnly(users.getId());
  }

  @Test
  public void fail_if_group_does_not_exist() throws Exception {
    UserDto user = db.users().insertUser("my-admin");

    expectedException.expect(NotFoundException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", "42")
      .setParam("login", user.getLogin())
      .execute();
  }

  @Test
  public void fail_if_user_does_not_exist() throws Exception {
    GroupDto group = db.users().insertGroup(defaultOrganizationProvider.getDto(), "admins");

    expectedException.expect(NotFoundException.class);

    loginAsAdmin();
    newRequest()
      .setParam("id", group.getId().toString())
      .setParam("login", "my-admin")
      .execute();
  }

  @Test
  public void fail_if_not_administrator() throws Exception {
    GroupDto group = db.users().insertGroup(defaultOrganizationProvider.getDto(), "admins");
    UserDto user = db.users().insertUser("my-admin");

    expectedException.expect(UnauthorizedException.class);

    newRequest()
      .setParam("id", group.getId().toString())
      .setParam("login", user.getLogin())
      .execute();
  }

  private WsTester.TestRequest newRequest() {
    return ws.newPostRequest("api/user_groups", "add_user");
  }

  private void loginAsAdmin() {
    userSession.login("admin").setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  }

  private GroupWsSupport newGroupWsSupport() {
    return new GroupWsSupport(db.getDbClient(), defaultOrganizationProvider);
  }

}

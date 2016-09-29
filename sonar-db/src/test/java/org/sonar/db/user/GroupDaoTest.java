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
package org.sonar.db.user;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.System2;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.RowNotFoundException;
import org.sonar.db.organization.OrganizationDto;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.db.user.GroupTesting.newGroupDto;

public class GroupDaoTest {

  private static final long NOW = 1_500_000L;
  private static final OrganizationDto AN_ORGANIZATION = new OrganizationDto()
    .setKey("an-org")
    .setName("An Org")
    .setUuid("abcde");

  private System2 system2 = mock(System2.class);

  @Rule
  public DbTester db = DbTester.create(system2);
  private final DbSession dbSession = db.getSession();
  private GroupDao underTest = new GroupDao(system2);

  // not static as group id is changed in each test
  private final GroupDto aGroup = new GroupDto()
    .setName("the-name")
    .setDescription("the description")
    .setOrganizationUuid(AN_ORGANIZATION.getUuid());

  @Before
  public void setUp() {
    when(system2.now()).thenReturn(NOW);
    db.getDbClient().organizationDao().insert(dbSession, AN_ORGANIZATION);
  }

  @Test
  public void selectByName() {
    db.getDbClient().groupDao().insert(dbSession, aGroup);

    GroupDto group = underTest.selectByName(dbSession, AN_ORGANIZATION.getKey(), aGroup.getName()).get();

    assertThat(group.getId()).isNotNull();
    assertThat(group.getOrganizationUuid()).isEqualTo(aGroup.getOrganizationUuid());
    assertThat(group.getName()).isEqualTo(aGroup.getName());
    assertThat(group.getDescription()).isEqualTo(aGroup.getDescription());
    assertThat(group.getCreatedAt()).isEqualTo(new Date(NOW));
    assertThat(group.getUpdatedAt()).isEqualTo(new Date(NOW));
  }

  @Test
  public void selectByName_returns_absent() {
    Optional<GroupDto> group = underTest.selectByName(dbSession, AN_ORGANIZATION.getKey(), "missing");

    assertThat(group).isNotPresent();
  }

  @Test
  public void test_insert_and_selectOrFailByName() {
    db.getDbClient().groupDao().insert(dbSession, aGroup);

    GroupDto group = underTest.selectOrFailByName(dbSession, aGroup.getName());

    assertThat(group.getId()).isNotNull();
    assertThat(group.getOrganizationUuid()).isEqualTo(aGroup.getOrganizationUuid());
    assertThat(group.getName()).isEqualTo(aGroup.getName());
    assertThat(group.getDescription()).isEqualTo(aGroup.getDescription());
    assertThat(group.getCreatedAt()).isEqualTo(new Date(NOW));
    assertThat(group.getUpdatedAt()).isEqualTo(new Date(NOW));
  }

  @Test(expected = RowNotFoundException.class)
  public void selectOrFailByName_throws_NFE_if_not_found() {
    underTest.selectOrFailByName(dbSession, "missing");
  }

  @Test
  public void selectOrFailById() {
    db.getDbClient().groupDao().insert(dbSession, aGroup);

    GroupDto group = underTest.selectOrFailById(dbSession, aGroup.getId());

    assertThat(group.getName()).isEqualTo(aGroup.getName());
  }

  @Test
  public void selectByUserLogin() {
    db.prepareDbUnit(getClass(), "find_by_user_login.xml");

    assertThat(underTest.selectByUserLogin(dbSession, "john")).hasSize(2);
    assertThat(underTest.selectByUserLogin(dbSession, "max")).isEmpty();
  }

  @Test
  public void selectByNames() {
    underTest.insert(dbSession, newGroupDto().setName("group1"));
    underTest.insert(dbSession, newGroupDto().setName("group2"));
    underTest.insert(dbSession, newGroupDto().setName("group3"));
    dbSession.commit();

    assertThat(underTest.selectByNames(dbSession, asList("group1", "group2", "group3"))).hasSize(3);
    assertThat(underTest.selectByNames(dbSession, singletonList("group1"))).hasSize(1);
    assertThat(underTest.selectByNames(dbSession, asList("group1", "unknown"))).hasSize(1);
    assertThat(underTest.selectByNames(dbSession, Collections.emptyList())).isEmpty();
  }

  @Test
  public void update() {
    db.getDbClient().groupDao().insert(dbSession, aGroup);
    GroupDto dto = new GroupDto()
      .setId(aGroup.getId())
      .setName("new-name")
      .setDescription("New description")
      .setOrganizationUuid("another-org")
      .setCreatedAt(new Date(NOW + 1_000L));

    underTest.update(dbSession, dto);

    GroupDto reloaded = underTest.selectById(dbSession, aGroup.getId());

    // verify mutable fields
    assertThat(reloaded.getName()).isEqualTo("new-name");
    assertThat(reloaded.getDescription()).isEqualTo("New description");

    // immutable fields --> to be ignored
    assertThat(reloaded.getOrganizationUuid()).isEqualTo(aGroup.getOrganizationUuid());
    assertThat(reloaded.getCreatedAt()).isEqualTo(aGroup.getCreatedAt());
  }

  @Test
  public void selectByQuery() {
    db.prepareDbUnit(getClass(), "select_by_query.xml");

    /*
     * Ordering and paging are not fully tested, case insensitive sort is broken on MySQL
     */

    // Null query
    assertThat(underTest.selectByQuery(dbSession, null, 0, 10))
      .hasSize(5)
      .extracting("name").containsOnly("customers-group1", "customers-group2", "customers-group3", "SONAR-ADMINS", "sonar-users");

    // Empty query
    assertThat(underTest.selectByQuery(dbSession, "", 0, 10))
      .hasSize(5)
      .extracting("name").containsOnly("customers-group1", "customers-group2", "customers-group3", "SONAR-ADMINS", "sonar-users");

    // Filter on name
    assertThat(underTest.selectByQuery(dbSession, "sonar", 0, 10))
      .hasSize(2)
      .extracting("name").containsOnly("SONAR-ADMINS", "sonar-users");

    // Pagination
    assertThat(underTest.selectByQuery(dbSession, null, 0, 3))
      .hasSize(3);
    assertThat(underTest.selectByQuery(dbSession, null, 3, 3))
      .hasSize(2);
    assertThat(underTest.selectByQuery(dbSession, null, 6, 3)).isEmpty();
    assertThat(underTest.selectByQuery(dbSession, null, 0, 5))
      .hasSize(5);
    assertThat(underTest.selectByQuery(dbSession, null, 5, 5)).isEmpty();
  }

  @Test
  public void select_by_query_with_special_characters() {
    String groupNameWithSpecialCharacters = "group%_%/name";
    underTest.insert(dbSession, newGroupDto().setName(groupNameWithSpecialCharacters));
    db.commit();

    List<GroupDto> result = underTest.selectByQuery(dbSession, "roup%_%/nam", 0, 10);
    int resultCount = underTest.countByQuery(dbSession, "roup%_%/nam");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getName()).isEqualTo(groupNameWithSpecialCharacters);
    assertThat(resultCount).isEqualTo(1);
  }

  @Test
  public void countByQuery() {
    db.prepareDbUnit(getClass(), "select_by_query.xml");

    // Null query
    assertThat(underTest.countByQuery(dbSession, null)).isEqualTo(5);

    // Empty query
    assertThat(underTest.countByQuery(dbSession, "")).isEqualTo(5);

    // Filter on name
    assertThat(underTest.countByQuery(dbSession, "sonar")).isEqualTo(2);
  }

  @Test
  public void deleteById() {
    db.getDbClient().groupDao().insert(dbSession, aGroup);

    underTest.deleteById(dbSession, aGroup.getId());

    assertThat(db.countRowsOfTable(dbSession, "groups")).isEqualTo(0);
  }

}

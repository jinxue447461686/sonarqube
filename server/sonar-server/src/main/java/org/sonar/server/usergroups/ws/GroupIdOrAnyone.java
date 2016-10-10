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

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonar.db.user.GroupDto;

import static java.util.Objects.requireNonNull;

@Immutable
public class GroupIdOrAnyone {

  private final Long id;
  private final String organizationUuid;

  public GroupIdOrAnyone(String organizationUuid, @Nullable Long id) {
    this.id = id;
    this.organizationUuid = requireNonNull(organizationUuid);
  }

  public GroupIdOrAnyone(GroupDto group) {
    this.id = requireNonNull(group.getId());
    this.organizationUuid = requireNonNull(group.getOrganizationUuid());
  }

  public boolean isAnyone() {
    return id == null;
  }

  @CheckForNull
  public Long getId() {
    return id;
  }

  public String getOrganizationUuid() {
    return organizationUuid;
  }

  public static GroupIdOrAnyone from(GroupDto dto) {
    return new GroupIdOrAnyone(dto.getOrganizationUuid(), dto.getId());
  }

  public static GroupIdOrAnyone forAnyone(String organizationUuid) {
    return new GroupIdOrAnyone(organizationUuid, null);
  }
}

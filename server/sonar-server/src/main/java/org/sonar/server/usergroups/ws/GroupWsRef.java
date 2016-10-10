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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.security.DefaultGroups;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static org.sonar.server.ws.WsUtils.checkRequest;

@Immutable
public class GroupWsRef {

  private static final int NULL_ID = -1;

  private final long id;
  private final String organizationUuid;
  private final String name;

  private GroupWsRef(long id, @Nullable String organizationUuid, @Nullable String name) {
    this.id = id;
    this.organizationUuid = organizationUuid;
    this.name = name;
  }

  /**
   * @return {@code true} if id is defined and {@link #getId()} can be called. If {@code false}, then
   *   the couple {organizationUuid, name} is defined and the methods {@link #getOrganizationUuid()}/{@link #getName()}
   *   can be called.
   */
  public boolean hasId() {
    return id != NULL_ID;
  }

  /**
   * @return the group id
   * @throws IllegalStateException if {@link #getId()} is {@code false}
   */
  public long getId() {
    checkState(hasId(), "Id is not present. Please see hasId().");
    return id;
  }

  /**
   * @return the organization UUID. Always present, even if default organization.
   * @throws IllegalStateException if {@link #getId()} is {@code true}
   */
  public String getOrganizationUuid() {
    checkState(!hasId(), "Organization is not present. Please see hasId().");
    return organizationUuid;
  }

  /**
   * @return the non-null group name. Can be anyone.
   * @throws IllegalStateException if {@link #getId()} is {@code true}
   */
  public String getName() {
    checkState(!hasId(), "Name is not present. Please see hasId().");
    return name;
  }

  /**
   * @return a new {@link GroupWsRef} referencing a group by its id
   */
  public static GroupWsRef fromId(long id) {
    checkArgument(id > NULL_ID, "Group id must be positive: %s", id);
    return new GroupWsRef(id, null, null);
  }

  /**
   * @param organizationUuid non-null UUID of organization
   * @param name non-null name. Can be "anyone"
   */
  public static GroupWsRef fromName(String organizationUuid, String name) {
    return new GroupWsRef(NULL_ID, requireNonNull(organizationUuid), requireNonNull(name));
  }

  public boolean isAnyone() {
    return !hasId() && DefaultGroups.isAnyone(name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GroupWsRef groupRef = (GroupWsRef) o;
    if (id != groupRef.id) {
      return false;
    }
    if (organizationUuid != null ? !organizationUuid.equals(groupRef.organizationUuid) : groupRef.organizationUuid != null) {
      return false;
    }
    return name != null ? name.equals(groupRef.name) : (groupRef.name == null);
  }

  @Override
  public int hashCode() {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + (organizationUuid != null ? organizationUuid.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("GroupWsRef{");
    sb.append("id=").append(id);
    sb.append(", organizationUuid='").append(organizationUuid).append('\'');
    sb.append(", name='").append(name).append('\'');
    sb.append('}');
    return sb.toString();
  }
}

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
package org.sonar.server.permission;

import java.util.Set;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.permission.UserPermissionDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.permission.PermissionChange.Operation;
import org.sonar.server.user.UserSession;

import static org.sonar.server.permission.PermissionPrivilegeChecker.checkProjectAdminUserByComponentUuid;

public class UserPermissionChanger {

  private final DbClient dbClient;
  private final UserSession userSession;

  public UserPermissionChanger(DbClient dbClient, UserSession userSession) {
    this.dbClient = dbClient;
    this.userSession = userSession;
  }

  public boolean apply(DbSession dbSession, UserPermissionChange change) {
    checkProjectAdminUserByComponentUuid(userSession, change.getProjectUuid());

    if (shouldSkipChange(dbSession, change)) {
      return false;
    }

    switch (change.getOperation()) {
      case ADD:
        UserPermissionDto dto = new UserPermissionDto(change.getOrganizationUuid(), change.getPermission(), change.getUserId().getId(), change.getProjectId());
        dbClient.userPermissionDao().insert(dbSession, dto);
        break;
      case REMOVE:
        checkOtherAdminUsersExist(dbSession, change);
        dbClient.userPermissionDao().delete(dbSession, change.getUserId().getLogin(), change.getProjectUuid(), change.getPermission());
        break;
      default:
        throw new UnsupportedOperationException("Unsupported permission change: " + change.getOperation());
    }
    return true;
  }

  private boolean shouldSkipChange(DbSession dbSession, UserPermissionChange change) {
    Set<String> existingPermissions = dbClient.userPermissionDao().selectPermissionsByLogin(dbSession, change.getUserId().getLogin(), change.getProjectUuid());
    return (Operation.ADD == change.getOperation() && existingPermissions.contains(change.getPermission())) ||
      (Operation.REMOVE == change.getOperation() && !existingPermissions.contains(change.getPermission()));
  }

  private void checkOtherAdminUsersExist(DbSession session, PermissionChange change) {
    if (GlobalPermissions.SYSTEM_ADMIN.equals(change.getPermission()) &&
      !change.getProjectRef().isPresent() &&
      dbClient.roleDao().countUserPermissions(session, change.getPermission(), null) <= 1) {
      throw new BadRequestException(String.format("Last user with '%s' permission. Permission cannot be removed.", GlobalPermissions.SYSTEM_ADMIN));
    }
  }
}

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
package org.sonar.db.version.v62;

import java.sql.Date;
import java.sql.SQLException;
import org.sonar.api.utils.System2;
import org.sonar.db.Database;
import org.sonar.db.version.BaseDataChange;
import org.sonar.db.version.MassUpdate;
import org.sonar.db.version.Select;

public class PopulateOrganizationUuidOfGroups extends BaseDataChange {

  private static final String INTERNAL_PROPERTY_DEFAULT_ORGANIZATION = "organization.default";

  private final System2 system2;

  public PopulateOrganizationUuidOfGroups(Database db, System2 system2) {
    super(db);
    this.system2 = system2;
  }

  @Override
  public void execute(Context context) throws SQLException {
    String organizationUuid = selectDefaultOrganizationUuid(context);

    MassUpdate massUpdate = context.prepareMassUpdate();
    massUpdate.select("select id from groups where organization_uuid is null");
    massUpdate.update("update groups set organization_uuid=?, updated_at=? where id=?");
    massUpdate.rowPluralName("groups");
    massUpdate.execute((row, update) -> {
      long groupId = row.getLong(1);
      update.setString(1, organizationUuid);
      update.setDate(2, new Date(system2.now()));
      update.setLong(3, groupId);
      return true;
    });
  }

  private String selectDefaultOrganizationUuid(Context context) throws SQLException {
    Select select = context.prepareSelect("select text_value from internal_properties where kee=?");
    select.setString(1, INTERNAL_PROPERTY_DEFAULT_ORGANIZATION);
    return select.get(row -> row.getString(1));
  }
}

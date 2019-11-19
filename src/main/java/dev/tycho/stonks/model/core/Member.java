package dev.tycho.stonks.model.core;

import dev.tycho.stonks.managers.DatabaseManager;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Date;
import java.util.UUID;

@AllArgsConstructor
public class Member {

  @Getter
  private UUID uuid;
  @Getter
  private Company company;
  @Getter
  private Date joinDate;
  @Getter
  private Role role;
  @Getter
  private boolean acceptedInvite;

  public boolean canChangeRole(Member other, Role newRole) {
    //We are the same role or superior to them
    //So we can change their role
    if (role.compareTo(other.getRole()) <= 0) {
      //We cannot promote them higher than us
      return role.compareTo(newRole) <= 0;
    }
    return false;
  }
  public Boolean hasManagementPermission() {
    return this.role.equals(Role.CEO) || this.role.equals(Role.Manager);
  }

  public Boolean hasHoldings(DatabaseManager databaseManager) {
    return databaseManager.getHoldingDao().memberHasHoldings(this);
  }
}

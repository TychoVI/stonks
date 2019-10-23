package dev.tycho.stonks.model.core;

import com.j256.ormlite.dao.ForeignCollection;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.field.ForeignCollectionField;
import com.j256.ormlite.table.DatabaseTable;
import dev.tycho.stonks.database.CompanyDaoImpl;
import dev.tycho.stonks.managers.DatabaseManager;
import dev.tycho.stonks.model.service.Service;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

@AllArgsConstructor
public class Company {

  @Getter
  private UUID id;
  @Getter
  private String name;
  private String shopName;
  @Getter
  private String logoMaterial;
  @Getter
  private boolean verified;
  @Getter
  private boolean hidden;

  public Member getMember(Player player) {
    for (Member member : members) {
      if (member.getUuid().equals(player.getUniqueId())) {
        return member;
      }
    }
    return null;
  }

  public int getNumAcceptedMembers() {
    int m = 0;
    for (Member member : members) {
      if (member.getAcceptedInvite()) m++;
    }
    return m;
  }

  public double getTotalValue() {
    double totalValue = 0;
    for (AccountLink accountLink : accounts) {
      totalValue += accountLink.getAccount().getTotalBalance();
    }
    return totalValue;
  }

  public void createCompanyAccount(DatabaseManager databaseManager, String name) throws SQLException {
    CompanyAccount companyAccount = new CompanyAccount(name);
    databaseManager.getCompanyAccountDao().create(companyAccount);
    //Create an link entry so the account is registered as ours
    databaseManager.getAccountLinkDao().create(new AccountLink(this, companyAccount));
  }


  public Boolean hasMember(Player player) {
    for (Member member : members) {
      if (member.getUuid().equals(player.getUniqueId())) {
        return true;
      }
    }
    return false;
  }
}

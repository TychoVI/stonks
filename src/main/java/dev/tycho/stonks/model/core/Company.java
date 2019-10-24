package dev.tycho.stonks.model.core;

import dev.tycho.stonks.managers.DatabaseManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

@AllArgsConstructor
public class Company {

  private DatabaseManager databaseManager;
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
    try {
      return databaseManager.getMemberManager().getMember(player, this);
    } catch (SQLException e) {
      return null;
    }
  }

  public int getNumAcceptedMembers() {
    int m = 0;
    for (Member member : databaseManager.getMemberManager().getCompanyMembers(this)) {
      if (member.isAcceptedInvite()) {
        m++;
      }
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


  public boolean hasMember(Player player) {
    return getMember(player) != null;
  }
}

package dev.tycho.stonks.database;

import dev.tycho.stonks.managers.DatabaseManager;
import dev.tycho.stonks.model.core.Company;
import dev.tycho.stonks.model.core.Member;
import dev.tycho.stonks.model.core.Role;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemberManager {

  private Connection connection;
  private CompanyManager companyManager;

  public MemberManager(DatabaseManager databaseManager, CompanyManager companyManager) {
    this.connection = databaseManager.getConnection();
    this.companyManager = companyManager;
  }

  public List<Member> getInvites(Player player) {
    List<Member> inviteMembers = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement("SELECT * FROM `member` WHERE uuid = ? AND acceptedInvite = 0;");
      statement.setString(1, player.getUniqueId().toString());
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        inviteMembers.add(new Member(
            UUID.fromString(set.getString(set.getString("uuid"))),
            companyManager.getCompanyFromId(UUID.fromString(set.getString("company_id"))),
            set.getDate("joinDate"),
            Role.valueOf(set.getString("role")),
            set.getInt("acceptedInvite") == 1));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return inviteMembers;
  }

  public void handleInvite(boolean accepted, UUID companyUuid, UUID playerUuid) throws SQLException {
    PreparedStatement statement = connection.prepareStatement((accepted ? "UPDATE `member` SET acceptedInvite = 1" : "DELETE FROM `member`") + " WHERE uuid = ? AND acceptedInvite = 0 AND company_id = ?;");
    statement.setString(1, playerUuid.toString());
    statement.setString(2, companyUuid.toString());
    statement.executeUpdate();
  }

  public Member getMember(Player player, Company company) throws SQLException {
    PreparedStatement statement = connection.prepareStatement("SELECT * FROM `member` WHERE uuid = ? AND company_id = ?;");
    statement.setString(1, player.getUniqueId().toString());
    statement.setString(2, company.getId().toString());
    ResultSet set = statement.executeQuery();
    set.next();
    return new Member(
        UUID.fromString(set.getString("uuid")),
        company,
        set.getDate("joinDate"),
        Role.valueOf(set.getString("role")),
        set.getInt("acceptedInvite") == 1);
  }

  public void deleteMember(Member member) throws SQLException {
    PreparedStatement statement = connection.prepareStatement("DELETE FROM `member` WHERE uuid = ? AND company_id = ?;");
    statement.setString(1, member.getUuid().toString());
    statement.setString(2, member.getCompany().getId().toString());
    statement.executeUpdate();
  }

  public void setRole(Member member, Role role) throws SQLException {
    PreparedStatement statement = connection.prepareStatement("UPDATE `member` SET `role` = ? WHERE uuid = ? AND company_id = ?;");
    statement.setString(1, role.name());
    statement.setString(2, member.getUuid().toString());
    statement.setString(3, member.getCompany().getId().toString());
    statement.executeUpdate();
  }

  public List<Member> getCompanyMembers(Company company) {
    List<Member> members = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement("SELECT * FROM `member` WHERE company_id = ?;");
      statement.setString(1, company.getId().toString());
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        members.add(new Member(UUID.fromString("uuid"),
            company,
            set.getDate("joinDate"),
            Role.valueOf(set.getString("role")),
            set.getInt("acceptedInvite") == 1));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return members;
  }
}

package dev.tycho.stonks.database;

import com.Acrobot.ChestShop.ORMlite.stmt.QueryBuilder;
import dev.tycho.stonks.managers.DatabaseManager;
import dev.tycho.stonks.model.core.Company;
import dev.tycho.stonks.model.core.Member;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompanyManager {

  private final DatabaseManager databaseManager;
  private final Connection connection;

  public CompanyManager(DatabaseManager databaseManager) {
    this.databaseManager = databaseManager;
    this.connection = databaseManager.getConnection();
  }

  public boolean companyExists(String name) {
    try {
      PreparedStatement statement = connection.prepareStatement("SELECT id FROM `company` WHERE name = ?;");
      statement.setString(1, name);
      return statement.executeQuery().next();
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
  }


  public Company getCompany(String name) throws SQLException {
    PreparedStatement statement = connection.prepareStatement("SELECT * FROM `company` WHERE name = ?;");
    statement.setString(1, name);
    ResultSet set = statement.executeQuery();
    set.next();
    return new Company(databaseManager,
        UUID.fromString(set.getString("id")),
        set.getString("name"),
        set.getString("shopName"),
        set.getString("logoMaterial"),
        set.getInt("verified") == 1,
        set.getInt("hidden") == 1);
  }

  public List<Company> getAllCompanies() {
    List<Company> companies = new ArrayList<>();
    try {
      ResultSet set = connection.createStatement().executeQuery("SELECT * FROM `company` ORDER BY name;");
      while (set.next()) {
        companies.add(new Company(databaseManager,
            UUID.fromString(set.getString("id")),
            set.getString("name"),
            set.getString("shopName"),
            set.getString("logoMaterial"),
            set.getInt("verified") == 1,
            set.getInt("hidden") == 1));
      }
      return companies;
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return companies;
  }

  public List<Company> getAllCompaniesWhereManager(Player player, QueryBuilder<Member, UUID> memberQuery) {
    List<Company> companies = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement("SELECT * FROM `member` WHERE uuid = ? AND (role = 'CEO' OR role = 'Manager');");
      statement.setString(1, player.getUniqueId().toString());
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        companies.add(getCompanyFromId(UUID.fromString(set.getString("company_id"))));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return companies;
  }

  public Company getCompanyFromId(UUID uuid) throws SQLException {
    PreparedStatement statement = connection.prepareStatement("SELECT * FROM `company` WHERE uuid = ?;");
    statement.setString(1, uuid.toString());
    ResultSet set = statement.executeQuery();
    set.next();
    return new Company(databaseManager,
        UUID.fromString(set.getString("id")),
        set.getString("name"),
        set.getString("shopName"),
        set.getString("logoMaterial"),
        set.getInt("verified") == 1,
        set.getInt("hidden") == 1);
  }
}

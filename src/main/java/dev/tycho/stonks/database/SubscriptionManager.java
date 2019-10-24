package dev.tycho.stonks.database;

import dev.tycho.stonks.managers.DatabaseManager;
import dev.tycho.stonks.model.service.Subscription;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SubscriptionManager {

  private Connection connection;

  public SubscriptionManager(DatabaseManager databaseManager) {
    this.connection = databaseManager.getConnection();
  }

  public List<Subscription> getPlayerSubscriptions(Player player) {
    List<Subscription> subscriptions = new ArrayList<>();
    try {
      PreparedStatement statement = connection.prepareStatement("SELECT * FROM `subscription` WHERE playerId = ?;");
      statement.setString(1, player.getUniqueId().toString());
      ResultSet set = statement.executeQuery();
      while (set.next()) {
        subscriptions.add(new Subscription(
            set.getInt("id"),
            set.getInt("service_id"),
            UUID.fromString(set.getString("playerId")),
            set.getDate("lastPaymentDate")));
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return subscriptions;
  }
}

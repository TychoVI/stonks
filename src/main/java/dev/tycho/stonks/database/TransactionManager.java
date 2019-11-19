package dev.tycho.stonks.database;

import dev.tycho.stonks.managers.DatabaseManager;

import java.sql.Connection;

public class TransactionManager {

  private Connection connection;

  public TransactionManager(DatabaseManager databaseManager) {
    this.connection = databaseManager.getConnection();
  }
}

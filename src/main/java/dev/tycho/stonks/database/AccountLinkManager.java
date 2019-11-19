package dev.tycho.stonks.database;

import dev.tycho.stonks.managers.DatabaseManager;
import dev.tycho.stonks.model.accountvisitors.ReturningAccountVisitor;
import dev.tycho.stonks.model.core.*;

import java.sql.Connection;
import java.sql.SQLException;

public class AccountLinkManager {

  private Connection connection;

  public AccountLinkManager(DatabaseManager databaseManager) {
    this.connection = databaseManager.getConnection();
  }

  public Company getCompany(Account account) {
    QueryBuilder<AccountLink, Integer> queryBuilder = queryBuilder();
    try {
      queryBuilder.where().eq("account_id", account.getId());
      AccountLink ao = queryBuilder.queryForFirst();
      if (ao != null) {
        return ao.getCompany();
      } else {
        return null;
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

  public AccountLink getAccountLink(Account account) {
    QueryBuilder<AccountLink, Integer> queryBuilder = queryBuilder();
    try {
      ReturningAccountVisitor<String> v = new ReturningAccountVisitor<String>() {
        @Override
        public void visit(CompanyAccount a) {
          val = "companyAccount_id";
        }

        @Override
        public void visit(HoldingsAccount a) {
          val = "holdingsAccount_id";
        }
      };
      account.accept(v);
      queryBuilder.where().eq(v.getRecentVal(), account.getId());
      return queryBuilder.queryForFirst();
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }
}

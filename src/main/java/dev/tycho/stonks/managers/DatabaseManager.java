package dev.tycho.stonks.managers;

import dev.tycho.stonks.Stonks;
import dev.tycho.stonks.command.MainCommand;
import dev.tycho.stonks.database.*;
import dev.tycho.stonks.model.accountvisitors.IAccountVisitor;
import dev.tycho.stonks.model.core.*;
import dev.tycho.stonks.model.logging.Transaction;
import dev.tycho.stonks.model.service.Service;
import dev.tycho.stonks.model.service.Subscription;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager extends SpigotModule {

  @Getter
  private Connection connection;
  @Getter
  private CompanyManager companyManager;
  @Getter
  private MemberManager memberManager;
  @Getter
  private SubscriptionManager subscriptionManager;

  public DatabaseManager(Stonks plugin) {
    super("databaseManager", plugin);
  }

  @Override
  public void enable() {
    try {
      synchronized (this) {
        if (connection != null && !connection.isClosed()) {
          return;
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection(
            "jdbc:mysql://" + plugin.getConfig().getString("mysql.host") + ":" + plugin.getConfig().getString("mysql.port")
                + "/" + plugin.getConfig().getString("mysql.database")
                + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT",
            plugin.getConfig().getString("mysql.username"),
            plugin.getConfig().getString("mysql.password"));
        log("Connected to SQL!");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `accountlink` (`id` int(11) NOT NULL AUTO_INCREMENT, `company_id` varchar(48) DEFAULT NULL, `companyAccount_id` int(11) DEFAULT NULL, `holdingsAccount_id` int(11) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=171 DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `company` (`id` varchar(48) NOT NULL, `name` varchar(255) DEFAULT NULL, `shopName` varchar(255) DEFAULT NULL, `logoMaterial` varchar(255) DEFAULT NULL, `verified` tinyint(1) NOT NULL DEFAULT '0', `hidden` tinyint(1) NOT NULL DEFAULT '0', PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `companyaccount` (`balance` double DEFAULT NULL, `id` int(11) NOT NULL AUTO_INCREMENT, `uuid` varchar(48) DEFAULT NULL, `name` varchar(255) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `holding` (`id` int(11) NOT NULL AUTO_INCREMENT, `player` varchar(48) DEFAULT NULL, `balance` double DEFAULT NULL, `share` double DEFAULT NULL, `holdingsAccount_id` int(11) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=80 DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `holdingsaccount` (`id` int(11) NOT NULL AUTO_INCREMENT, `uuid` varchar(48) DEFAULT NULL, `name` varchar(255) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `member` (`uuid` varchar(48) DEFAULT NULL, `company_id` varchar(48) DEFAULT NULL, `joinDate` datetime DEFAULT NULL, `role` varchar(100) DEFAULT NULL, `acceptedInvite` tinyint(1) DEFAULT NULL, UNIQUE KEY `uuid` (`uuid`,`company_id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `service` (`id` int(11) NOT NULL AUTO_INCREMENT, `name` varchar(255) DEFAULT NULL, `duration` double DEFAULT NULL, `cost` double DEFAULT NULL, `maxSubscribers` int(11) DEFAULT NULL, `company_id` varchar(48) DEFAULT NULL, `account_id` int(11) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `subscription` (`id` int(11) NOT NULL AUTO_INCREMENT, `service_id` int(11) DEFAULT NULL, `playerId` varchar(48) DEFAULT NULL, `lastPaymentDate` datetime DEFAULT NULL, `autoPay` tinyint(1) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4;");
        connection.createStatement().executeUpdate("CREATE TABLE IF NOT EXISTS `transaction` (`id` int(11) NOT NULL AUTO_INCREMENT, `account_id` int(11) DEFAULT NULL, `payee` varchar(48) DEFAULT NULL, `amount` double DEFAULT NULL, `timestamp` datetime DEFAULT NULL, `message` varchar(255) DEFAULT NULL, PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=11402 DEFAULT CHARSET=utf8mb4;");
        log("Initialized Tables!");
        companyManager = new CompanyManager(this);
        memberManager = new MemberManager(this, companyManager);
        subscriptionManager = new SubscriptionManager(this);
      }
    } catch (SQLException | ClassNotFoundException e) {
      e.printStackTrace();
      log("Error connecting to the SQL Database: " + e.getMessage());
      return;
    }

    new DatabaseHelper(plugin, this);
  }

  @Override
  public void addCommands() {
    MainCommand command = new MainCommand();
    addCommand("company", command);
    //noinspection ConstantConditions
    plugin.getCommand("company").setTabCompleter(command);
  }

  @Override
  public void disable() {
    try {
      connection.close();
    } catch (SQLException ignored) {
    }
  }

  public AccountLinkDaoImpl getAccountLinkDao() {
    return accountlinkDao;
  }

  public CompanyAccountDao getCompanyAccountDao() {
    return companyAccountDao;
  }

  Dao<HoldingsAccount, Integer> getHoldingAccountDao() {
    return holdingAccountDao;
  }

  public HoldingDao getHoldingDao() {
    return holdingDao;
  }

  public TransactionDaoImpl getTransactionDao() {
    return transactionDao;
  }

  //TODO move this method to a better place
  Account getAccountWithUUID(UUID uuid) {
    try {
      //Try company account first as those are the most common
      QueryBuilder<CompanyAccount, Integer> queryBuilder = getCompanyAccountDao().queryBuilder();
      queryBuilder.where().eq("uuid", uuid);
      CompanyAccount companyAccount = queryBuilder.queryForFirst();
      //If no company account was found try and find a holdings account
      if (companyAccount == null) {
        QueryBuilder<HoldingsAccount, Integer> queryBuilder2 = getHoldingAccountDao().queryBuilder();
        queryBuilder2.where().eq("uuid", uuid);
        //This will either return a result or null
        //If it is null there are no accounts with this match
        return queryBuilder2.queryForFirst();
      } else {
        return companyAccount;
      }

    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

  //TODO move this too
  public void logTransaction(Transaction transaction) {
    try {
      getTransactionDao().create(transaction);
    } catch (SQLException e) {
      e.printStackTrace();
      Bukkit.broadcastMessage(ChatColor.RED + "SQL Exception creating a log ");
    }
  }

  public void refreshAccount(Account account) {
    //Refresh the account because we have to recurse quite deeply
    IAccountVisitor visitor = new IAccountVisitor() {
      @Override
      public void visit(CompanyAccount a) {
        try {
          DatabaseHelper.getInstance().getDatabaseManager().getCompanyAccountDao().refresh(a);
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void visit(HoldingsAccount a) {
        try {
          DatabaseHelper.getInstance().getDatabaseManager().getHoldingsAccountDao().refresh(a);
          for (Holding h : a.getHoldings()) getHoldingDao().refresh(h);
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }

    };
    account.accept(visitor);
  }

  public void updateAccount(Account account) {
    //Update the account database
    IAccountVisitor visitor = new IAccountVisitor() {
      @Override
      public void visit(CompanyAccount a) {
        try {
          getCompanyAccountDao().update(a);
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }

      @Override
      public void visit(HoldingsAccount a) {
        try {
          //Update the account and holdings
          getHoldingAccountDao().update(a);
          if (a.getHoldings() != null)
            for (Holding h : a.getHoldings()) if (h != null) getHoldingDao().update(h);
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    };
    account.accept(visitor);
  }

  public Dao<Service, Integer> getServiceDao() {
    return serviceDao;
  }

  public HoldingsAccountDaoImpl getHoldingsAccountDao() {
    return holdingAccountDao;
  }

  public SubscriptionDaoImpl getSubscriptionDao() {
    return subscriptionDao;
  }
}

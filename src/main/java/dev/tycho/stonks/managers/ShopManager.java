package dev.tycho.stonks.managers;

import com.Acrobot.ChestShop.Database.Account;
import com.Acrobot.ChestShop.Events.*;
import com.Acrobot.ChestShop.Events.Economy.AccountCheckEvent;
import com.Acrobot.ChestShop.UUIDs.NameManager;
import com.j256.ormlite.stmt.QueryBuilder;
import dev.tycho.stonks.Stonks;
import dev.tycho.stonks.model.accountvisitors.IAccountVisitor;
import dev.tycho.stonks.model.core.*;
import dev.tycho.stonks.model.logging.Transaction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static com.Acrobot.ChestShop.Signs.ChestShopSign.NAME_LINE;
import static com.Acrobot.ChestShop.Signs.ChestShopSign.PRICE_LINE;


public class ShopManager extends SpigotModule {

  private DatabaseManager databaseManager;

  public ShopManager(Stonks plugin) {
    super("Shop Manager", plugin);
    databaseManager = (DatabaseManager) plugin.getModule("databaseManager");
  }

  @EventHandler
  public void onPreShopCreation(PreShopCreationEvent event) {
    String accountLine = event.getSignLine(NAME_LINE);
    String priceLine = event.getSignLine(PRICE_LINE);
    if (!accountLine.startsWith("#")) {
      return;
    }

    int accountId = 0;
    if (accountLine.substring(1).matches("\\d+")) {
      accountId = Integer.parseInt(accountLine.substring(1));
    } else if (accountLine.contains("-")) {
      int indexOfDash = accountLine.indexOf("-");
      if (accountLine.substring(1, indexOfDash).matches("\\d+")) {
        accountId = Integer.parseInt(accountLine.substring(1, indexOfDash));
      }
    } else {
      return;
    }

    try {
      //If an account exists for this account id
      if (databaseManager.getAccountLinkDao().idExists(accountId)) {
        AccountLink link = databaseManager.getAccountLinkDao().queryForId(accountId);
        UUID companyUuid = link.getCompany().getId();

        //If the sign is a sell sign don't allow holdings accounts
        //todo turn this into a visitor and remove the need for getAccountType
        if (priceLine.toLowerCase().contains("s") && link.getAccount() instanceof HoldingsAccount) {
          event.setOutcome(PreShopCreationEvent.CreationOutcome.INVALID_PRICE);
          event.getPlayer().sendMessage(ChatColor.RED + "You cannot create a sell sign for a holdings account"
              + "\n" + link.getAccount().getName() + " (id " + link.getId() + ") is a holdings account");
          return;
        }

        QueryBuilder<Member, UUID> queryBuilder = databaseManager.getMemberDao().queryBuilder();
        queryBuilder.where().eq("uuid", event.getPlayer().getUniqueId()).and().eq("company_id", companyUuid);
        Member membership = queryBuilder.queryForFirst();
        if (membership == null || membership.getRole().equals(Role.Intern) || !membership.getAcceptedInvite()) {
          event.setOutcome(PreShopCreationEvent.CreationOutcome.NO_PERMISSION);
        }

      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @EventHandler
  public void onAccountQuery(AccountQueryEvent event) {
    String accountLine = event.getName();
    if (!accountLine.startsWith("#")) {
      return;
    }
    int accountId;
    if (accountLine.substring(1).matches("\\d+")) {
      accountId = Integer.parseInt(accountLine.substring(1));
    } else if (accountLine.contains("-")) {
      int indexOfDash = accountLine.indexOf("-");
      accountId = Integer.parseInt(accountLine.substring(1, indexOfDash));
    } else {
      return;
    }

    try {
      //If an account exists with this id
      if (databaseManager.getAccountLinkDao().idExists(accountId)) {
        AccountLink link = databaseManager.getAccountLinkDao().queryForId(accountId);
        UUID accountUuid = link.getAccount().getUuid();
        //Get the chest shop account associated with this uuid
        Account CSaccount = NameManager.getAccount(accountUuid);

        if (CSaccount == null) {
          //If none exists then make a new one
          String name = link.getCompany().getName();
          Account newCSaccount = new Account("#" + accountId + "-" + name, accountUuid);
          event.setAccount(newCSaccount);
        } else {
          event.setAccount(CSaccount);
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @EventHandler
  public void onAccountCheck(AccountCheckEvent event) {

  }

  @EventHandler
  public void onOwnEvent(AccountOwnerCheckEvent event) {
    if (!event.getName().startsWith("#")) {
      return;
    }
    int dashIndex = event.getName().indexOf("-");
    if (dashIndex == -1) {
      event.setCancelled(true);
      return;
    }
    int accountId = Integer.parseInt(event.getName().substring(1, dashIndex));
    try {
      //Find the account for this id
      AccountLink link = databaseManager.getAccountLinkDao().queryForId(accountId);
      QueryBuilder<Member, UUID> queryBuilder = databaseManager.getMemberDao().queryBuilder();
      queryBuilder.where().eq("uuid", event.getPlayer().getUniqueId()).and().eq("company_id", link.getCompany().getId());
      List<Member> list = queryBuilder.query();
      if (list.isEmpty() || list.get(0).getRole().equals(Role.Intern) || !list.get(0).getAcceptedInvite()) {
        return;
      }
      event.setCancelled(true);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @EventHandler
  public void onEconomyCheck(AccountCheckEvent event) {
    //Try to see if we have an account for this UUID
    dev.tycho.stonks.model.core.Account account = databaseManager.getAccountWithUUID(event.getAccount());
    if (account != null) {
      event.hasAccount(true);
    }
  }

  @EventHandler
  public void onCurrencyAdd(PreCurrencyAddEvent event) {
    dev.tycho.stonks.model.core.Account account = databaseManager.getAccountWithUUID(event.getTarget());
    if (account != null) {
      event.setCancelled(true);

      Stonks.newChain()
          .async(() -> {
            account.addBalance(event.getAmountSent().doubleValue());
            //Create a visitor to use the correct DAO to update the account
            IAccountVisitor visitor = new IAccountVisitor() {
              @Override
              public void visit(CompanyAccount a) {
                try {
                  databaseManager.getCompanyAccountDao().update(a);
                } catch (SQLException e) {
                  e.printStackTrace();
                }
              }

              @Override
              public void visit(HoldingsAccount a) {
                try {
                  databaseManager.getHoldingAccountDao().update(a);
                  for (Holding h : a.getHoldings()) {
                    databaseManager.getHoldingDao().update(h);
                  }
                } catch (SQLException e) {
                  e.printStackTrace();
                }
              }
            };
            account.accept(visitor);
            //Log the transaction
            databaseManager.logTransaction(new Transaction(
                databaseManager.getAccountLinkDao().getAccountLink(account),
                null,
                "Shop Buy",
                event.getAmountSent().doubleValue()));
          }).execute();
    }
  }

  @EventHandler
  public void onCurrencySubtract(PreCurrencySubtractEvent event) {
    dev.tycho.stonks.model.core.Account account = databaseManager.getAccountWithUUID(event.getSender());
    if (account != null) {
      event.setCancelled(true);
      Stonks.newChain()
          .async(() -> {
            IAccountVisitor visitor = new IAccountVisitor() {
              @Override
              public void visit(CompanyAccount a) {
                if (!a.subtractBalance(event.getAmountSent().doubleValue())) {
                  event.setBalanceSufficient(false);
                } else {
                  //Transaction success
                  //Log the transaction
                  databaseManager.logTransaction(new Transaction(
                      databaseManager.getAccountLinkDao().getAccountLink(account),
                      null,
                      "Shop Sell",
                      -event.getAmountSent().doubleValue()));
                }
                try {
                  databaseManager.getCompanyAccountDao().update(a);
                } catch (SQLException e) {
                  e.printStackTrace();
                }
              }

              @Override
              public void visit(HoldingsAccount a) {
                //A holdings account can't pay out
                System.out.println("Tried to pay out of a holdings account using a shop sign");
                System.out.println("Holdings account ID: " + a.getId());
                event.setBalanceSufficient(false);
              }
            };
            account.accept(visitor);
          }).execute();
    }
  }

  @EventHandler
  public void onAmountCheck(PreAmountCheckEvent event) {
    //If they try to check an account balance for one of our accounts cancel it
    dev.tycho.stonks.model.core.Account account = databaseManager.getAccountWithUUID(event.getAccount());
    if (account != null) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onCurrencyCheck(PreCurrencyCheckEvent event) {
    dev.tycho.stonks.model.core.Account account = databaseManager.getAccountWithUUID(event.getAccount());
    if (account != null) {
      if (account.getTotalBalance() >= event.getAmountSent().doubleValue()) {
        event.setHasEnough(true);
      }
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onOfflinePlayerBuyEvent(OfflinePlayerBuyEvent event) {
    if (!event.getTransactionEvent().getOwnerAccount().getName().startsWith("#")) {
      event.setCancelled(true);
      return;
    }
    try {
      for (Member member : databaseManager.getAccountLinkDao().queryForId(Integer.valueOf(event.getTransactionEvent().getOwnerAccount().getName().split("-")[0].replaceFirst("#", ""))).getCompany().getMembers()) {
        if (member.hasManagementPermission()) {
          Player player = DatabaseHelper.getInstance().getEssentials().getUser(member.getUuid()).getBase();
          if (player.isOnline()) {
            event.addTarget(player);
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  @EventHandler
  public void onOfflinePlayerSellEvent(OfflinePlayerSellEvent event) {
    if (!event.getTransactionEvent().getOwnerAccount().getName().startsWith("#")) {
      event.setCancelled(true);
      return;
    }
    try {
      for (Member member : databaseManager.getAccountLinkDao().queryForId(Integer.valueOf(event.getTransactionEvent().getOwnerAccount().getName().split("-")[0].replaceFirst("#", ""))).getCompany().getMembers()) {
        if (member.hasManagementPermission()) {
          OfflinePlayer player = Bukkit.getOfflinePlayer(member.getUuid());
          if (player.isOnline()) {
            event.addTarget(player.getPlayer());
          }
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
}

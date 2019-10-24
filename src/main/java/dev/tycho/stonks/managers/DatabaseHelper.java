package dev.tycho.stonks.managers;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import com.j256.ormlite.stmt.QueryBuilder;
import dev.tycho.stonks.Stonks;
import dev.tycho.stonks.command.subs.ListCommandSub;
import dev.tycho.stonks.gui.*;
import dev.tycho.stonks.model.accountvisitors.IAccountVisitor;
import dev.tycho.stonks.model.core.*;
import dev.tycho.stonks.model.logging.Transaction;
import dev.tycho.stonks.model.service.Service;
import dev.tycho.stonks.model.service.Subscription;
import dev.tycho.stonks.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static dev.tycho.stonks.model.core.Role.*;

public class DatabaseHelper extends SpigotModule {

  private static DatabaseHelper instance;
  private final DatabaseManager databaseManager;

  private final Essentials essentials;

  private final double COMPANY_FEE;
  private final double ACCOUNT_FEE;
  private final long COMPANY_CREATION_COOLDOWN;
  private final long ACCOUNT_CREATION_COOLDOWN;

  private HashMap<UUID, Long> playerCompanyCooldown = new HashMap<>();
  private HashMap<UUID, Long> playerAccountCooldown = new HashMap<>();

  public DatabaseHelper(Stonks plugin, DatabaseManager databaseManager) {
    super("Database Helper", plugin);
    instance = this;
    this.databaseManager = databaseManager;
    essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
    COMPANY_FEE = plugin.getConfig().getDouble("fees.companycreation");
    ACCOUNT_FEE = plugin.getConfig().getDouble("fees.companyaccountcreation");
    COMPANY_CREATION_COOLDOWN = plugin.getConfig().getLong("cooldowns.companycreation");
    ACCOUNT_CREATION_COOLDOWN = plugin.getConfig().getLong("cooldowns.accountcreation");
  }

  public static DatabaseHelper getInstance() {
    return instance;
  }

  public void createCompany(Player player, String companyName) {
    //Prevent the player from spamming companies
    if (!player.isOp() && playerCompanyCooldown.containsKey(player.getUniqueId()) && (System.currentTimeMillis() - playerCompanyCooldown.get(player.getUniqueId())) < COMPANY_CREATION_COOLDOWN) {
      sendMessage(player, "You cannot make a company for another " + Util.convertString(COMPANY_CREATION_COOLDOWN - (System.currentTimeMillis() - playerCompanyCooldown.get(player.getUniqueId()))));
      return;
    }
    Stonks.newChain()
        .async(() -> {
          if (companyName.length() > 32) {
            sendMessage(player, "A company name cannot be longer than 32 characters!");
            return;
          }
          try {
            if (databaseManager.getCompanyDao().companyExists(companyName)) {
              sendMessage(player, "A company with that name already exists!");
              return;
            }
            double creationFee = plugin.getConfig().getDouble("fees.companycreation");
            if (!Stonks.economy.withdrawPlayer(player, creationFee).transactionSuccess()) {
              sendMessage(player, "You don't have the sufficient funds for the $" + COMPANY_FEE + " company creation fee.");
              return;
            }
            Company newCompany = new Company(companyName, "S" + companyName, player);
            databaseManager.getCompanyDao().assignEmptyForeignCollection(newCompany, "members");
            databaseManager.getCompanyDao().create(newCompany);

            CompanyAccount companyAccount = new CompanyAccount("Main");
            databaseManager.getCompanyAccountDao().create(companyAccount);

            //Create an link so the account is stored as belonging to the new company
            AccountLink link = new AccountLink(newCompany, companyAccount);
            databaseManager.getAccountLinkDao().create(link);

            Member creator = new Member(player, CEO);
            newCompany.getMembers().add(creator);

            sendMessage(player, ChatColor.GREEN + "Company with name: \"" + companyName + "\" created successfully!");
            playerCompanyCooldown.put(player.getUniqueId(), System.currentTimeMillis());
            Bukkit.broadcastMessage(ChatColor.GREEN + player.getDisplayName() + ChatColor.GREEN + " just founded a new company - " + newCompany.getName() + "!");

          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, ChatColor.RED + "Something went wrong! :(");
            sendMessage(player, ChatColor.RED + "Try changing the name you entered (make it shorter, don't use special characters)");
          }
        }).execute();
  }


  //api
  public Company getCompanyByName(String name) {
    try {
      return databaseManager.getCompanyDao().getCompany(name);
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }


  public void openInvitesList(Player player) {
    Stonks.newChain()
        .asyncFirst(() -> {
          List<Member> invites;
          invites = databaseManager.getMemberDao().getInvites(player);
          if (invites == null) {
            sendMessage(player, "You don't have any pending invites!");
            return null;
          }
          return new InviteListGui(player);
        })
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }

  public void openCompanyList(Player player, ListCommandSub.CompanyListOptions options) {
    Stonks.newChain()
        .asyncFirst(() -> {
          List<Company> list = null;
          try {
            QueryBuilder<Company, UUID> companyQueryBuilder = databaseManager.getCompanyDao().queryBuilder();
            companyQueryBuilder.orderBy("name", true);
            list = companyQueryBuilder.query();

            if (options == ListCommandSub.CompanyListOptions.VERIFIED) {
              list.removeIf(c->!c.isVerified());
            } else if (options == ListCommandSub.CompanyListOptions.MEMBER_OF) {
              list.removeIf(c->c.getMember(player) == null || !c.getMember(player).getAcceptedInvite());
            } else if (options == ListCommandSub.CompanyListOptions.NOT_HIDDEN_OR_MEMBER) {
              list.removeIf(c->(c.getMember(player) == null || !c.getMember(player).getAcceptedInvite()) && c.isHidden());
            }
          } catch (SQLException e) {
            e.printStackTrace();
          }
          return new CompanyListGui(list);
        })
        .sync(gui -> gui.show(player))
        .execute();
  }

  public void openCompanyInfo(Player player, String companyName) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            Company company = databaseManager.getCompanyDao().getCompany(companyName);
            if (company == null) {
              sendMessage(player, "That company doesn't exist!");
              return null;
            }
            return CompanyInfoGui.getInventory(company);
          } catch (SQLException e) {
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync((result) -> result.open(player))
        .execute();
  }

  public void openCompanyMembers(Player player, String companyName) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            Company company = databaseManager.getCompanyDao().getCompany(companyName);
            if (company == null) {
              sendMessage(player, "That company doesn't exist!");
              return null;
            }
            List<Member> list = null;
            try {
              QueryBuilder<Member, UUID> queryBuilder = databaseManager.getMemberDao().queryBuilder();
              queryBuilder.where().eq("company_id", company.getId()).and().eq("acceptedInvite", true);
              list = queryBuilder.query();
            } catch (SQLException e) {
              e.printStackTrace();
            }
            return new MemberListGui(company, list);
          } catch (SQLException e) {
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }

  public void openCompanyAccounts(Player player, String companyName) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            Company company = databaseManager.getCompanyDao().getCompany(companyName);
            if (company == null) {
              sendMessage(player, "That company doesn't exist!");
              return null;
            }
            return new AccountListGui(company);
          } catch (SQLException e) {
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }

  public void invitePlayerToCompany(Player player, String companyName, String playerToInvite) {
    Stonks.newChain()
        .async(() -> {
          try {
            User u = essentials.getOfflineUser(playerToInvite);
            if (u == null) {
              sendMessage(player, "This player has never played on the server before!");
              return;
            }
            Player playerToInviteObject = u.getBase();

            QueryBuilder<Company, UUID> queryBuilder = databaseManager.getCompanyDao().queryBuilder();
            queryBuilder.where().eq("name", companyName);
            List<Company> companies = queryBuilder.query();
            if (companies.isEmpty()) {
              sendMessage(player, "That company doesn't exist!");
              return;
            }
            if (companies.get(0).getMember(player) == null) {
              sendMessage(player, "You don't have permission to do that!");
              return;
            }
            if (!companies.get(0).getMember(player).hasManagementPermission()) {
              sendMessage(player, "You don't have permission to do that!");
              return;
            }
            Member newMember = new Member(playerToInviteObject, Employee, companies.get(0));
            QueryBuilder<Member, UUID> checkQueryBuilder = databaseManager.getMemberDao().queryBuilder();
            checkQueryBuilder.where().eq("uuid", newMember.getUuid()).and().eq("company_id", newMember.getCompany().getId());
            List<Member> list = checkQueryBuilder.query();
            if (!list.isEmpty()) {
              if (list.get(0).getAcceptedInvite()) {
                sendMessage(player, "That player is already a member of that company!");
              } else {
                sendMessage(player, "That player has already been invited to that company!");
              }
              return;
            }
            databaseManager.getMemberDao().create(newMember);
            sendMessage(player, "Successfully invited player!");
            sendMessage(playerToInviteObject, "You have been invited to join " + ChatColor.YELLOW + companyName);
          } catch (SQLException e) {
            e.printStackTrace();
          }
        }).execute();
  }

  public void createCompanyAccount(Player player, String companyName, String accountName, boolean holding) {
    if (!player.isOp() && playerAccountCooldown.containsKey(player.getUniqueId()) && (System.currentTimeMillis() - playerAccountCooldown.get(player.getUniqueId())) < ACCOUNT_CREATION_COOLDOWN) {
      sendMessage(player, "You cannot make an account for another " + Util.convertString(ACCOUNT_CREATION_COOLDOWN - (System.currentTimeMillis() - playerAccountCooldown.get(player.getUniqueId()))));
      return;
    }

    Stonks.newChain()
        .async(() -> {
          try {
            Company company = databaseManager.getCompanyDao().getCompany(companyName);
            if (company == null) {
              sendMessage(player, "Invalid company name!");
              return;
            }
            if (company.getAccounts().stream().anyMatch(a -> a.getAccount().getName().toLowerCase().equals(accountName.toLowerCase()))) {
              sendMessage(player, "Account name already exist in company!");
              return;
            }
            Member member = company.getMember(player);
            if (member == null) {
              sendMessage(player, "You are not a member of this company!");
              return;
            }
            if (!member.hasManagementPermission()) {
              sendMessage(player, "You don't have permission to preform this action. Ask for a promotion!");
              return;
            }
            if (!Stonks.economy.withdrawPlayer(player, ACCOUNT_FEE).transactionSuccess()) {
              sendMessage(player, "You don't have the sufficient funds for the $" + ACCOUNT_FEE + " account creation fee.");
              return;
            }

            Account account;
            if (holding) {
              HoldingsAccount ha = new HoldingsAccount(accountName);
              databaseManager.getHoldingAccountDao().create(ha);
              databaseManager.getHoldingDao().create(new Holding(player.getUniqueId(), 1, ha));
              account = ha;
            } else {
              CompanyAccount ca = new CompanyAccount(accountName);
              databaseManager.getCompanyAccountDao().create(ca);
              account = ca;
            }
            databaseManager.getAccountLinkDao().create(new AccountLink(company, account));

            sendMessage(player, "Created new " + (holding ? "holdings " : "") + "account!");

            playerAccountCooldown.put(player.getUniqueId(), System.currentTimeMillis());
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        }).execute();
  }

  public void createHolding(Player player, int accountId, String playerName, double share) {
    Stonks.newChain()
        .async(() -> {
          if (share <= 0) {
            sendMessage(player, "Holding share must be greater than 0");
            return;
          }
          try {
            AccountLink link = databaseManager.getAccountLinkDao().queryForId(accountId);
            if (link == null) {
              sendMessage(player, "Invalid account id!");
              return;
            }
            //We have a valid account
            Member member = link.getCompany().getMember(player);
            //First make sure the account is a holdings account
            if (!(link.getAccount() instanceof HoldingsAccount)) {
              sendMessage(player, "That is not a Holdings Account!");
              return;
            }
            HoldingsAccount account = (HoldingsAccount) link.getAccount();
            //Is the player a member of that company
            if (member == null) {
              sendMessage(player, "You are not a member of that company!");
              return;
            }
            //Does the player have permission to create a holding in that account?
            if (!member.hasManagementPermission()) {
              sendMessage(player, "You do not have permission to create a holding account! Ask to be promoted.");
              return;
            }
            //Try and find the UUID of that player
            User u = essentials.getOfflineUser(playerName);
            if (u == null) {
              sendMessage(player, "That player has never played on the server!");
              return;
            }
            Player newHoldingOwner = essentials.getOfflineUser(playerName).getBase();
            //Check they are a member of that company
            if (!link.getCompany().hasMember(newHoldingOwner)) {
              sendMessage(player, "That player isn't a member of the selected company");
              return;
            }
            if (account.getPlayerHolding(newHoldingOwner.getUniqueId()) != null) {
              sendMessage(player, "That player already has a holding in this holding account!");
              return;
            }
            //We can make a holding
            databaseManager.getHoldingDao().create(new Holding(newHoldingOwner.getUniqueId(), share, account));
            sendMessage(player, "Holding successfully created!");
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        }).execute();
  }

  public void removeHolding(Player player, int accountId, String playerName) {
    //noinspection Convert2MethodRef
    Stonks.newChain()
        .async(() -> {
          try {
            AccountLink link = databaseManager.getAccountLinkDao().queryForId(accountId);
            if (link == null) {
              sendMessage(player, "Invalid account id!");
              return;
            }
            //We have a valid account
            //First make sure the account is a holdings account
            if (!(link.getAccount() instanceof HoldingsAccount)) {
              sendMessage(player, "That is not a holding account!");
              return;
            }
            HoldingsAccount account = (HoldingsAccount) link.getAccount();
            Member member = link.getCompany().getMember(player);
            //Is the player a member of that company
            if (member == null) {
              sendMessage(player, "You are not a member of that company!");
              return;
            }
            //Does the player have permission to create a holding in that account?
            if (!member.hasManagementPermission()) {
              sendMessage(player, "You do not have permission to create a holding account! Ask to be promoted.");
              return;
            }
            //Try and find the UUID of that player
            User u = essentials.getOfflineUser(playerName);
            //check if the player has been on the server
            if (u == null) {
              sendMessage(player, "That player has never played on the server!");
              return;
            }
            Player op = essentials.getOfflineUser(playerName).getBase();
            Holding playerHolding = account.getPlayerHolding(op.getUniqueId());
            if (playerHolding == null) {
              sendMessage(player, "There is no holding for this player!");
              return;
            }
            //That player has a holding
            //If their balance is lower than 1 we can remove it
            //This isnt == 0 because of possible floating point errors
            if (playerHolding.getBalance() > 1) {
              sendMessage(player, "That account is worth more than $1! Please get the player to withdraw the money from it!");
              return;
            }
            if (account.getHoldings().size() < 2) {
              sendMessage(player, "There needs to be at least one holding per holding account!");
              return;
            }
            account.removeHolding(playerHolding);
            databaseManager.getHoldingDao().delete(playerHolding);
            sendMessage(player, "Holding removed successfully!");
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        })
        .sync(() -> player.closeInventory())
        .execute();
  }

  public void withdrawFromAccount(Player player, double amount, int accountId) {
    Stonks.newChain()
        .async(() -> {
          try {
            AccountLink link = databaseManager.getAccountLinkDao().queryForId(accountId);
            if (link == null) {
              sendMessage(player, "Invalid account id!");
              return;
            }
            //We have a valid account
            //First check they are a member of the company
            if (!link.getCompany().hasMember(player)) {
              sendMessage(player, "You are not a member of the company the account is in!");
              return;
            }

            if (amount < 0) {
              sendMessage(player, "You cannot withdraw a negative number");
              return;
            }

            Member member = link.getCompany().getMember(player);
            IAccountVisitor visitor = new IAccountVisitor() {
              @Override
              public void visit(CompanyAccount a) {
                //With a company account we need to verify they have withdraw permission
                if (member.hasManagementPermission()) {
                  if (a.getTotalBalance() >= amount) {
                    a.subtractBalance(amount);
                    try {
                      databaseManager.getCompanyAccountDao().update(a);
                      Stonks.economy.depositPlayer(player, amount);
                      sendMessage(player, "Money withdrawn successfully!");


                      //Log the transaction
                      databaseManager.logTransaction(new Transaction(link, player.getUniqueId(), null, -amount));

                    } catch (SQLException e) {
                      e.printStackTrace();
                      sendMessage(player, "Error while executing command!");
                    }

                  } else {
                    sendMessage(player, "That account doesn't have enough funds to complete this transaction!");
                  }
                } else {
                  sendMessage(player, "You have insufficient permissions to withdraw money from this account!");
                }
              }

              @Override
              public void visit(HoldingsAccount a) {
                //Check to see if they own a holding in this holdingsaccount
                Holding h = a.getPlayerHolding(player.getUniqueId());
                if (h != null) {
                  //They have a holding
                  if (h.getBalance() >= amount) {
                    //They have enough money so we can withdraw
                    try {
                      h.subtractBalance(amount);
                      databaseManager.getHoldingDao().update(h);
                      Stonks.economy.depositPlayer(player, amount);
                      sendMessage(player, "Money withdrawn successfully!");
                    } catch (SQLException e) {
                      e.printStackTrace();
                      sendMessage(player, "Error while executing command!");
                    }
                  } else {
                    sendMessage(player, "That account doesn't have enough funds to complete this transaction!");
                  }
                } else {
                  sendMessage(player, "You do not have a holding in this account!");
                }
              }
            };
            link.getAccount().accept(visitor);
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        }).execute();
  }

  public void setLogo(Player player, String companyName) {
    ItemStack itemInHand = player.getInventory().getItemInMainHand();
    if (itemInHand.getAmount() == 0) {
      sendMessage(player, "You must hold the item you wish to set as the company icon!");
      return;
    }
    Stonks.newChain()
        .async(() -> {
          Company company;
          try {
            company = databaseManager.getCompanyDao().getCompany(companyName);
            if (company == null) {
              sendMessage(player, "Invalid company!");
              return;
            }
            if (!company.hasMember(player)) {
              sendMessage(player, "You are not a member of that company!");
              return;
            }
            company.setLogoMaterial(itemInHand.getType().name());
            databaseManager.getCompanyDao().update(company);
            sendMessage(player, "Company logo updated successfully!");
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        })
        .execute();
  }

  public void payAccount(Player sender, int accountId, String message, double amount) {
    Stonks.newChain()
        .async(() -> {
          AccountLink accountLink = null;
          if (amount < 0) {
            sendMessage(sender, "You cannot pay a negative number");
            return;
          }


          try {
            accountLink = databaseManager.getAccountLinkDao().queryForId(accountId);
          } catch (SQLException e) {
            e.printStackTrace();
          }
          if (accountLink == null) {
            sendMessage(sender, "Invalid account id!");
            return;
          }

          if (!Stonks.economy.withdrawPlayer(sender, amount).transactionSuccess()) {
            sendMessage(sender, "Insufficient funds!");
            return;
          }

          accountLink.getAccount().addBalance(amount);
          IAccountVisitor visitor = new IAccountVisitor() {
            @Override
            public void visit(CompanyAccount a) {
              try {
                databaseManager.getCompanyAccountDao().update(a);
              } catch (SQLException e) {
                sendMessage(sender, "Error while executing command!");
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
                sendMessage(sender, "Error while executing command!");
                e.printStackTrace();
              }
            }
          };
          accountLink.getAccount().accept(visitor);
          //Log the transaction
          databaseManager.logTransaction(new Transaction(accountLink, sender.getUniqueId(), message, amount));
          //Tell the user we paid the account
          sendMessage(sender, "Paid " + ChatColor.YELLOW + accountLink.getCompany().getName() + " (" + accountLink.getAccount().getName() + ")" + ChatColor.YELLOW + " $" + Util.commify(amount) + ChatColor.GREEN + "!");

          //Send a message to all managers in the company that are online that the company got paid
          for (Member member : accountLink.getCompany().getMembers()) {
            if (member.hasManagementPermission()) {
              Player u = essentials.getUser(member.getUuid()).getBase();
              if (!u.getName().equalsIgnoreCase(sender.getName()) && u.isOnline()) {
                sendMessage(u, sender.getDisplayName() + ChatColor.GREEN + " paid " + ChatColor.YELLOW + " " + accountLink.getCompany().getName() + " (" + accountLink.getAccount().getName() + ") $" + Util.commify(amount));
              }
            }
          }
        }).execute();
  }

  public void openMemberInfo(Player player, String memberName, String companyName) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            Player playerProfile = essentials.getUser(memberName).getBase();
            Company company = databaseManager.getCompanyDao().getCompany(companyName);

            if (company == null || playerProfile == null) {
              sendMessage(player, "That Player or Company could not be found!");
              return null;
            }

            Member member = databaseManager.getMemberDao().getMember(playerProfile, company);
            if (member == null) {
              sendMessage(player, "That player isn't a member of that company!");
              return null;
            }
            return MemberInfoGui.getInventory(member);
          } catch (SQLException e) {
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync((result) -> result.open(player))
        .execute();
  }

  public void setRole(Player player, String playerName, String roleString, String companyName) {
    Stonks.newChain()
        .async(() -> {
          //Try and parse the role
          Role newRole;
          try {
            newRole = Role.valueOf(roleString);
          } catch (IllegalArgumentException e) {
            sendMessage(player, "Invalid role!");
            return;
          }
          //Now see if the player to promote exists
          Player playerToChange = essentials.getUser(playerName).getBase();
          if (playerToChange == null) {
            sendMessage(player, "That player has never played on the server!");
            return;
          }
          Company company;
          try {
            company = databaseManager.getCompanyDao().getCompany(companyName);
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
            return;
          }
          //Find the company they are making the changes in
          if (company == null) {
            sendMessage(player, "That company doesn't exist!");
            return;
          }
          //Now check both are members
          Member changingMember = company.getMember(player);
          if (changingMember == null) {
            sendMessage(player, "You are not a member of that company!");
            return;
          }
          Member memberToChange = company.getMember(playerToChange);
          if (memberToChange == null) {
            sendMessage(player, "That player is not part of that company!");
            return;
          }
          //Both players are a member of the company
          //Now check permissions
          //A player can't change their own role
          if (changingMember.getUuid().equals(memberToChange.getUuid())) {
            sendMessage(player, "You cannot change your own role!");
            return;
          }
          if (!changingMember.canChangeRole(memberToChange, newRole)) {
            sendMessage(player, "You do not have the permissions to promote that user to " + ChatColor.YELLOW + roleString);
            return;
          }
          //If we are promoting them to a ceo then demote us
          try {
            databaseManager.getMemberDao().setRole(memberToChange, newRole);
            sendMessage(player, "Promoted user successfully!");
            OfflinePlayer p = Bukkit.getOfflinePlayer(memberToChange.getUuid());
            if (p.isOnline() && p.getPlayer() != null) {
              sendMessage(p.getPlayer(), "Your rank in the company " + ChatColor.YELLOW + company.getName() + ChatColor.GREEN + " has changed to " + ChatColor.YELLOW + newRole.toString());
            }
            if (newRole == CEO) {
              databaseManager.getMemberDao().setRole(changingMember, Manager);
              sendMessage(player, "You promoted " + ChatColor.YELLOW + playerName + ChatColor.GREEN + " to CEO, you have been demoted to a Manager since there can only be one CEO.");
            }
          } catch (SQLException e) {
            sendMessage(player, "Error while executing command!");
            e.printStackTrace();
          }
        }).execute();
  }

  public void kickMember(Player player, String memberName, String companyName) {
    Stonks.newChain()
        .async(() -> {
          try {
            User u = essentials.getUser(memberName);
            if (u == null) {
              sendMessage(player, "That player has never played on this server before!");
              return;
            }
            Player playerProfile = u.getBase();
            Company company = databaseManager.getCompanyDao().getCompany(companyName);

            if (company == null) {
              sendMessage(player, "Invalid company name!");
              return;
            }

            Member memberToKick = databaseManager.getMemberDao().getMember(playerProfile, company);
            if (memberToKick == null) {
              sendMessage(player, "That player isn't part of that company!");
              return;
            }

            Member sender = databaseManager.getMemberDao().getMember(player, company);
            if (sender == null || !sender.hasManagementPermission()) {
              sendMessage(player, "You don't have permission to preform that action.");
              return;
            }
            if (memberToKick.getRole() == Role.CEO) {
              sendMessage(player, "You cannot kick the CEO!");
              return;
            }

            if (memberToKick.hasHoldings(databaseManager)) {
              sendMessage(player, "That player has holdings! Please delete them before kicking them!");
              return;
            }
            databaseManager.getMemberDao().deleteMember(memberToKick);
            sendMessage(player, "Player kicked successfully!");
//            OfflinePlayer p = Bukkit.getOfflinePlayer(memberToKick.getUuid());
//            if (p.isOnline() && p.getPlayer() != null) p.getPlayer().sendMessage(ChatColor.RED + "You have been fired from " + company.getName() + "!");
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        })
        .sync(() -> player.performCommand("stonks members " + companyName))
        .execute();
  }

  public void openHoldingAccountInfo(Player player, int accountId) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            AccountLink link;
            link = databaseManager.getAccountLinkDao().queryForId(accountId);
            if (!(link.getAccount() instanceof HoldingsAccount)) {
              sendMessage(player, "You can only view holdings of holding accounts!");
              return null;
            }
            return new HoldingListGui((HoldingsAccount) link.getAccount());
          } catch (SQLException e) {
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }

  public void showTopCompanies(Player player) {
    sendMessage(player, "Fetching top companies, please wait one moment...");
    Stonks.newChain()
        .async(() -> {
          List<Company> list;
          try {
            QueryBuilder<Company, UUID> companyQueryBuilder = databaseManager.getCompanyDao().queryBuilder();
            list = companyQueryBuilder.query();
            list.sort((c1, c2) -> (int) (c2.getTotalValue() - c1.getTotalValue()));
            for (int i = 0; i < Math.min(10, list.size()); i++) {
              Company company = list.get(i);
              sendMessage(player, "#" + (i + 1) + " - " + ChatColor.YELLOW + company.getName() + ChatColor.GREEN + ", " + ChatColor.DARK_GREEN + "$" + Util.commify(company.getTotalValue()));
            }
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        })
        .execute();
  }

  public void renameCompany(Player player, String companyName, String newCompanyName) {
    Stonks.newChain()
        .async(() -> {
          if (newCompanyName.length() > 32) {
            sendMessage(player, "A company name cannot be larger than 32 characters!");
            return;
          }
          Company company;
          try {
            company = databaseManager.getCompanyDao().getCompany(companyName);
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
            return;
          }
          //Find the company they are making the changes in
          if (company == null) {
            sendMessage(player, "Invalid Company!");
            return;
          }
          company.setName(newCompanyName);
          try {
            databaseManager.getCompanyDao().update(company);
            sendMessage(player, "Company name changed successfully!");
          } catch (SQLException e) {
            sendMessage(player, "Error while executing command!");
            e.printStackTrace();
          }
        }).execute();
  }

  public void changeVerification(Player player, String companyName, boolean newVerification) {
    Stonks.newChain()
        .async(() -> {
          Company company;
          try {
            company = databaseManager.getCompanyDao().getCompany(companyName);
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
            return;
          }
          //Find the company they are making the changes in
          if (company == null) {
            sendMessage(player, "Invalid company name!");
            return;
          }
          company.setVerified(newVerification);
          try {
            databaseManager.getCompanyDao().update(company);
            sendMessage(player, "Company verification updated!");
          } catch (SQLException e) {
            sendMessage(player, "Error while executing command!");
            e.printStackTrace();
          }
        }).execute();
  }

  public void changeServiceMaxSubs(Player player, int maxSubs, Service service) {
    Member m = service.getCompany().getMember(player);
    if (m == null || !m.hasManagementPermission()) {
      sendMessage(player, "You don't have management perms for this company");
      return;
    }

    if (maxSubs > 0 && maxSubs < service.getNumSubscriptions()) {
      sendMessage(player, "You can't set the max subscriptions lower than the current number of subscriptions");
      return;
    }

    service.setMaxSubscribers(maxSubs);
    try {
      databaseManager.getServiceDao().update(service);
      sendMessage(player, "Max subs updated to " + maxSubs + " :)");
    } catch (SQLException e) {
      e.printStackTrace();
      sendMessage(player, "Error while executing command!");
    }
  }

  public void paySubscription(Player player, int id) {
    Service service;
    try {
      service = databaseManager.getServiceDao().queryForId(id);
    } catch (SQLException e) {
      e.printStackTrace();
      sendMessage(player, "Error while executing command!");
      return;
    }
    if (service == null) {
      sendMessage(player, "Service id not found");
      return;
    }

    Subscription subscription = service.getSubscription(player);
    if (subscription == null) {
      sendMessage(player, "You are not subscribed to this service");
      return;
    }

    if (!subscription.isOverdue()) {
      sendMessage(player, "The subscription is not overdue, you don't need to pay for it");
      return;
    }


    new ConfirmationGui.Builder().title("Pay bill of $" + service.getCost()).onChoiceMade(
        c -> {
          if (!c) return;
          if (!Stonks.economy.withdrawPlayer(player, service.getCost()).transactionSuccess()) {
            sendMessage(player, "Insufficient funds!");
          } else {
            //Payment success
            try {
              //Update that the subscription is paid
              subscription.registerPaid();
              databaseManager.getSubscriptionDao().update(subscription);
              service.getAccount().getAccount().addBalance(service.getCost());
              databaseManager.logTransaction(new Transaction(service.getAccount(),
                  player.getUniqueId(), "Subscription payment for " + service.getName(), service.getCost()));

              //Update the account database
              databaseManager.updateAccount(service.getAccount().getAccount());

              //Subscription created!
              sendMessage(player, "You have resubscribed to the service " + service.getName() +
                  " (provided by " + service.getCompany().getName() + ")!");
              sendMessage(player, "This service will expire in " + service.getDuration() + " days");
              sendMessage(player, "Your subscription will automatically renew, so you don't need to do anything.");
            } catch (SQLException e) {
              e.printStackTrace();
              sendMessage(player, "Error while executing command!");
            }
          }
        }
    ).open(player);

  }

  public void unsubscribeFromService(Player player, int id) {
    Service service;
    try {
      service = databaseManager.getServiceDao().queryForId(id);
    } catch (SQLException e) {
      e.printStackTrace();
      sendMessage(player, "Error while executing command!");
      return;
    }
    if (service == null) {
      sendMessage(player, "Service id not found");
      return;
    }

    Subscription subscription = service.getSubscription(player);
    if (subscription == null) {
      sendMessage(player, "You are not subscribed to this service");
      return;
    }

    new ConfirmationGui.Builder().title("Unsubscribe from " + service.getName() + "?")
        .onChoiceMade(c -> {
          if (!c) return;
          try {
            databaseManager.getSubscriptionDao().delete(subscription);
            sendMessage(player, "You have unsubscribed from " + service.getName() +
                " (from " + service.getCompany().getName() + ")");
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
          }
        }).open(player);

  }

  public void createService(Player player, double duration, double cost, int maxSubs, String name, Company company, AccountLink account) {
    //Check for the same name
    for (Service service : company.getServices()) {
      if (service.getName().equals(name)) {
        sendMessage(player, "A service with the same name already exists for this company");
        return;
      }
    }

    if (duration <= 0.5) {
      sendMessage(player, "Service duration must be greater than 0.5 (12 hours)");
      return;
    }

    if (name.length() > 40) {
      sendMessage(player, "Service name cannot be longer than 40 characters");
      return;
    }

    //Only verified companies can create services
    if (!company.isVerified()) {
      sendMessage(player, "Your company must be verified before you can create a service. Ask a moderator to consider verifying your company.");
      return;
    }

    Service newService = new Service(name, company, account, duration, cost, maxSubs);
    try {
      databaseManager.getServiceDao().create(newService);
      sendMessage(player, "Service created!");
    } catch (SQLException e) {
      e.printStackTrace();
      sendMessage(player, "Error while executing command!");
    }
  }

  public void subscribeToService(Player player, int serviceId, Boolean autoPay) {
    Service service;
    try {
      service = databaseManager.getServiceDao().queryForId(serviceId);
    } catch (SQLException e) {
      e.printStackTrace();
      sendMessage(player, "Error while executing command!");
      return;
    }
    if (service == null) {
      sendMessage(player, "That service id does not exist");
      return;
    }
    if (service.getMaxSubscriptions() > 0 && service.getNumSubscriptions() >= service.getMaxSubscriptions()) {
      sendMessage(player, "That service has the maximum number of subscriptions");
      return;
    }

    if (service.getSubscription(player) != null) {
      sendMessage(player, "You cannot subscribe to the same service twice");
      return;
    }

    new ConfirmationGui.Builder().title("Accept first bill of $" + service.getCost()).onChoiceMade(
        c -> {
          if (!c) return;
          //We can now subscribe to the service
          //Setup the subscription
          Subscription subscription = new Subscription(player, service);
          if (!Stonks.economy.withdrawPlayer(player, service.getCost()).transactionSuccess()) {
            sendMessage(player, "Insufficient funds!");
          } else {
            //Payment success
            try {
              databaseManager.getSubscriptionDao().create(subscription);
              service.getAccount().getAccount().addBalance(service.getCost());
              databaseManager.logTransaction(new Transaction(service.getAccount(),
                  player.getUniqueId(), "Subscription payment for " + service.getName(), service.getCost()));
              //Update the account database
              databaseManager.updateAccount(service.getAccount().getAccount());


              //Subscription created!
              sendMessage(player, "You have subscribed to the service " + service.getName() + " (provided by " + service.getCompany().getName() + ")");
              sendMessage(player, "This service will expire in " + service.getDuration() + " days");
              if (autoPay) {
                sendMessage(player, "Your subscription will automatically renew, so you don't need to do anything.");
              } else {
                sendMessage(player, "You have set your subscription to manually renew, so you will need to resubscribe in " + service.getDuration() + " days time.");
              }
            } catch (SQLException e) {
              e.printStackTrace();
              sendMessage(player, "Error while executing command!");
            }
          }
        }
    ).open(player);
  }

  public void openCompanyServices(Player player, int accountId) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            AccountLink accountLink = databaseManager.getAccountLinkDao().queryForId(accountId);
            if (accountLink == null) {
              sendMessage(player, "That accountId doesn't exist!");
              return null;
            }
            return new ServicesListGui(accountLink.getCompany(), accountLink);
          } catch (SQLException e) {
            sendMessage(player, "Error while executing command!");
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }

  public void openCompanyServiceFolders(Player player, String companyName) {
    Stonks.newChain()
        .asyncFirst(() -> {
          try {
            Company company = databaseManager.getCompanyDao().getCompany(companyName);
            if (company == null) {
              sendMessage(player, "That company doesn't exist!");
              return null;
            }
            return new ServiceFoldersListGui(company);
          } catch (SQLException e) {
            sendMessage(player, "Error while executing command!");
            e.printStackTrace();
          }
          return null;
        })
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }

  @SuppressWarnings("DuplicatedCode")
  public void changeHidden(Player player, String companyName, boolean newHidden) {
    Stonks.newChain()
        .async(() -> {
          Company company;
          try {
            company = databaseManager.getCompanyDao().getCompany(companyName);
          } catch (SQLException e) {
            e.printStackTrace();
            sendMessage(player, "Error while executing command!");
            return;
          }
          //Find the company they are making the changes in
          if (company == null) {
            sendMessage(player, "Invalid company name!");
            return;
          }
          company.setHidden(newHidden);
          try {
            databaseManager.getCompanyDao().update(company);
            sendMessage(player, "Company visibility updated!");
          } catch (SQLException e) {
            sendMessage(player, "Error while executing command!");
            e.printStackTrace();
          }
        }).execute();
  }

  public void transactionHistoryPagination(Player player, int accountId, int page) {
    AccountLink link;
    try {
      link = databaseManager.getAccountLinkDao().queryForId(accountId);
    } catch (SQLException e) {
      e.printStackTrace();
      sendMessage(player, "Error while executing command!");
      return;
    }

    if (link == null) {
      sendMessage(player, "Account not found");
      return;
    }

    List<Transaction> transactions = databaseManager.getTransactionDao()
        .getTransactionsForAccount(link, databaseManager.getAccountLinkDao().queryBuilder(), 10, 10 * page);
    if (transactions.size() == 0) {
      sendMessage(player, "!");
      sendMessage(player, ChatColor.YELLOW + "==== No more pages ====");
      return;
    }
    int i = 0;
    sendMessage(player, ChatColor.YELLOW + "==== Page " + page + " ====");
    for (Transaction transaction : transactions) {
      StringBuilder s = new StringBuilder((i + page * 10) + ")");
      s.append("[").append(transaction.getId()).append("] ");
      s.append("$").append(transaction.getAmount()).append(" ");
      s.append((transaction.getPayee() != null) ? transaction.getPayee() : "unknown").append(" ");
      if (transaction.getMessage() != null) s.append(transaction.getMessage());
      sendMessage(player, s.toString());
      i++;
    }
    if (i < 10) sendMessage(player, "!");
  }

  public DatabaseManager getDatabaseManager() {
    return databaseManager;
  }

  Essentials getEssentials() {
    return essentials;
  }

  public void showPlayerHoldings(Player player) {
    //Get all holdings for a player
    Stonks.newChain()
        .asyncFirst(() -> new AllPlayerHoldingsGui(player))
        .abortIfNull()
        .sync(gui -> gui.show(player))
        .execute();
  }
}

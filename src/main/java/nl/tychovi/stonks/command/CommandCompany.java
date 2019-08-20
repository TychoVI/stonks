package nl.tychovi.stonks.command;

import co.aikar.taskchain.TaskChainTasks;
import com.earth2me.essentials.Essentials;
import com.j256.ormlite.stmt.QueryBuilder;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import javafx.concurrent.Task;
import nl.tychovi.stonks.Database.Company;
import nl.tychovi.stonks.Database.CompanyAccount;
import nl.tychovi.stonks.Database.Member;
import nl.tychovi.stonks.Database.Role;
import nl.tychovi.stonks.Stonks;
import nl.tychovi.stonks.gui.CompanyListGui;
import nl.tychovi.stonks.gui.InviteListGui;
import nl.tychovi.stonks.managers.DatabaseManager;
import nl.tychovi.stonks.managers.GuiManager;
import nl.tychovi.stonks.managers.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class CommandCompany implements CommandExecutor {

  private DatabaseManager databaseManager;
  private GuiManager guiManager;
  private JavaPlugin plugin;
  private Essentials ess;

  public CommandCompany(DatabaseManager databaseManager, Stonks plugin) {
    this.databaseManager = databaseManager;
    this.plugin = plugin;
    this.ess = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
    this.guiManager = (GuiManager) plugin.getModule("guiManager");
  }

  @Override
  public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, @Nonnull String[] args) {

    if (!(sender instanceof Player)) {
      sender.sendMessage(ChatColor.RED + "This command can only be used by a player!");
      return true;
    }

    Player player = (Player) sender;

    if(args.length == 0) {
      //add list with all commands here later;
        MessageManager.sendHelpMessage(player);
      return true;
    }

    switch (args[0].toLowerCase()) {
        case "create": {
            if (!args[1].isEmpty()) {
              return companyCreate(args[1], player);
            } else {
              player.sendMessage(ChatColor.RED + "Correct usage: /stonks create <company>");
              return true;
            }
        }
        case "invites": {
            List<Member> invites = null;
            try {
              invites = databaseManager.getMemberDao().getInvites(player);
            } catch (SQLException e) {
              e.printStackTrace();
            }
            if(invites == null) {
              player.sendMessage(ChatColor.RED + "You don't have any invites!");
              return true;
            }
            InviteListGui.getInventory().open(player);
            return true;
        }
        case "list": {
            CompanyListGui.getInventory().open(player);
            return true;
        }
        case "invite": {
            if (!args[1].isEmpty() && !args[2].isEmpty()) {
                return invitePlayerToCompany(args[1], args[2], player);
            } else {
                player.sendMessage(ChatColor.RED + "Correct usage: /stonks invite <player> <company>");
                return true;
            }
        }
        case "createcompanyaccount": {
            try {
                databaseManager.getCompanyDao().getCompany(args[1]).createCompanyAccount(databaseManager, args[2]);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return true;
        }
        case "setlogo": {
            if(args.length < 2) {
                player.sendMessage(ChatColor.RED + "Please specify a company!");
                return true;
            }
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if(itemInHand.getAmount() == 0) {
                player.sendMessage(ChatColor.RED + "You must be holding an item to set it as your company logo!");
                return true;
            }
            try {
                Company company = databaseManager.getCompanyDao().getCompany(args[1]);
                if(company == null) {
                    player.sendMessage(ChatColor.RED + "That company does not exist!");
                    return true;
                }
                if(!company.hasMember(player)) {
                    player.sendMessage(ChatColor.RED + "You're not a member of that company!");
                    return true;
                }
                company.setLogoMaterial(itemInHand.getType().name());
                databaseManager.getCompanyDao().update(company);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return true;
        }
    }
    MessageManager.sendHelpMessage(player);
    return true;
  }
  
  private Boolean companyCreate(String companyName, Player player) {
      if(companyName.length() > 32) {
          player.sendMessage(ChatColor.RED + "A company name can't be longer than 32 characters!");
          return true;
      }
    if(Stonks.companies.size() != 0) {
      for(Company company : Stonks.companies) {
        if(company.getName().equals(companyName)) {
          player.sendMessage(ChatColor.RED + "A company with that name already exists!");
          return true;
        }
      }
    }
    try {
      double creationFee = plugin.getConfig().getInt("fees.companycreation");
      if(!Stonks.economy.withdrawPlayer(player, creationFee).transactionSuccess()) {
        player.sendMessage(ChatColor.RED + "There is a $" + creationFee + " fee for creating a company and you did not have sufficient funds, get more money you poor fuck.");
        return true;
      }
      Company newCompany = new Company(companyName, "S" + companyName, player);
      databaseManager.getCompanyDao().assignEmptyForeignCollection(newCompany, "members");
      Stonks.companies.add(newCompany);
      databaseManager.getCompanyDao().create(newCompany);

      CompanyAccount companyAccount = new CompanyAccount(newCompany, "Main");
      databaseManager.getCompanyAccountDao().create(companyAccount);

      Member creator = new Member(player, Role.CEO);
      newCompany.getMembers().add(creator);

      player.sendMessage(ChatColor.GREEN + "Company with name: \"" + companyName + "\" created successfully!");
      return true;
    } catch (SQLException e) {
      e.printStackTrace();
      player.sendMessage(ChatColor.RED + "Something went wrong! :(");
      return false;
    }
  }

  private Boolean invitePlayerToCompany(String playerToInvite, String companyName, Player player) {
    if(Stonks.companies.size() != 0) {
      for(Company company : Stonks.companies) {
        if(company.getName().equals(companyName)) {
          if(company.hasMember(player)) {
            Member playerProfile = company.getMember(player);
            if(playerProfile.hasManagamentPermission()) {
              Player playerToInviteObject = ess.getUser(playerToInvite).getBase();
              Member newMember = new Member(playerToInviteObject, Role.Employee, company);
              try {
                  QueryBuilder<Member, UUID> queryBuilder = databaseManager.getMemberDao().queryBuilder();
                  queryBuilder.where().eq("uuid", newMember.getUuid()).and().eq("company_id", newMember.getCompany().getId());
                  List<Member> list = queryBuilder.query();
                  if(!list.isEmpty()) {
                      if(list.get(0).getAcceptedInvite()) {
                          player.sendMessage(ChatColor.RED + playerToInvite + " is already a member of " + newMember.getCompany().getName());
                      } else {
                          player.sendMessage(ChatColor.RED + playerToInvite + " has already been invited to " + newMember.getCompany().getName());
                      }
                      return true;
                  }
                databaseManager.getMemberDao().create(newMember);
                player.sendMessage(playerToInviteObject.getName() + " has successfully been invited!");
                playerToInviteObject.sendMessage("You have been invited to join " + companyName);
                return true;
              } catch (SQLException e) {
                e.printStackTrace();
              }
            }
            return true;
          }
        }
      }
    }
    return false;
  }
}

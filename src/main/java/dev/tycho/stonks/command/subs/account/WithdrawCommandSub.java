package dev.tycho.stonks.command.subs.account;

import dev.tycho.stonks.command.base.CommandSub;
import dev.tycho.stonks.gui.AccountSelectorGui;
import dev.tycho.stonks.gui.CompanySelectorGui;
import dev.tycho.stonks.managers.DatabaseHelper;
import dev.tycho.stonks.model.accountvisitors.ReturningAccountVisitor;
import dev.tycho.stonks.model.core.*;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public class WithdrawCommandSub extends CommandSub {

  private static final List<String> AMOUNTS = Arrays.asList(
      "1",
      "10",
      "1000",
      "10000");

  @Override
  public List<String> onTabComplete(CommandSender sender, String alias, String[] args) {
    if (args.length == 2) {
      return copyPartialMatches(args[1], AMOUNTS);
    }
    return null;
  }

  @Override
  public void onCommand(Player player, String alias, String[] args) {
    if (args.length == 1) {
      sendMessage(player, "Correct usage: " + ChatColor.YELLOW + "/" + alias + " withdraw <amount> [<account id>]");
      return;
    }

    if (!validateDouble(args[1])) {
      sendMessage(player, "Invalid amount!");
      return;
    }
    double amount = Double.parseDouble(args[1]);
    if (args.length == 3) {
      if (StringUtils.isNumeric(args[2])) {
        DatabaseHelper.getInstance().withdrawFromAccount(player, amount, Integer.parseInt(args[2]));
        return;
      } else {
        sendMessage(player, "Invalid account id! Pulling up selector...");
      }
    }
    List<Company> list = DatabaseHelper.getInstance().getDatabaseManager().getCompanyDao().getAllCompanies();
    //We need a list of all companies with a withdrawable account for this player
    //Remove companies where the player is not a manager and doesn't have an account
    //todo remove this messy logic
    for (int i = list.size() - 1; i >= 0; i--) {
      boolean remove = true;
      Company c = list.get(i);
      Member m = c.getMember(player);
      if (m != null && m.hasManagementPermission()) {
        //If a manager or ceo
        remove = false;
      }
      //If you are not a manager, or a non-member with a holding then don't remove
      for (AccountLink a : c.getAccounts()) {
        //Is there a holding account for the player
        ReturningAccountVisitor<Boolean> visitor = new ReturningAccountVisitor<>() {
          @Override
          public void visit(CompanyAccount a) {
            val = false;
          }

          @Override
          public void visit(HoldingsAccount a) {
            val = (a.getPlayerHolding(player.getUniqueId()) != null);
          }
        };
        a.getAccount().accept(visitor);
        if (visitor.getRecentVal()) remove = false;
      }
      if (remove) list.remove(i);
    }

    new CompanySelectorGui.Builder()
        .companies(list)
        .title("Select a company to withdraw from")
        .companySelected((company ->
            new AccountSelectorGui.Builder()
                .company(company)
                .title("Select an account to withdraw from")
                .accountSelected(acc -> {
                  DatabaseHelper.getInstance().withdrawFromAccount(player, amount, acc.getId());
                })
                .open(player)))
        .open(player);
  }

}

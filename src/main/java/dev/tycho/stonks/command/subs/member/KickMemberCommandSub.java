package dev.tycho.stonks.command.subs.member;

import dev.tycho.stonks.command.base.CommandSub;
import dev.tycho.stonks.managers.Repo;
import dev.tycho.stonks.model.accountvisitors.ReturningAccountVisitor;
import dev.tycho.stonks.model.core.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KickMemberCommandSub extends CommandSub {

  public KickMemberCommandSub() {
    super(false);
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, String alias, String[] args) {
    if (args.length == 2) {
      return matchPlayerName(args[1]);
    }
    return null;
  }

  @Override
  public void onCommand(Player player, String alias, String[] args) {
    if (args.length < 3) {
      sendMessage(player, "Correct usage: " + ChatColor.YELLOW + "/" + alias + " kickmember <player> <company>");
      return;
    }
    Company company = companyFromName(concatArgs(2, args));
    kickMember(player, company, args[1]);
  }

  private void kickMember(Player player, Company company, String playerName) {
    Player playerToKick = playerFromName(playerName);
    if (playerToKick == null) {
      sendMessage(player, "That player has never played on this server before!");
      return;
    }

    if (company == null) {
      sendMessage(player, "Invalid company!");
      return;
    }

    Member memberToKick = company.getMember(playerToKick);
    if (memberToKick == null) {
      sendMessage(player, "That player isn't part of that company!");
      return;
    }

    Member sender = company.getMember(player);
    //If the the member is not kicking themselves and doesnt have management permission
    if (sender == null || (!memberToKick.playerUUID.equals(sender.playerUUID) && !sender.hasManagamentPermission())) {
      sendMessage(player, "You don't have permission to preform that action.");
      return;
    }

    if (memberToKick.role == Role.CEO) {
      sendMessage(player, "You cannot kick the CEO!");
      return;
    }

    boolean hasHoldings = false;
    for (Account account : company.accounts) {
      ReturningAccountVisitor<Boolean> visitor = new ReturningAccountVisitor<Boolean>() {
        @Override
        public void visit(CompanyAccount a) {
          val = false;
        }

        @Override
        public void visit(HoldingsAccount a) {
          val = a.getPlayerHolding(playerToKick.getUniqueId()) != null;
        }
      };
      account.accept(visitor);
      hasHoldings = visitor.getRecentVal();
      if (hasHoldings) break;
    }


    if (hasHoldings) {
      sendMessage(player, "That player has holdings! Please delete them before kicking them!");
      return;
    }

    //We can kick the player
    if (Repo.getInstance().deleteMember(memberToKick)) {
      sendMessage(player, "Player kicked successfully!");
    } else {
      sendMessage(player, "Error kicking player");
    }
  }
}

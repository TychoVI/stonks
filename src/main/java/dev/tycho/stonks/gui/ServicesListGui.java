package dev.tycho.stonks.gui;

import dev.tycho.stonks.model.core.AccountLink;
import dev.tycho.stonks.model.core.Company;
import dev.tycho.stonks.model.core.Member;
import dev.tycho.stonks.model.service.Service;
import dev.tycho.stonks.util.Util;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ServicesListGui extends CollectionGuiBase<Service> {
  private Company company;

  public ServicesListGui(Company company, AccountLink folder) {
    super(servicesInFolder(company.getServices(), folder), company.getName() + " services (" + folder.getAccount().getName() + ")");
    this.company = company;
  }

  static Collection<Service> servicesInFolder(Collection<Service> services, AccountLink folder) {
    List<Service> newServices = new ArrayList<>();
    for (Service service : services) {
      if (service.getAccount().getId() == folder.getId()) {
        newServices.add(service);
      }
    }
    return newServices;
  }


  @Override
  protected void customInit(Player player, InventoryContents contents) {
    contents.set(0, 0, ClickableItem.of(Util.item(Material.BARRIER, "Back to service folders"), e -> player.performCommand("stonks servicefolders " + company.getName())));
    contents.set(0, 4, ClickableItem.empty(Util.item(Material.getMaterial(company.getLogoMaterial()), company.getName())));
  }

  @Override
  protected ClickableItem itemProvider(Player player, Service obj) {
    //Two different sets of actions based on the role of the player in the company
    Member m = obj.getCompany().getMember(player);
    //If the player isnt a manager then allow them to subscribe
    if (m == null || !m.hasManagementPermission()) {
      //If the player is subscribed tell them, and dont let them subscribe
      if (obj.getSubscription(player) != null) {
        return ClickableItem.of(ItemInfoHelper.serviceDisplayItem(obj,
            ChatColor.GREEN + "You are subscribed!",
            ChatColor.RED + "Left click to unsubscribe",
            ChatColor.LIGHT_PURPLE + "Right click to view subscribers"),
            e -> {
              if (e.getClick().isLeftClick()) {
                player.performCommand("stonks unsubscribe " + obj.getId());
              } else if (e.getClick().isRightClick()) {
                player.performCommand("stonks subscribers " + obj.getId());
              }
            }
        );
        //Else allow the player to subscribe
      } else {
        return ClickableItem.of(ItemInfoHelper.serviceDisplayItem(obj,
            ChatColor.LIGHT_PURPLE + "Left click to subscribe",
            ChatColor.LIGHT_PURPLE + "Right click to view subscribers"),
            e -> {
              if (e.getClick().isLeftClick()) {
                player.performCommand("stonks subscribe " + obj.getId());
              } else if (e.getClick().isRightClick()) {
                player.performCommand("stonks subscribers " + obj.getId());
              }
            }
        );
      }
      //Else show management info and commands
    } else {
      return ClickableItem.of(ItemInfoHelper.serviceDisplayItem(obj,
          ChatColor.LIGHT_PURPLE + "Left click to view info",
          ChatColor.LIGHT_PURPLE + "Right click to view subscribers"),
          e -> {
            if (e.getClick().isLeftClick()) {
              player.performCommand("stonks serviceinfo " + obj.getId());
            } else if (e.getClick().isRightClick()) {
              player.performCommand("stonks subscribers " + obj.getId());
            }
          }
      );
    }


  }
}

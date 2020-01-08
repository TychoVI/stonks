package dev.tycho.stonks.api.perks;

import dev.tycho.stonks.model.core.Company;
import dev.tycho.stonks.model.core.Member;
import org.bukkit.Material;

public abstract class CompanyPerk {

  private final String name;
  private final Material icon;
  private final int price;
  private final String[] description;

  public CompanyPerk(String name, Material icon, int price, String... description) {
    this.name = name;
    this.icon = icon;
    this.price = price;
    this.description = description;
  }

  public abstract void onPurchase(Company company, Member purchaser);

  public String getName() {
    return name;
  }

  public Material getIcon() {
    return icon;
  }

  public int getPrice() {
    return price;
  }

  public String[] getDescription() {
    return description;
  }
}

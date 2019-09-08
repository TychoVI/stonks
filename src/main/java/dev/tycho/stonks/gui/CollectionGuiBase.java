package dev.tycho.stonks.gui;

import dev.tycho.stonks.managers.DatabaseManager;
import dev.tycho.stonks.util.Util;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.InventoryManager;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;

public abstract class CollectionGuiBase<T> implements InventoryProvider {
  public static DatabaseManager databaseManager;
  public static InventoryManager inventoryManager;

  private SmartInventory inventory;

  protected Collection<T> collection;

  protected CollectionGuiBase(Collection<T> collection, String title) {
    this.collection = collection;
    this.inventory = SmartInventory.builder()
        .id(title)
        .provider(this)
        .manager(inventoryManager)
        .size(5, 9)
        .title(title)
        .build();
  }

  public Inventory show(Player player) {
    return inventory.open(player);
  }
  public void close(Player player) {
    inventory.close(player);
  }

  public SmartInventory getInventory() {
    return inventory;
  }


  protected abstract void customInit(Player player, InventoryContents contents);

  protected abstract ClickableItem itemProvider(Player player, T obj);


  @Override
  public void init(Player player, InventoryContents contents) {
    contents.fillRow(0, ClickableItem.empty(Util.item(Material.BLACK_STAINED_GLASS_PANE, " ")));
    contents.fillRow(4, ClickableItem.empty(Util.item(Material.BLACK_STAINED_GLASS_PANE, " ")));
    customInit(player, contents);

    Pagination pagination = contents.pagination();
    ClickableItem[] items = new ClickableItem[collection.size()];
    int i = 0;
    for (T obj : collection) {
      items[i] = itemProvider(player, obj);
      i++;
    }
    pagination.setItems(items);
    pagination.setItemsPerPage(27);
    pagination.addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0));

    contents.set(4, 3, ClickableItem.of(Util.item(Material.ARROW, "Previous page"),
        e -> getInventory().open(player, pagination.previous().getPage())));
    contents.set(4, 5, ClickableItem.of(Util.item(Material.ARROW, "Next page"),
        e -> getInventory().open(player, pagination.next().getPage())));
  }

  @Override
  public void update(Player player, InventoryContents contents) {

  }
}

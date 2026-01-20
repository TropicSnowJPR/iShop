package com.minedhype.ishop.inventories;

import java.util.Optional;
import com.minedhype.ishop.iShop;
import com.minedhype.ishop.Messages;
import com.minedhype.ishop.Shop;
import com.minedhype.ishop.RowStore;
import com.minedhype.ishop.Utils;
import com.minedhype.ishop.gui.GUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public class InvShop extends GUI {
	public static boolean listAllShops = iShop.config.getBoolean("publicShopListCommand");
	private final Shop shop;

	private static String getShopName(Shop shop) {
		String shopId = String.valueOf(shop.shopId());
		if(shop.isAdmin())
			return Messages.SHOP_TITLE_ADMIN_SHOP.toString().replaceAll("%id", shopId);
		String msg = Messages.SHOP_TITLE_NORMAL_SHOP.toString();
		OfflinePlayer pl = Bukkit.getOfflinePlayer(shop.getOwner());
		if(pl == null || pl.getName() == null)
			return msg.replaceAll("%player%", "<unknown>").replaceAll("%id", shopId);
		return msg.replaceAll("%player%", pl.getName()).replaceAll("%id", shopId);
	}

	public InvShop(Shop shop) {
		super(54, getShopName(shop));
		this.shop = shop;
		for(int x=0; x<9; x++) {
			for(int y=0; y<6; y++) {
				if(x == 1) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + Messages.SHOP_TITLE_SELL.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemOut());
					}
				} else if(x == 2) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN + Messages.SHOP_TITLE_SELL2.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemOut2());
					}
				} else if(x == 5) {
					if(y == 0)
						placeItem(x, GUI.createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + Messages.SHOP_TITLE_BUY.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemIn());
					}
				} else if(x == 6) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + Messages.SHOP_TITLE_BUY2.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemIn2());
					}
				} else if(x == 8 && y == 0) {
					if(listAllShops) {
						placeItem(y*9+x, GUI.createItem(Material.END_CRYSTAL, Messages.SHOP_LIST_ALL.toString()), p -> {
							p.closeInventory();
							p.performCommand("shop shops");
						});
					} else
						placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
				} else if(x == 8 && y >= 1) {
					Optional<RowStore> row = shop.getRow(y-1);
					if(row.isPresent()) {
						final int index = y - 1;
						if(row.get().getItemOut().isSimilar(row.get().getItemOut2()) && !Utils.hasDoubleItemStock(shop, row.get().getItemOut(), row.get().getItemOut2()))
							placeItem(y * 9 + x, GUI.createItem(Material.RED_DYE, Messages.SHOP_NO_STOCK_BUTTON.toString()), p -> {
								p.closeInventory();
								shop.buy(p, index);
							});
						else if(!Utils.hasStock(shop, row.get().getItemOut()))
							placeItem(y * 9 + x, GUI.createItem(Material.RED_DYE, Messages.SHOP_NO_STOCK_BUTTON.toString()), p -> {
								p.closeInventory();
								shop.buy(p, index);
							});
						else if(!Utils.hasStock(shop, row.get().getItemOut2()))
							placeItem(y * 9 + x, GUI.createItem(Material.RED_DYE, Messages.SHOP_NO_STOCK_BUTTON.toString()), p -> {
								p.closeInventory();
								shop.buy(p, index);
							});
						else {
							placeItem(y * 9 + x, GUI.createItem(Material.LIME_DYE, ChatColor.BOLD + Messages.SHOP_TITLE_BUYACTION.toString()), p -> {
								p.closeInventory();
								shop.buy(p, index);
							});
						}
					} else
						placeItem(y*9+x, GUI.createItem(Material.GRAY_DYE, ""));
				} else
					placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
			}
		}
	}
	
	@Override
	public void onClick(InventoryClickEvent e) {
		super.onClick(e);
		
		// Verify shop still exists
		Optional<Shop> shopCheck = Shop.getShopById(shop.shopId());
		if(!shopCheck.isPresent()) {
			e.setCancelled(true);
			e.getWhoClicked().closeInventory();
			e.getWhoClicked().sendMessage("§cThis shop has been deleted.");
			return;
		}
		
		if(!iShop.config.getBoolean("enableBulkBuy", true))
			return;
		
		if(e.getClick() != ClickType.RIGHT && e.getClick() != ClickType.SHIFT_RIGHT)
			return;
		
		int slot = e.getRawSlot();
		int x = slot % 9;
		int y = slot / 9;
		
		if(y >= 1 && y <= 5 && x == 8) {
			int rowIndex = y - 1;
			Optional<RowStore> row = shop.getRow(rowIndex);
			if(row.isPresent() && e.getWhoClicked() instanceof Player) {
				Player player = (Player) e.getWhoClicked();
				player.closeInventory();
				Bukkit.getScheduler().runTask(iShop.getPlugin(), () -> {
					InvBulkBuy bulkBuyGUI = new InvBulkBuy(shop, rowIndex);
					bulkBuyGUI.open(player);
				});
			}
		}
	}
}

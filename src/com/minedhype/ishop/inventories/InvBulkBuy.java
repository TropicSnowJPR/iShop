package com.minedhype.ishop.inventories;

import com.minedhype.ishop.RowStore;
import com.minedhype.ishop.Shop;
import com.minedhype.ishop.Utils;
import com.minedhype.ishop.gui.GUI;
import com.minedhype.ishop.iShop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InvBulkBuy extends GUI {
	private final Shop shop;
	private final int rowIndex;

	public InvBulkBuy(Shop shop, int rowIndex) {
		super(27, ChatColor.translateAlternateColorCodes('&', iShop.config.getString("bulkBuyTitle", "Bulk Purchase")));
		this.shop = shop;
		this.rowIndex = rowIndex;
		setupItems();
	}

	private void setupItems() {
		Optional<RowStore> row = shop.getRow(rowIndex);
		if(!row.isPresent())
			return;

		String selectMsg = ChatColor.translateAlternateColorCodes('&', iShop.config.getString("bulkBuySelect", "&aSelect quantity:"));
		
		int[] multipliers = {1, 8, 16, 32, 64};
		int[] slots = {10, 12, 14, 16, 22};

		for(int i = 0; i < multipliers.length; i++) {
			final int multiplier = multipliers[i];
			ItemStack displayItem = new ItemStack(Material.LIME_DYE, multiplier);
			ItemMeta meta = displayItem.getItemMeta();
			meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + multiplier + "x");
			
			List<String> lore = new ArrayList<>();
			lore.add(selectMsg);
			lore.add("");
			lore.add(ChatColor.GRAY + "You will receive:");
			if(!row.get().getItemOut().getType().isAir())
				lore.add(ChatColor.GREEN + " " + (row.get().getItemOut().getAmount() * multiplier) + "x " + row.get().getItemOut().getType().name());
			if(!row.get().getItemOut2().getType().isAir())
				lore.add(ChatColor.GREEN + " " + (row.get().getItemOut2().getAmount() * multiplier) + "x " + row.get().getItemOut2().getType().name());
			lore.add("");
			lore.add(ChatColor.GRAY + "You will pay:");
			if(!row.get().getItemIn().getType().isAir())
				lore.add(ChatColor.RED + " " + (row.get().getItemIn().getAmount() * multiplier) + "x " + row.get().getItemIn().getType().name());
			if(!row.get().getItemIn2().getType().isAir())
				lore.add(ChatColor.RED + " " + (row.get().getItemIn2().getAmount() * multiplier) + "x " + row.get().getItemIn2().getType().name());
			
			meta.setLore(lore);
			displayItem.setItemMeta(meta);
			
			placeItem(slots[i], displayItem, p -> {
				p.closeInventory();
				executeBulkBuy(p, multiplier);
			});
		}

		for(int i = 0; i < 27; i++) {
			if(getItem(i) == null) {
				placeItem(i, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
			}
		}
	}

	private void executeBulkBuy(Player player, int multiplier) {
		Optional<RowStore> row = shop.getRow(rowIndex);
		if(!row.isPresent()) {
			player.sendMessage(ChatColor.RED + "This trade no longer exists!");
			return;
		}

		for(int i = 0; i < multiplier; i++) {
			if(row.get().getItemIn().isSimilar(row.get().getItemIn2()) && !Utils.hasDoubleItemStock(player, row.get().getItemIn(), row.get().getItemIn2())) {
				player.sendMessage(ChatColor.RED + "You don't have enough items to complete all trades! Completed " + i + " trades.");
				return;
			} else if(!Utils.hasStock(player, row.get().getItemIn()) || !Utils.hasStock(player, row.get().getItemIn2())) {
				player.sendMessage(ChatColor.RED + "You don't have enough items to complete all trades! Completed " + i + " trades.");
				return;
			}

			if(row.get().getItemOut().isSimilar(row.get().getItemOut2()) && !Utils.hasDoubleItemStock(shop, row.get().getItemOut(), row.get().getItemOut2())) {
				player.sendMessage(ChatColor.RED + "Shop ran out of stock! Completed " + i + " trades.");
				return;
			} else if(!Utils.hasStock(shop, row.get().getItemOut()) || !Utils.hasStock(shop, row.get().getItemOut2())) {
				player.sendMessage(ChatColor.RED + "Shop ran out of stock! Completed " + i + " trades.");
				return;
			}

			int emptySlots = 0;
			for(ItemStack item : player.getInventory().getStorageContents())
				if(item == null || item.getType().isAir())
					emptySlots++;

			if(!row.get().getItemOut().getType().isAir() && !row.get().getItemOut2().getType().isAir()) {
				if(emptySlots <= 1) {
					player.sendMessage(ChatColor.RED + "Your inventory is full! Completed " + i + " trades.");
					return;
				}
			} else {
				if(emptySlots < 1) {
					player.sendMessage(ChatColor.RED + "Your inventory is full! Completed " + i + " trades.");
					return;
				}
			}

			Bukkit.getScheduler().runTask(iShop.getPlugin(), () -> shop.buy(player, rowIndex));
			
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		player.sendMessage(ChatColor.GREEN + "Successfully completed " + multiplier + " trades!");
	}
}

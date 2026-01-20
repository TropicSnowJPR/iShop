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
	private static final long TRADE_DELAY_TICKS = 3L;
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
		
		RowStore rowData = row.get();
		
		// Validate upfront before executing any trades
		// Check player has enough items
		int requiredIn = rowData.getItemIn().getAmount() * multiplier;
		int requiredIn2 = rowData.getItemIn2().getAmount() * multiplier;
		if(!hasEnoughItems(player, rowData.getItemIn(), requiredIn) || 
		   !hasEnoughItems(player, rowData.getItemIn2(), requiredIn2)) {
			player.sendMessage(ChatColor.RED + "You don't have enough items for " + multiplier + " trades!");
			return;
		}
		
		// Check shop has enough stock
		int requiredOut = rowData.getItemOut().getAmount() * multiplier;
		int requiredOut2 = rowData.getItemOut2().getAmount() * multiplier;
		if(!hasEnoughItems(shop, rowData.getItemOut(), requiredOut) || 
		   !hasEnoughItems(shop, rowData.getItemOut2(), requiredOut2)) {
			player.sendMessage(ChatColor.RED + "Shop doesn't have enough stock for " + multiplier + " trades!");
			return;
		}
		
		// Check player has enough inventory space
		int emptySlots = 0;
		for(ItemStack item : player.getInventory().getStorageContents())
			if(item == null || item.getType().isAir())
				emptySlots++;
		
		int requiredSlots = 0;
		if(!rowData.getItemOut().getType().isAir()) requiredSlots++;
		if(!rowData.getItemOut2().getType().isAir()) requiredSlots++;
		
		if(emptySlots < requiredSlots) {
			player.sendMessage(ChatColor.RED + "Your inventory is full! Need at least " + requiredSlots + " empty slots.");
			return;
		}
		
		// Execute all trades synchronously to avoid race conditions
		for(int i = 0; i < multiplier; i++) {
			shop.buy(player, rowIndex);
		}
		
		player.sendMessage(ChatColor.GREEN + "Successfully completed " + multiplier + " trades!");
	}
	
	private boolean hasEnoughItems(Object source, ItemStack item, int required) {
		if(item.getType().isAir() || required == 0)
			return true;
		
		if(source instanceof Player) {
			Player p = (Player) source;
			int count = 0;
			for(ItemStack invItem : p.getInventory().getContents()) {
				if(invItem != null && invItem.isSimilar(item))
					count += invItem.getAmount();
			}
			return count >= required;
		} else if(source instanceof Shop) {
			return Utils.hasStock((Shop) source, item);
		}
		return false;
	}
}

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
		if(!hasEnoughPlayerItems(player, rowData.getItemIn(), multiplier) || 
		   !hasEnoughPlayerItems(player, rowData.getItemIn2(), multiplier)) {
			player.sendMessage(ChatColor.RED + "You don't have enough items for " + multiplier + " trades!");
			return;
		}
		
		// Check shop has enough stock
		if(!hasEnoughShopStock(shop, rowData.getItemOut(), multiplier) || 
		   !hasEnoughShopStock(shop, rowData.getItemOut2(), multiplier)) {
			player.sendMessage(ChatColor.RED + "Shop doesn't have enough stock for " + multiplier + " trades!");
			return;
		}
		
		// Check player has enough inventory space (accounting for stackable items)
		if(!hasEnoughInventorySpace(player, rowData, multiplier)) {
			player.sendMessage(ChatColor.RED + "Not enough inventory space for " + multiplier + " trades!");
			return;
		}
		
		// Execute all trades synchronously to avoid race conditions
		for(int i = 0; i < multiplier; i++) {
			shop.buy(player, rowIndex);
		}
		
		player.sendMessage(ChatColor.GREEN + "Successfully completed " + multiplier + " trades!");
	}
	
	private boolean hasEnoughPlayerItems(Player player, ItemStack item, int multiplier) {
		if(item.getType().isAir() || item.getAmount() == 0)
			return true;
		
		int required = item.getAmount() * multiplier;
		ItemStack checkItem = item.clone();
		checkItem.setAmount(required);
		
		return Utils.hasStock(player, checkItem);
	}
	
	private boolean hasEnoughShopStock(Shop shop, ItemStack item, int multiplier) {
		if(shop.isAdmin() || item.getType().isAir() || item.getAmount() == 0)
			return true;
		
		int required = item.getAmount() * multiplier;
		ItemStack checkItem = item.clone();
		checkItem.setAmount(required);
		
		return Utils.hasStock(shop, checkItem);
	}
	
	private boolean hasEnoughInventorySpace(Player player, RowStore rowData, int multiplier) {
		// Calculate how many items will be received
		int itemsToReceive = 0;
		if(!rowData.getItemOut().getType().isAir()) itemsToReceive++;
		if(!rowData.getItemOut2().getType().isAir()) itemsToReceive++;
		
		if(itemsToReceive == 0)
			return true;
		
		// Count actual space considering stackable items
		int availableSpace = 0;
		ItemStack[] contents = player.getInventory().getStorageContents();
		
		// Count empty slots
		for(ItemStack item : contents) {
			if(item == null || item.getType().isAir())
				availableSpace++;
		}
		
		// Count partial stacks that can accept more of the same item
		if(!rowData.getItemOut().getType().isAir()) {
			int outAmount = rowData.getItemOut().getAmount() * multiplier;
			for(ItemStack item : contents) {
				if(item != null && item.isSimilar(rowData.getItemOut())) {
					int canFit = item.getMaxStackSize() - item.getAmount();
					if(canFit > 0) {
						outAmount -= canFit;
						if(outAmount <= 0) break;
					}
				}
			}
			// If still need space after merging with existing stacks
			if(outAmount > 0) {
				int slotsNeeded = (int) Math.ceil((double) outAmount / rowData.getItemOut().getMaxStackSize());
				if(availableSpace < slotsNeeded)
					return false;
				availableSpace -= slotsNeeded;
			}
		}
		
		if(!rowData.getItemOut2().getType().isAir()) {
			int out2Amount = rowData.getItemOut2().getAmount() * multiplier;
			for(ItemStack item : contents) {
				if(item != null && item.isSimilar(rowData.getItemOut2())) {
					int canFit = item.getMaxStackSize() - item.getAmount();
					if(canFit > 0) {
						out2Amount -= canFit;
						if(out2Amount <= 0) break;
					}
				}
			}
			// If still need space after merging with existing stacks
			if(out2Amount > 0) {
				int slotsNeeded = (int) Math.ceil((double) out2Amount / rowData.getItemOut2().getMaxStackSize());
				if(availableSpace < slotsNeeded)
					return false;
			}
		}
		
		return true;
	}
}

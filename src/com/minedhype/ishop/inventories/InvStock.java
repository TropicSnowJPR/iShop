package com.minedhype.ishop.inventories;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.minedhype.ishop.iShop;
import com.minedhype.ishop.Messages;
import com.minedhype.ishop.Permission;
import com.minedhype.ishop.Shop;
import com.minedhype.ishop.ShopMember;
import com.minedhype.ishop.StockShop;
import com.minedhype.ishop.gui.GUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

public class InvStock extends GUI {

	public static final HashMap<Player, Integer> inShopInv = new HashMap<>();
	// Track which player has which shop's stock inventory open
	protected static final ConcurrentHashMap<Integer, UUID> stockAccessLock = new ConcurrentHashMap<>();
	private static final List<InvStock> inventories = new ArrayList<>();
	private final ItemStack airItem = new ItemStack(Material.AIR, 0);
	protected int shopId;
	private int pag;
	private int stockPages;
	private Player player;
	
	private InvStock(int shopId) {
		super(54, ChatColor.GREEN + "Shop #" + shopId + " Stock");
		inventories.add(this);
		this.shopId = shopId;
		this.pag = 0;
	}
	
	public static InvStock getInvStock(int shopId, Player player) {
		// Check if stock locking is enabled
		boolean lockingEnabled = iShop.config.getBoolean("enableStockLocking", true);
		
		if (lockingEnabled) {
			UUID playerId = player.getUniqueId();
			
			// Atomically acquire or validate the lock using putIfAbsent / replace
			for (;;) {
				// Try to acquire the lock if it's free
				UUID currentUser = stockAccessLock.putIfAbsent(shopId, playerId);
				
				// Lock was free or already held by this player
				if (currentUser == null || currentUser.equals(playerId)) {
					break;
				}
				
				// Lock is held by another player
				Player otherPlayer = Bukkit.getPlayer(currentUser);
				
				if (otherPlayer != null && otherPlayer.isOnline()) {
					// Stock inventory is actually in use
					if (iShop.config.getBoolean("showLockMessages", true)) {
						player.sendMessage(ChatColor.RED + otherPlayer.getName() + " is currently managing this shop's stock inventory.");
					}
					return null;
				}
				
				// Stale lock (player logged out or closed inventory) - try to replace it atomically
				boolean replaced = stockAccessLock.replace(shopId, currentUser, playerId);
				if (replaced) {
					break;
				}
				
				// Another thread modified the lock between our read and replace; retry
			}
		}
		
		return inventories.parallelStream().filter(inv -> inv.shopId == shopId).findFirst().orElse(new InvStock(shopId));
	}
	
	// Keep old method for backwards compatibility but deprecate it
	@Deprecated
	public static InvStock getInvStock(int shopId) {
		return inventories.parallelStream().filter(inv -> inv.shopId == shopId).findFirst().orElse(new InvStock(shopId));
	}
	
	@Override
	public void onClick(InventoryClickEvent event) {
		super.onClick(event);
		
		Player player = (Player) event.getWhoClicked();
		
		// Check if manager is trying to remove items
		if(event.getRawSlot() < 45 && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			Optional<Shop> shop = Shop.getShopById(this.shopId);
			if(shop.isPresent()) {
				ShopMember.MemberRole role = shop.get().getMemberRole(player.getUniqueId());
				
				// If manager, only allow adding items, not removing
				if(role == ShopMember.MemberRole.MANAGER) {
					// Check if trying to take an item from stock
					if(event.getCurrentItem() != null && !event.getCurrentItem().getType().equals(Material.AIR)) {
						// Trying to take item - deny
						event.setCancelled(true);
						player.sendMessage(Messages.SHOP_MANAGER_STOCK_ONLY.toString());
						return;
					}
				}
			}
		}
		
		if(event.getRawSlot() >= 45 && event.getRawSlot() < 54)
			return;
		if(event.getRawSlot() >= 54 && !player.hasPermission(Permission.SHOP_ADMIN.toString())) {
			if(InvCreateRow.strictStock) {
				ItemStack item = event.getCurrentItem();
				ItemStack item2 = event.getCursor();
				Optional<Shop> shop = Shop.getShopById(shopId);
				if(shop.isPresent() && (Shop.strictStockShopCheck(item, shop.get().getOwner()) || Shop.strictStockShopCheck(item2, shop.get().getOwner())))
					return;
			}
			if(InvCreateRow.itemsDisabled) {
				ItemStack item = event.getCurrentItem();
				ItemStack item2 = event.getCursor();
				for(String itemsList:InvCreateRow.disabledItemList) {
					Material disabledItemsList = Material.matchMaterial(itemsList);
					if(item == null)
						item = airItem;
					if(item2 == null)
						item2 = airItem;
					if(disabledItemsList != null) {
						if(!item.isSimilar(airItem)) {
							if(item.getType().equals(disabledItemsList))
								return;
							if(item.getType().toString().contains("SHULKER_BOX") && item.getItemMeta() instanceof BlockStateMeta) {
								BlockStateMeta itemMeta1 = (BlockStateMeta) item.getItemMeta();
								ShulkerBox shulkerBox1 = (ShulkerBox) itemMeta1.getBlockState();
								if(shulkerBox1.getInventory().contains(disabledItemsList))
									return;
							} else if(item.getType().equals(Material.BUNDLE)) {
								BundleMeta bundleIn1 = (BundleMeta) item.getItemMeta();
								if(bundleIn1.hasItems()) {
									ItemStack itemDisabledOut = new ItemStack(disabledItemsList);
									List<ItemStack> bundleIn1Items = bundleIn1.getItems();
									for(ItemStack bundleList : bundleIn1Items)
										if(bundleList.isSimilar(itemDisabledOut))
											return;
								}
							}
						}
						if(!item2.isSimilar(airItem)) {
							if(item2.getType().equals(disabledItemsList))
								return;
							if(item2.getType().toString().contains("SHULKER_BOX") && item2.getItemMeta() instanceof BlockStateMeta) {
								BlockStateMeta itemMeta2 = (BlockStateMeta) item2.getItemMeta();
								ShulkerBox shulkerBox2 = (ShulkerBox) itemMeta2.getBlockState();
								if(shulkerBox2.getInventory().contains(disabledItemsList))
									return;
							} else if(item2.getType().equals(Material.BUNDLE)) {
								BundleMeta bundleIn2 = (BundleMeta) item2.getItemMeta();
								if(bundleIn2.hasItems()) {
									ItemStack itemDisabledOut = new ItemStack(disabledItemsList);
									List<ItemStack> bundleIn2Items = bundleIn2.getItems();
									for(ItemStack bundleList : bundleIn2Items)
										if(bundleList.isSimilar(itemDisabledOut))
											return;
								}
							}
						}
					}
				}
			}
		}
		event.setCancelled(false);
	}
	
	public void refreshItems() {
		Optional<StockShop> stockOpt = StockShop.getStockShopByShopId(shopId, pag);
		StockShop stock;
		stock = stockOpt.orElseGet(() -> new StockShop(shopId, pag));
		Inventory inv = stock.getInventory();
		for(int i=0; i<45; i++)
			placeItem(i, inv.getItem(i));
		for(int i=45; i<54; i++) {
			if(i == 46 && pag > 4 && stockPages >= 10)
				placeItem(i, GUI.createItem(Material.SPECTRAL_ARROW, Messages.SHOP_PAGE_SKIPPREV.toString()), p -> openPage(p, pag-5));
			else if(i == 47 && pag > 0)
				placeItem(i, GUI.createItem(Material.ARROW, Messages.SHOP_PAGE + " " + (pag)), p -> openPage(p, pag-1));
			else if(i == 51 && pag < stockPages-1)
				placeItem(i, GUI.createItem(Material.ARROW, Messages.SHOP_PAGE + " " + (pag+2)), p -> openPage(p, pag+1));
			else if(i == 52 && pag < stockPages-5 && stockPages >= 10)
				placeItem(i, GUI.createItem(Material.SPECTRAL_ARROW, Messages.SHOP_PAGE_SKIPAHEAD.toString()), p -> openPage(p, pag+5));
			else
				placeItem(i, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
		}
	}
	
	private void openPage(Player player, int pag) {
		for(int i=45; i<54; i++)
			placeItem(i, new ItemStack(Material.AIR));
		player.closeInventory();
		this.pag = pag;
		this.open(player);
	}
	
	@Override
	public void open(Player player) {
		this.player = player;
		refreshItems();
		super.open(player);
	}
	
	@Override
	public void onClose(InventoryCloseEvent event) {
		super.onClose(event);
		
		Inventory inventory = event.getInventory();
		Optional<StockShop> stock = StockShop.getStockShopByShopId(shopId, pag);
		if(!stock.isPresent())
			return;
		stock.get().setInventory(inventory);
		
		// Save inventory changes
		refreshItems();
		
		// Save stock data immediately (don't wait for auto-save)
		Bukkit.getScheduler().runTaskAsynchronously(iShop.getPlugin(), () -> {
			for(int i = 0; i < stockPages; i++) {
				Optional<StockShop> stockToSave = StockShop.getStockShopByShopId(this.shopId, i);
				if(stockToSave.isPresent()) {
					stockToSave.get().saveStockData();
				}
			}
		});
		
		// Release stock access lock
		Player player = (Player) event.getWhoClicked();
		stockAccessLock.remove(this.shopId, player.getUniqueId());
		
		// Remove from tracking map
		inShopInv.remove(player);
	}
	
	public void setPag(int pag) {
		this.pag = pag;
	}
	public void setMaxPages(int maxPages) {
		this.stockPages = maxPages;
	}
}

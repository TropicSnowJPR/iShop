package com.minedhype.ishop.inventories;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import com.minedhype.ishop.iShop;
import com.minedhype.ishop.Messages;
import com.minedhype.ishop.Permission;
import com.minedhype.ishop.RowStore;
import com.minedhype.ishop.Shop;
import com.minedhype.ishop.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import com.minedhype.ishop.gui.GUI;

public class InvAdminShop extends GUI {
	public static boolean remoteManage = iShop.config.getBoolean("remoteManage");
	public static boolean stockGUIShop = iShop.config.getBoolean("enableStockAccessFromShopGUI");
	public static boolean stockCommandEnabled = iShop.config.getBoolean("enableStockCommand");
	public static boolean usePerms = iShop.config.getBoolean("usePermissions");
	public static int maxPages = iShop.config.getInt("stockPages");
	public static int permissionMax = iShop.config.getInt("maxStockPages");
	// Track which player is editing which shop
	protected static final ConcurrentHashMap<Integer, UUID> editLocks = new ConcurrentHashMap<>();
	private Shop shop;

	public Shop getShop() {
		return shop;
	}

	public InvAdminShop(Shop shop, Player player) {
		super(54, getShopName(shop));
		
		// Check if edit locking is enabled
		boolean lockingEnabled = iShop.config.getBoolean("enableEditLocking", true);
		
		if(lockingEnabled) {
			final int shopId = shop.shopId();
			final UUID playerId = player.getUniqueId();
			final boolean showLockMessages = iShop.config.getBoolean("showLockMessages", true);
			final boolean[] lockDenied = new boolean[] { false };
			
			// Atomically check and acquire/edit lock for this shop
			editLocks.compute(shopId, (id, currentEditor) -> {
				// No current editor or same player re-opening: acquire or retain lock
				if (currentEditor == null || currentEditor.equals(playerId)) {
					return playerId;
				}
				
				// Different editor currently recorded
				Player otherEditor = Bukkit.getPlayer(currentEditor);
				
				if (otherEditor != null && otherEditor.isOnline()) {
					// Shop is actually being edited by another online player
					if (showLockMessages) {
						player.sendMessage(ChatColor.RED + otherEditor.getName() + " is currently editing this shop.");
					}
					player.closeInventory();
					InvAdminShop.this.shop = null;
					lockDenied[0] = true;
					// Keep existing lock with the other editor
					return currentEditor;
				}
				
				// Other player logged out or is offline, treat lock as stale and transfer it
				return playerId;
			});
			
			if (lockDenied[0]) {
				return;
			}
		}
		
		this.shop = shop;
		updateItems(player);
	}

	private static String getShopName(Shop shop) {
		String shopId = String.valueOf(shop.shopId());
		if(shop.isAdmin())
			return Messages.SHOP_TITLE_ADMIN_SHOP.toString().replaceAll("%id", shopId);
		String msg = Messages.SHOP_TITLE_NORMAL_SHOP.toString().replaceAll("%id", shopId);
		OfflinePlayer pl = Bukkit.getOfflinePlayer(shop.getOwner());
		if(pl == null || pl.getName() == null)
			return msg.replaceAll("%player%", "<unknown>").replaceAll("%id", shopId);
		return msg.replaceAll("%player%", pl.getName()).replaceAll("%id", shopId);
	}

	private void updateItems(Player player) {
		// Check if player can edit trades
		boolean canEdit = shop.canEditTrades(player.getUniqueId()) || player.hasPermission(Permission.SHOP_ADMIN.toString());
		
		for(int x=0; x<9; x++) {
			for(int y=0; y<6; y++) {
				if(x == 1) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN+ Messages.SHOP_TITLE_SELL.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemOut());
					}
				} else if(x == 2) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.GREEN_STAINED_GLASS_PANE, ChatColor.GREEN+ Messages.SHOP_TITLE_SELL2.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemOut2());
					}
				} else if(x == 4) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED+ Messages.SHOP_TITLE_BUY.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemIn());
					}
				} else if(x == 5) {
					if(y == 0)
						placeItem(y*9+x, GUI.createItem(Material.RED_STAINED_GLASS_PANE, ChatColor.RED+ Messages.SHOP_TITLE_BUY2.toString()));
					else {
						Optional<RowStore> row = shop.getRow(y-1);
						if(row.isPresent())
							placeItem(y*9+x, row.get().getItemIn2());
					}
				} else if(x == 7 && y > 0) {
					Optional<RowStore> row = shop.getRow(y-1);
					final int index = y-1;
					if(canEdit) {
						if(row.isPresent()) {
							placeItem(y*9+x, GUI.createItem(Material.TNT, ChatColor.BOLD+ Messages.SHOP_TITLE_DELETE.toString()), p -> {
								shop.delete(index);
								InvAdminShop inv = new InvAdminShop(shop, p.getPlayer());
								inv.open(p);
							});
						} else {
							placeItem(y*9+x, GUI.createItem(Material.LIME_DYE, ChatColor.BOLD+ Messages.SHOP_TITLE_CREATE.toString()), p -> {
								InvCreateRow inv = new InvCreateRow(shop, index);
								inv.open(p);
							});
						}
					} else {
						// Read-only mode - show gray glass pane
						placeItem(y*9+x, GUI.createItem(Material.GRAY_STAINED_GLASS_PANE, ""));
					}
				} else if(x == 7 && y == 0) {
					if(stockGUIShop && !shop.isAdmin() && shop.canManageStock(player.getUniqueId())) {
						placeItem(y * 9 + x, GUI.createItem(Material.CHEST, Messages.SHOP_TITLE_STOCK.toString()), p -> {
							p.closeInventory();
							InvStock inv = InvStock.getInvStock(shop.shopId(), p);
							if(inv != null) {
								int maxStockPages = maxPages;
								if(usePerms) {
									String permPrefix = Permission.SHOP_STOCK_PREFIX.toString();
									int maxPermPages = InvAdminShop.permissionMax;
									boolean permissionFound = false;
									for(int i=maxPermPages; i>0; i--)
										if(player.hasPermission(permPrefix + i)) {
											maxStockPages = i;
											permissionFound = true;
											break;
										}
									if(!permissionFound)
										maxStockPages = maxPermPages;
								}
								inv.setMaxPages(maxStockPages);
								inv.setPag(0);
								InvStock.inShopInv.put(player, player.getUniqueId());
								inv.open(player);
							}
						});
					} else
						placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
				} else if(x == 8 && y == 0) {
					if(InvShop.listAllShops && Shop.showOwnedShops && remoteManage) {
						placeItem(y*9+x, GUI.createItem(Material.END_CRYSTAL, Messages.SHOP_LIST_ALL.toString()), p -> {
							p.closeInventory();
							p.performCommand("shop shops");
						});
					} else
						placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
				} else if(x == 8 && y >= 1) {
					final Optional<RowStore> row = shop.getRow(y-1);
					if(shop.isAdmin()) {
						if(row.isPresent()) {
							if(row.get().broadcast) {
								placeItem(y * 9 + x, GUI.createItem(Material.REDSTONE_TORCH, Messages.SHOP_TITLE_BROADCAST_ON.toString(), (short) 15), p -> {
									row.get().toggleBroadcast();
									updateItems(player);
								});
							} else {
								placeItem(y * 9 + x, GUI.createItem(Material.LEVER, Messages.SHOP_TITLE_BROADCAST_OFF.toString(), (short) 15), p -> {
									row.get().toggleBroadcast();
									updateItems(player);
								});
							}
						} else
							placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
					} else {
						if(row.isPresent()) {
							if(row.get().getItemOut().isSimilar(row.get().getItemOut2()) && !Utils.hasDoubleItemStock(shop, row.get().getItemOut(), row.get().getItemOut2()))
								placeItem(y*9+x, GUI.createItem(Material.RED_DYE, Messages.SHOP_NO_STOCK_BUTTON.toString()));
							else if(!Utils.hasStock(shop, row.get().getItemOut()))
								placeItem(y*9+x, GUI.createItem(Material.RED_DYE, Messages.SHOP_NO_STOCK_BUTTON.toString()));
							else if(!Utils.hasStock(shop, row.get().getItemOut2()))
								placeItem(y*9+x, GUI.createItem(Material.RED_DYE, Messages.SHOP_NO_STOCK_BUTTON.toString()));
							else
								placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
						} else
							placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
					}
				} else
					placeItem(y*9+x, GUI.createItem(Material.BLACK_STAINED_GLASS_PANE, ""));
			}
		}
	}
	
	@Override
	public void onClose(InventoryCloseEvent event) {
		super.onClose(event);
		
		if(shop != null) {
			// Shop data is saved automatically through the Shop.saveData() mechanism
			
			// Release edit lock
			Player player = (Player) event.getWhoClicked();
			editLocks.remove(shop.shopId(), player.getUniqueId());
		}
	}
}

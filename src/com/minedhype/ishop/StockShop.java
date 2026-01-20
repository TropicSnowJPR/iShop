package com.minedhype.ishop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;

public class StockShop {
	private static final List<StockShop> stocks = new ArrayList<>();
	private final int shopId;
	private final Inventory inventory;
	private final int pag;

	public StockShop(int shopId, int pag) { 
		this(shopId, Bukkit.createInventory(null, 45, ChatColor.GREEN + "Shop #" + shopId + " Stock - Page " + (pag + 1)), pag); 
	}

	public StockShop(int shopId, Inventory inv, int pag) {
		this.shopId = shopId;
		this.inventory = inv;
		this.pag = pag;
		stocks.add(this);
	}

	public static Optional<StockShop> getStockShopByShopId(int shopId, int pag) { return stocks.parallelStream().filter(t -> t.shopId == shopId && t.pag == pag).findFirst(); }
	
	public static void saveData() {
		if(!hasStock())
			return;
		
		Connection conn = iShop.getConnection();
		PreparedStatement stmt = null;
		
		try {
			// Disable auto-commit for transaction
			conn.setAutoCommit(false);
			
			// Delete old data
			stmt = conn.prepareStatement("DELETE FROM shop_stocks;");
			stmt.execute();
			stmt.close();
			
			// Insert new data
			for(StockShop stock : stocks)
				stock.saveStockData();
			
			// Commit transaction - all or nothing
			conn.commit();
			
		} catch (Exception e) {
			e.printStackTrace();
			try {
				// Rollback on error - restore old data
				conn.rollback();
				System.err.println("[iShop] Stock save failed, rolled back transaction");
			} catch (Exception rollbackEx) {
				rollbackEx.printStackTrace();
			}
		} finally {
			try {
				if(stmt != null)
					stmt.close();
				// Re-enable auto-commit
				conn.setAutoCommit(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private static boolean hasStock() { return (int) stocks.parallelStream().filter(stock -> Arrays.asList(stock.getInventory().getContents()).parallelStream().anyMatch(item -> item != null && item.getAmount() > 0)).count() > 0; }
	
	public void saveStockData() {
		PreparedStatement stmt = null;
		try {
			stmt = iShop.getConnection().prepareStatement("INSERT INTO shop_stocks (shop_id, page, items) VALUES (?,?,?);");
			stmt.setInt(1, shopId);
			stmt.setInt(2, pag);
			stmt.setBytes(3,iShop.encodeByte(inventory.getContents()));
			stmt.execute();
		} catch (Exception e) { e.printStackTrace(); }
			finally {
			try {
				if(stmt != null)
					stmt.close();
			} catch (Exception e) { e.printStackTrace(); }
		}
	}

	public Inventory getInventory() {
		return inventory;
	}

	public void setInventory(Inventory inventory) {
		for(int i=0; i<45; i++)
			this.inventory.setItem(i, inventory.getItem(i));
	}
	public int getShopId() {
		return shopId;
	}
}

package com.minedhype.ishop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.bukkit.ChatColor;

public class ShopAnalytics {
	private int shopId;
	private int totalSales;
	private int totalTrades;
	private long lastSaleTimestamp;
	private String popularItem;

	public ShopAnalytics(int shopId) {
		this.shopId = shopId;
		loadAnalytics();
	}

	private void loadAnalytics() {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try {
			stmt = iShop.getConnection().prepareStatement("SELECT * FROM shop_analytics WHERE shop_id = ?;");
			stmt.setInt(1, shopId);
			rs = stmt.executeQuery();
			
			if(rs.next()) {
				this.totalSales = rs.getInt("total_sales");
				this.totalTrades = rs.getInt("total_trades");
				this.lastSaleTimestamp = rs.getLong("last_sale_timestamp");
				this.popularItem = rs.getString("popular_item");
			} else {
				this.totalSales = 0;
				this.totalTrades = 0;
				this.lastSaleTimestamp = 0;
				this.popularItem = "None";
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(rs != null) rs.close();
				if(stmt != null) stmt.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void updateAnalytics(int shopId, String itemSold, int salesAmount) {
		if(!iShop.config.getBoolean("trackShopAnalytics", true))
			return;
		
		PreparedStatement stmt = null;
		
		try {
			// Note: This uses SQLite-specific ON CONFLICT syntax
			// The plugin is designed for SQLite as per existing database implementation
			stmt = iShop.getConnection().prepareStatement(
				"INSERT INTO shop_analytics (shop_id, total_sales, total_trades, last_sale_timestamp, popular_item) " +
				"VALUES (?, ?, 1, ?, ?) " +
				"ON CONFLICT(shop_id) DO UPDATE SET " +
				"total_sales = total_sales + ?, " +
				"total_trades = total_trades + 1, " +
				"last_sale_timestamp = ?, " +
				"popular_item = ?;"
			);
			long timestamp = System.currentTimeMillis() / 1000;
			stmt.setInt(1, shopId);
			stmt.setInt(2, salesAmount);
			stmt.setLong(3, timestamp);
			stmt.setString(4, itemSold);
			stmt.setInt(5, salesAmount);
			stmt.setLong(6, timestamp);
			stmt.setString(7, itemSold);
			stmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(stmt != null)
					stmt.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public String[] getStatsMessages() {
		SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
		String lastSaleStr = lastSaleTimestamp > 0 ? sdf.format(new Date(lastSaleTimestamp * 1000)) : "Never";
		
		String[] messages = new String[5];
		messages[0] = ChatColor.translateAlternateColorCodes('&', 
			iShop.config.getString("shopStatsTitle", "&6=== Shop #%id Statistics ===")
			.replaceAll("%id", String.valueOf(shopId)));
		messages[1] = ChatColor.translateAlternateColorCodes('&',
			iShop.config.getString("shopStatsTotalSales", "&aSales: %sales")
			.replaceAll("%sales", String.valueOf(totalSales)));
		messages[2] = ChatColor.translateAlternateColorCodes('&',
			iShop.config.getString("shopStatsTotalTrades", "&aTrades: %trades")
			.replaceAll("%trades", String.valueOf(totalTrades)));
		messages[3] = ChatColor.translateAlternateColorCodes('&',
			iShop.config.getString("shopStatsPopularItem", "&aPopular: %item")
			.replaceAll("%item", popularItem != null ? popularItem : "None"));
		messages[4] = ChatColor.translateAlternateColorCodes('&',
			iShop.config.getString("shopStatsLastSale", "&aLast sale: %time")
			.replaceAll("%time", lastSaleStr));
		
		return messages;
	}
}

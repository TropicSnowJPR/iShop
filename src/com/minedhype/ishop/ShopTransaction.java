package com.minedhype.ishop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bukkit.entity.Player;

public class ShopTransaction {
	private final int shopId;
	private final String buyerUuid;
	private final String buyerName;
	private final String itemSold;
	private final int itemSoldAmount;
	private final String itemBought;
	private final int itemBoughtAmount;
	private final long timestamp;

	public ShopTransaction(int shopId, String buyerUuid, String buyerName, String itemSold, int itemSoldAmount, String itemBought, int itemBoughtAmount) {
		this.shopId = shopId;
		this.buyerUuid = buyerUuid;
		this.buyerName = buyerName;
		this.itemSold = itemSold;
		this.itemSoldAmount = itemSoldAmount;
		this.itemBought = itemBought;
		this.itemBoughtAmount = itemBoughtAmount;
		this.timestamp = System.currentTimeMillis() / 1000;
	}

	public static void logTransaction(int shopId, Player buyer, String itemSold, int itemSoldAmount, String itemBought, int itemBoughtAmount) {
		if(!iShop.config.getBoolean("logTransactions", true))
			return;
		
		PreparedStatement stmt = null;
		try {
			stmt = iShop.getConnection().prepareStatement("INSERT INTO shop_transactions (shop_id, buyer_uuid, buyer_name, item_sold, item_sold_amount, item_bought, item_bought_amount, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?);");
			stmt.setInt(1, shopId);
			stmt.setString(2, buyer.getUniqueId().toString());
			stmt.setString(3, buyer.getName());
			stmt.setString(4, itemSold);
			stmt.setInt(5, itemSoldAmount);
			stmt.setString(6, itemBought);
			stmt.setInt(7, itemBoughtAmount);
			stmt.setLong(8, System.currentTimeMillis() / 1000);
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

	public static List<String> getTransactions(int shopId, int limit) {
		List<String> transactions = new ArrayList<>();
		PreparedStatement stmt = null;
		ResultSet rs = null;
		
		try {
			stmt = iShop.getConnection().prepareStatement("SELECT * FROM shop_transactions WHERE shop_id = ? ORDER BY timestamp DESC LIMIT ?;");
			stmt.setInt(1, shopId);
			stmt.setInt(2, limit);
			rs = stmt.executeQuery();
			
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
			while(rs.next()) {
				String buyerName = rs.getString("buyer_name");
				String itemSold = rs.getString("item_sold");
				int itemSoldAmount = rs.getInt("item_sold_amount");
				String itemBought = rs.getString("item_bought");
				int itemBoughtAmount = rs.getInt("item_bought_amount");
				long timestamp = rs.getLong("timestamp");
				
				String timeStr = sdf.format(new Date(timestamp * 1000));
				String boughtStr = formatItem(itemBought, itemBoughtAmount);
				String soldStr = formatItem(itemSold, itemSoldAmount);
				
				String message = iShop.config.getString("transactionLog", "&7[%time] %buyer bought %bought for %sold")
					.replaceAll("%time", timeStr)
					.replaceAll("%buyer", buyerName)
					.replaceAll("%bought", boughtStr)
					.replaceAll("%sold", soldStr);
				transactions.add(message);
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
		
		return transactions;
	}

	private static String formatItem(String item, int amount) {
		if(item.equals("empty") || amount == 0)
			return "";
		return amount + "x " + item;
	}
}

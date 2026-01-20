package com.minedhype.ishop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ShopMember {
    private final int shopId;
    private final UUID playerUuid;
    private final MemberRole role;
    private final long addedDate;

    public enum MemberRole {
        OWNER,      // Full control
        CO_OWNER,   // Can edit, manage, add managers
        MANAGER     // Stock refill only, view trades
    }

    public ShopMember(int shopId, UUID playerUuid, MemberRole role, long addedDate) {
        this.shopId = shopId;
        this.playerUuid = playerUuid;
        this.role = role;
        this.addedDate = addedDate;
    }

    public int getShopId() { return shopId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public MemberRole getRole() { return role; }
    public long getAddedDate() { return addedDate; }

    // Save to database
    public void save() {
        PreparedStatement stmt = null;
        try {
            stmt = iShop.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO shop_members (shop_id, player_uuid, role, added_date) VALUES (?, ?, ?, ?);"
            );
            stmt.setInt(1, shopId);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, role.name());
            stmt.setLong(4, addedDate);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(stmt != null) stmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Delete from database
    public void delete() {
        PreparedStatement stmt = null;
        try {
            stmt = iShop.getConnection().prepareStatement(
                "DELETE FROM shop_members WHERE shop_id = ? AND player_uuid = ?;"
            );
            stmt.setInt(1, shopId);
            stmt.setString(2, playerUuid.toString());
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(stmt != null) stmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Load all members for a shop
    public static List<ShopMember> loadMembersForShop(int shopId) {
        List<ShopMember> members = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            stmt = iShop.getConnection().prepareStatement(
                "SELECT player_uuid, role, added_date FROM shop_members WHERE shop_id = ?;"
            );
            stmt.setInt(1, shopId);
            ResultSet rs = stmt.executeQuery();
            
            while(rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                MemberRole role = MemberRole.valueOf(rs.getString("role"));
                long addedDate = rs.getLong("added_date");
                members.add(new ShopMember(shopId, uuid, role, addedDate));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(stmt != null) stmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return members;
    }

    // Delete all members for a shop (when shop is deleted)
    public static void deleteAllMembersForShop(int shopId) {
        PreparedStatement stmt = null;
        try {
            stmt = iShop.getConnection().prepareStatement(
                "DELETE FROM shop_members WHERE shop_id = ?;"
            );
            stmt.setInt(1, shopId);
            stmt.execute();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(stmt != null) stmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

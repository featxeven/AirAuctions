package com.ftxeven.airauctions.util;

import com.ftxeven.airauctions.AirAuctions;
import org.bukkit.inventory.ItemStack;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ItemSerializerUtil {
    private static AirAuctions plugin;

    public static void init(AirAuctions airAuctions) {
        plugin = airAuctions;
    }

    public static byte[] serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                byte[] rawBytes = item.serializeAsBytes();
                gzos.write(rawBytes);
            }
            return baos.toByteArray();

        } catch (IOException e) {
            if (plugin != null) plugin.getLogger().warning("Failed to serialize item: " + e.getMessage());
            return new byte[0];
        }
    }

    public static ItemStack deserialize(byte[] blob) {
        if (blob == null || blob.length == 0) return null;

        try (ByteArrayInputStream bais = new ByteArrayInputStream(blob);
             GZIPInputStream gzis = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            return ItemStack.deserializeBytes(baos.toByteArray());
        } catch (Exception e) {
            if (plugin != null) plugin.getLogger().warning("Deserialization failed! Blob size: " + blob.length);
            return null;
        }
    }
}
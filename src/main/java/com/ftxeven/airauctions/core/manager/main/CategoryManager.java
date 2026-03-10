package com.ftxeven.airauctions.core.manager.main;

import com.ftxeven.airauctions.AirAuctions;
import com.ftxeven.airauctions.core.gui.*;
import com.ftxeven.airauctions.core.gui.GuiDefinition.GuiItem;
import com.ftxeven.airauctions.util.PlaceholderUtil;
import com.ftxeven.airauctions.core.gui.util.GuiItemFinder;
import com.ftxeven.airauctions.core.gui.util.GuiListingUtil;
import com.ftxeven.airauctions.core.gui.util.GuiSlotMapper;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class CategoryManager implements GuiManager.CustomGuiManager {

    private final AirAuctions plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private GuiDefinition definition;
    private final BitSet emptySet = new BitSet();
    public static final ThreadLocal<CategoryHolder> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    public CategoryManager(AirAuctions plugin) {
        this.plugin = plugin;
        loadDefinition();
    }

    public void loadDefinition() {
        File file = new File(plugin.getDataFolder(), "guis/categories.yml");
        if (!file.exists()) plugin.saveResource("guis/categories.yml", false);

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("categories");
        if (section == null) section = cfg;

        Map<String, GuiItem> items = new LinkedHashMap<>();
        loadItems(section.getConfigurationSection("buttons"), items);
        loadItems(section.getConfigurationSection("items"), items);

        this.definition = new GuiDefinition(section.getString("title", "Categories"), section.getInt("rows", 6), items, section);
    }

    private void loadItems(ConfigurationSection sec, Map<String, GuiItem> items) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            ConfigurationSection sub = sec.getConfigurationSection(k);
            if (sub != null) items.putIfAbsent(k, GuiItem.fromSection(k, sub));
        }
    }

    @Override
    public Inventory build(Player viewer, Map<String, String> ph) {
        CategoryHolder holder = new CategoryHolder(ph);

        CONSTRUCTION_CONTEXT.set(holder);
        try {
            Map<String, String> displayPh = holder.asContext();

            Map<String, String> visualPh = new HashMap<>(displayPh);
            visualPh.put("filter", plugin.filters().getDisplayName(holder.filter()));
            visualPh.put("sort", plugin.config().getSortingDisplayName(holder.sort()));

            String title = PlaceholderUtil.apply(viewer, definition.title(), visualPh);
            Inventory inv = Bukkit.createInventory(holder, definition.rows() * 9, mm.deserialize("<!italic>" + title));
            holder.setInventory(inv);

            GuiSlotMapper.fill(plugin, inv, definition, viewer, displayPh, Collections.emptyList(),
                    emptySet, 0, null, null, Integer.MAX_VALUE);

            return inv;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player viewer) {
        event.setCancelled(true);
        if (!(event.getInventory().getHolder() instanceof CategoryHolder holder) || event.getCurrentItem() == null) return;

        GuiItem item = GuiItemFinder.find(definition, event.getSlot(), viewer, holder.asContext(), holder);

        if (item != null) {
            GuiListingUtil.handleItemAction(plugin, item, viewer, event.getClick(), holder);
        }
    }

    public boolean isEnabled() {
        return definition != null && definition.config().getBoolean("enabled", true);
    }

    @Override
    public boolean owns(Inventory inv) { return inv != null && inv.getHolder() instanceof CategoryHolder; }

    public static final class CategoryHolder implements PageableHolder {
        private final String page, filter, sort;
        private Inventory inventory;

        public CategoryHolder(Map<String, String> ph) {
            this.page = ph.getOrDefault("page", "1");
            this.filter = ph.getOrDefault("filter", "all");
            this.sort = ph.getOrDefault("sort", "newest-date");
        }

        @Override public int page() { try { return Integer.parseInt(page) - 1; } catch (Exception e) { return 0; } }
        @Override public int totalPages() { return 1; }
        @Override public String filter() { return filter; }
        @Override public String sort() { return sort; }
        @Override public Map<Integer, Integer> displayedListings() { return Collections.emptyMap(); }

        public void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory != null ? inventory : Bukkit.createInventory(this, 9);
        }

        @Override
        public Map<String, String> asContext() {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("filter", filter);
            ctx.put("sort", sort);
            ctx.put("page", String.valueOf(page() + 1));
            ctx.put("pages", "1");
            return ctx;
        }
    }
}
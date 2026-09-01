package com.ftxeven.airauctions.gui.impl;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiSession;
import com.ftxeven.airauctions.core.gui.config.ItemConfig;
import com.ftxeven.airauctions.core.gui.render.RenderEntry;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.ListingGuiManager;
import com.ftxeven.airauctions.gui.action.AmountAction;
import com.ftxeven.airauctions.gui.action.AmountEditor;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.gui.render.FilterOptions;
import com.ftxeven.airauctions.model.ListingType;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DraftGui extends BaseGui {

    public static final String AUCTION_ID = "confirm/auction";
    public static final String BID_ID = "confirm/bid";

    public static final String ATTR_DRAFT = "create-draft";

    private static final String ATTR_PROVIDER = "create-provider";

    private final Messenger messenger;

    public DraftGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis, Messenger messenger) {
        super(services, configs, guis);
        this.messenger = messenger;
    }

    public static String guiIdFor(ListingType type) {
        return type == ListingType.AUCTION ? AUCTION_ID : BID_ID;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        ListingDraft draft = session.attribute(ATTR_DRAFT, ListingDraft.class);
        if (draft == null) {
            return; // opened without a draft attached
        }

        LinkedHashMap<String, String> economyOptions = FilterOptions.economy(configs, false);
        String economyId = resolveSelectedEconomy(session, draft, economyOptions);
        EconomyProvider provider = services.economy().get(economyId).orElse(null);
        session.attribute(ATTR_PROVIDER, provider);
        if (provider == null) {
            return; // every configured provider is disabled
        }

        Map<String, String> placeholders = session.placeholders();
        placeholders.putAll(guis.placeholders().forDraft(viewer, draft, provider));
        placeholders.putAll(priceValidityPlaceholders(viewer, draft, provider));
        writeCycler(session, "filter_ECONOMY", "economy", layout(session).filters(), economyOptions, economyId, null, LayoutConfig.Cycler.Format.FILTER_DEFAULT);

        session.flagResolver(guis.flags().forDraft(viewer, draft.toFlagDraft(provider)));
        session.attribute(AmountAction.ATTR_EDITOR, AmountEditor.price(
                inputContextFor(draft.type()), draft::price, draft::price, () -> provider,
                services.economy(), configs, messenger));
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        ListingDraft draft = session.attribute(ATTR_DRAFT, ListingDraft.class);
        EconomyProvider provider = session.attribute(ATTR_PROVIDER, EconomyProvider.class);
        LayoutConfig.ListingRender listingRender = layout(session).listing();
        if (draft == null || provider == null || listingRender == null) {
            return;
        }

        ItemConfig.Template template = listingRender.forType(null);
        if (template == null) {
            return;
        }

        RenderEntry entry = new RenderEntry(template, draft.asStack(),
                guis.placeholders().forDraft(viewer, draft, provider),
                guis.flags().forDraft(viewer, draft.toFlagDraft(provider)));
        drawEntries(viewer, session, List.of(entry), layout(session).listingSlots());
    }

    // Economy selection

    private String resolveSelectedEconomy(GuiSession session, ListingDraft draft, LinkedHashMap<String, String> options) {
        String value = session.attribute(ATTR_FILTER_ECONOMY, String.class);
        if (value == null) {
            value = draft.economyId();
        }
        if (!options.containsKey(value)) {
            value = options.isEmpty() ? draft.economyId() : options.keySet().iterator().next();
        }
        session.attribute(ATTR_FILTER_ECONOMY, value);
        draft.economyId(value);
        return value;
    }

    // Price validity

    private Map<String, String> priceValidityPlaceholders(Player viewer, ListingDraft draft, EconomyProvider provider) {
        EconomyService economy = services.economy();

        Map<String, String> map = new HashMap<>();
        map.put("valid_min_price", String.valueOf(economy.meetsMinPrice(draft.price())));
        map.put("valid_max_price", String.valueOf(economy.meetsMaxPrice(draft.price())));
        economy.formatInto(map, "min_price", provider.id(), economy.minPrice());
        economy.formatInto(map, "max_price", provider.id(), economy.maxPrice());

        EconomyService.ChargeResult fee = economy.fee(viewer, provider, draft.price());
        economy.formatInto(map, "fee", provider.id(), fee);
        map.put("can_afford_fee", String.valueOf(economy.eligibleForFee(viewer, provider, fee).ok()));

        return map;
    }

    private static String inputContextFor(ListingType type) {
        return type == ListingType.AUCTION ? "price" : "initial-price";
    }
}
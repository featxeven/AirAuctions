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
import com.ftxeven.airauctions.gui.action.BuyAction;
import com.ftxeven.airauctions.gui.config.LayoutConfig;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.util.Messenger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BuyAmountGui extends BaseGui {

    public static final String ID = "confirm/buy_amount";

    private static final String ATTR_PROVIDER = "buy-amount-provider";

    private final Messenger messenger;

    public BuyAmountGui(ServiceManager services, ConfigManager configs, ListingGuiManager guis, Messenger messenger) {
        super(services, configs, guis);
        this.messenger = messenger;
    }

    @Override
    public void prepare(Player viewer, GuiSession session) {
        Listing resolved = resolveTargetListing(viewer, session);
        Listing.Auction auction = resolved instanceof Listing.Auction a ? a : null;
        EconomyProvider provider = auction != null ? services.economy().get(auction.info().economy()).orElse(null) : null;

        session.attribute(ATTR_PROVIDER, provider);
        session.flagResolver(guis.flags().forBuyAmount(viewer, auction, () -> buyAmount(session, auction)));
        session.attribute(AmountAction.ATTR_EDITOR, AmountEditor.quantity(
                () -> buyAmount(session, auction),
                amount -> session.attribute(BuyAction.ATTR_BUY_AMOUNT, amount),
                () -> auction != null ? auction.remainingAmount() : 0,
                () -> auction != null,
                configs, messenger));

        if (auction == null || provider == null) {
            return;
        }

        int clamped = clampedBuyAmount(session, auction);
        session.attribute(BuyAction.ATTR_BUY_AMOUNT, clamped);
        session.placeholders().putAll(guis.placeholders().forBuyAmount(viewer, auction, provider, clamped));
    }

    @Override
    public void render(Player viewer, GuiSession session) {
        Listing.Auction auction = targetListing(session) instanceof Listing.Auction a ? a : null;
        EconomyProvider provider = session.attribute(ATTR_PROVIDER, EconomyProvider.class);
        LayoutConfig.ListingRender listingRender = layout(session).listing();
        if (auction == null || provider == null || listingRender == null) {
            return;
        }

        ItemConfig.Template template = listingRender.forType(null);
        if (template == null) {
            return;
        }

        int buyAmount = buyAmount(session, auction);
        ItemStack displayItem = auction.info().item().clone();
        displayItem.setAmount(buyAmount);

        RenderEntry entry = new RenderEntry(template, displayItem,
                guis.placeholders().forBuyAmount(viewer, auction, provider, buyAmount),
                guis.flags().forBuyAmount(viewer, auction, () -> buyAmount(session, auction)));
        drawEntries(viewer, session, List.of(entry), layout(session).listingSlots());
    }

    private int clampedBuyAmount(GuiSession session, Listing.Auction auction) {
        return Math.clamp(buyAmount(session, auction), 1, auction.remainingAmount());
    }

    private int buyAmount(GuiSession session, @Nullable Listing.Auction auction) {
        Integer amount = session.attribute(BuyAction.ATTR_BUY_AMOUNT, Integer.class);
        if (amount != null) {
            return amount;
        }
        return auction != null ? auction.remainingAmount() : 0;
    }
}
package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.action.ActionRegistry;
import com.ftxeven.airauctions.gui.BaseGui;
import com.ftxeven.airauctions.gui.impl.ListingPreviewGui;
import com.ftxeven.airauctions.model.HistoryEntry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ServiceManager;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Shared shape behind actions that open a {@link ListingPreviewGui} for a specific listing or
 * history entry
 */
public abstract class PreviewAction implements ActionRegistry.Handler {

    protected final ServiceManager services;
    protected final ConfigManager configs;

    protected PreviewAction(ServiceManager services, ConfigManager configs) {
        this.services = services;
        this.configs = configs;
    }

    protected abstract String guiId();

    protected abstract boolean applies(Listing listing);

    protected abstract boolean applies(HistoryEntry entry);

    @Override
    public final void execute(ActionContext context, String args) {
        Map<String, String> placeholders = context.placeholders();
        String completedAtRaw = placeholders.get("completed_at");

        Map<String, Object> attributes = completedAtRaw != null
                ? resolveHistory(placeholders.get("id"), completedAtRaw)
                : resolveListing(placeholders.get("id"));

        if (attributes == null) {
            TargetedListingAction.notifyUnavailable(context, configs);
            return;
        }

        ScreenOpener.open(context, guiId(), args, attributes);
    }

    private @Nullable Map<String, Object> resolveListing(@Nullable String id) {
        if (id == null) {
            return null;
        }
        Listing listing = services.listings().find(id).orElse(null);
        if (listing == null || !applies(listing)) {
            return null;
        }
        return Map.of(BaseGui.ATTR_LISTING_ID, id);
    }

    private @Nullable Map<String, Object> resolveHistory(@Nullable String id, String completedAtRaw) {
        if (id == null) {
            return null;
        }

        Instant completedAt;
        try {
            completedAt = Instant.ofEpochMilli(Long.parseLong(completedAtRaw));
        } catch (NumberFormatException e) {
            return null;
        }

        HistoryEntry entry = services.history().find(id, completedAt).orElse(null);
        if (entry == null || !applies(entry)) {
            return null;
        }
        return Map.of(
                ListingPreviewGui.ATTR_HISTORY_ID, id,
                ListingPreviewGui.ATTR_HISTORY_COMPLETED_AT, completedAt.toEpochMilli());
    }
}
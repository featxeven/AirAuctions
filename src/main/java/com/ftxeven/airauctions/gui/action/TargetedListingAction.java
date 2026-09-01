package com.ftxeven.airauctions.gui.action;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.core.gui.GuiManager;
import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.action.ActionRegistry;
import com.ftxeven.airauctions.model.Listing;
import com.ftxeven.airauctions.service.ActionResult;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.ServiceManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class TargetedListingAction implements ActionRegistry.Handler {

    protected final ServiceManager services;
    protected final ConfigManager configs;

    protected TargetedListingAction(ServiceManager services, ConfigManager configs) {
        this.services = services;
        this.configs = configs;
    }

    @Override
    public final void execute(ActionContext context, String args) {
        Optional<Listing> listing = resolve(services, context);
        if (listing.isEmpty()) {
            notifyUnavailable(context, configs);
            return;
        }
        perform(context, listing.get(), args);
    }

    protected abstract void perform(ActionContext context, Listing listing, String args);

    public static Optional<Listing> resolve(ServiceManager services, ActionContext context) {
        String id = context.placeholders().get("id");
        return id != null ? services.listings().find(id) : Optional.empty();
    }

    public static void notifyUnavailable(ActionContext context, ConfigManager configs) {
        context.messenger().send(context.viewer(), configs.lang().get("errors.item.unavailable"), context.placeholders());
    }

    // shared by every [confirm:x] gate and every mutating action
    public static void notifyDenied(ActionContext context, ConfigManager configs, Eligibility.Denied denied) {
        send(context, configs, denied.langKey(), denied.placeholders());
    }

    public static void notifyDenied(ActionContext context, ConfigManager configs, ActionResult.Denied<?> denied) {
        send(context, configs, denied.langKey(), denied.placeholders());
    }

    // sends the denial message for a failed eligibility check and reports whether it failed
    public static boolean notifyIfDenied(ActionContext context, ConfigManager configs, Eligibility eligibility) {
        if (eligibility instanceof Eligibility.Denied denied) {
            notifyDenied(context, configs, denied);
            return true;
        }
        return false;
    }

    public static boolean guiEnabled(GuiManager guis, String guiId) {
        return guis.definition(guiId).map(gui -> gui.settings().enabled()).orElse(false);
    }

    private static void send(ActionContext context, ConfigManager configs, String langKey, Map<String, String> extra) {
        Map<String, String> placeholders = extra.isEmpty() ? context.placeholders() : merge(context.placeholders(), extra);
        context.messenger().send(context.viewer(), configs.lang().get(langKey), placeholders);
    }

    private static Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new HashMap<>(base);
        merged.putAll(extra);
        return merged;
    }
}
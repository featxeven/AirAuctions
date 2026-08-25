package com.ftxeven.airauctions.command.player;

import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.config.MainConfig;
import com.ftxeven.airauctions.common.command.CommandDispatch;
import com.ftxeven.airauctions.common.command.DynamicCommand;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.common.command.tabcomplete.TabCompleteEngine;
import com.ftxeven.airauctions.common.gui.GuiManager;
import com.ftxeven.airauctions.common.gui.OpenOptions;
import com.ftxeven.airauctions.economy.EconomyProvider;
import com.ftxeven.airauctions.gui.impl.DraftGui;
import com.ftxeven.airauctions.gui.impl.ListingDraft;
import com.ftxeven.airauctions.model.ListingType;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.Eligibility;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.economy.EconomyService;
import com.ftxeven.airauctions.service.listing.ListingService;
import com.ftxeven.airauctions.service.listing.ValidationResult;
import com.ftxeven.airauctions.util.ItemDisplay;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.TimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

// shared shape behind /ah sell and /ah bid

public abstract class DraftSubcommand<E> implements SubCommand {

    protected final ConfigManager configs;
    protected final Messenger messenger;
    protected final ServiceManager services;

    private final GuiManager guis;
    private final TabCompleteEngine tabCompleteEngine;
    private final String key;
    private final int leadingArgs;
    private final String langPrefix;

    protected DraftSubcommand(ConfigManager configs, Messenger messenger, ServiceManager services, GuiManager guis,
                              TabCompleteEngine tabCompleteEngine, String key, int leadingArgs, String langPrefix) {
        this.configs = configs;
        this.messenger = messenger;
        this.services = services;
        this.guis = guis;
        this.tabCompleteEngine = tabCompleteEngine;
        this.key = key;
        this.leadingArgs = leadingArgs;
        this.langPrefix = langPrefix;
    }

    // SubCommand

    @Override
    public final String name() { return config().name(); }

    @Override
    public final List<String> aliases() { return config().aliases(); }

    @Override
    public final boolean enabled() { return config().enabled(); }

    @Override
    public final String usage() { return config().usage(); }

    @Override
    public final String permission() { return Permissions.command(key); }

    @Override
    public final boolean playerOnly() { return true; }

    @Override
    public final int minArgs() {
        return CommandDispatch.minArgs(leadingArgs, extraAvailability(), amountAvailability(), economyAvailability());
    }

    @Override
    public final int maxArgs() {
        return CommandDispatch.maxArgs(leadingArgs, extraAvailability(), amountAvailability(), economyAvailability());
    }

    protected final DynamicCommand config() {
        DynamicCommand command = configs.commands().subcommands().get(key);
        return command != null ? command : new DynamicCommand(false, key, List.of(), "", "", Map.of(), Map.of());
    }

    // SubCommand - dispatch

    @Override
    public final void execute(CommandSender sender, String label, String subLabel, String[] args) {
        Player player = (Player) sender;

        Optional<PendingListing<E>> pending = services.confirmations().confirm(player.getUniqueId(), key);
        if (pending.isPresent() && pending.get().args().equals(List.of(args))) {
            finalizeListing(player, pending.get());
            return;
        }

        requestNew(player, args);
    }

    @Override
    public final List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }
        int typingIndex = args.length - 1;
        int extraSlots = extraAvailability() == CommandDispatch.Availability.DISABLED ? 0 : 1;

        int yamlPosition;
        if (typingIndex < leadingArgs) {
            yamlPosition = typingIndex + 1; // price is always fixed, unaffected by any slot
        } else if (extraSlots > 0 && typingIndex == extraArgIndex()) {
            yamlPosition = leadingArgs + 1; // ex: bid's duration
        } else if (typingIndex == amountArgIndex()) {
            yamlPosition = leadingArgs + extraSlots + 1;
        } else if (typingIndex == economyArgIndex()) {
            yamlPosition = leadingArgs + extraSlots + 2;
        } else {
            return List.of();
        }

        String[] synthetic = new String[yamlPosition];
        synthetic[yamlPosition - 1] = args[typingIndex];
        return tabCompleteEngine.complete(sender, config(), synthetic);
    }

    // Hooks

    protected abstract MainConfig.ListingFlow flow();

    protected CommandDispatch.Availability extraAvailability() {
        return CommandDispatch.Availability.DISABLED;
    }

    protected abstract Optional<E> parseLeadingExtra(Player seller, Optional<String> token);

    protected abstract Map<String, String> previewPlaceholders(Player seller, E extra);

    protected abstract Optional<ListingService.Prepared> prepareListing(
            Player seller, ItemStack item, int amount, double price, EconomyProvider provider, E extra);

    protected abstract ListingDraft buildDraft(
            ItemStack itemSnapshot, int slot, int amount, double price, EconomyProvider provider, E extra);

    protected abstract void announceCreated(Player seller, ListingService.Prepared prepared);

    // Availability / arg-index

    private CommandDispatch.Availability amountAvailability() {
        return switch (configs.main().listings().amountMode()) {
            case REQUIRED -> CommandDispatch.Availability.REQUIRED;
            case OPTIONAL -> CommandDispatch.Availability.OPTIONAL;
            case DISABLED -> CommandDispatch.Availability.DISABLED;
        };
    }

    private CommandDispatch.Availability economyAvailability() {
        return CommandDispatch.Availability.ofConfig(services.economy().multiCurrency());
    }

    // slot 0 = leading extra (ex: duration), slot 1 = amount, slot 2 = economy - always in
    // that declared order, regardless of which ones happen to be enabled right now
    private int extraArgIndex() {
        return CommandDispatch.argIndex(leadingArgs, 0, extraAvailability(), amountAvailability(), economyAvailability());
    }

    private int amountArgIndex() {
        return CommandDispatch.argIndex(leadingArgs, 1, extraAvailability(), amountAvailability(), economyAvailability());
    }

    private int economyArgIndex() {
        return CommandDispatch.argIndex(leadingArgs, 2, extraAvailability(), amountAvailability(), economyAvailability());
    }

    // Fresh request

    private void requestNew(Player player, String[] args) {
        Optional<EconomyProvider> providerOpt = resolveProvider(player, args);
        if (providerOpt.isEmpty()) {
            return;
        }
        EconomyProvider provider = providerOpt.get();

        OptionalDouble priceOpt = parsePrice(player, args[0], provider);
        if (priceOpt.isEmpty()) {
            return;
        }

        Optional<E> extraOpt = parseLeadingExtra(player, extraToken(args));
        if (extraOpt.isEmpty()) {
            return; // error already sent by the hook
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            messenger.send(player, configs.lang().get("errors.item.holding-air"));
            return;
        }
        int heldSlot = player.getInventory().getHeldItemSlot(); // captured alongside 'held', not re-read after the hop below

        OptionalInt amountOpt = parseAmount(player, args, held);
        if (amountOpt.isEmpty()) {
            return;
        }

        ValidationResult validation = services.validator().validate(player, held, amountOpt.getAsInt());
        if (!validation.ok()) {
            validation.send(player, configs, messenger);
            return;
        }

        PreChecked<E> preChecked = new PreChecked<>(args, held, heldSlot, amountOpt.getAsInt(), priceOpt.getAsDouble(), provider, extraOpt.get());
        services.validator().checkListingLimits(player, messenger, () -> afterLimitCheck(player, preChecked));
    }

    // continues once every check
    private void afterLimitCheck(Player player, PreChecked<E> preChecked) {
        EconomyService economy = services.economy();
        EconomyService.ChargeResult fee = economy.fee(player, preChecked.provider(), preChecked.price());
        if (notifyIfDenied(player, economy.eligibleForFee(player, preChecked.provider(), fee))) {
            return;
        }

        boolean requireConfirmation = flow().requireConfirmation();

        if (requireConfirmation) {
            ListingDraft draft = buildDraft(preChecked.held().clone(), preChecked.heldSlot(),
                    preChecked.amount(), preChecked.price(), preChecked.provider(), preChecked.extra());
            if (guiConfirmationEnabled(draft.type())) {
                openConfirmGui(player, draft);
                return;
            }
        }

        PendingListing<E> context = new PendingListing<>(List.of(preChecked.args()), preChecked.held().clone(),
                preChecked.heldSlot(), preChecked.amount(), preChecked.price(), preChecked.provider(), preChecked.extra());

        if (!requireConfirmation) {
            finalizeListing(player, context);
            return;
        }

        services.confirmations().request(player, key, flow().confirmationTimeout(), context, () -> sendExpired(player, context));
        sendConfirmationRequest(player, context);
    }

    // GUI confirmation

    private void openConfirmGui(Player player, ListingDraft draft) {
        guis.open(player, DraftGui.guiIdFor(draft.type()), new HashMap<>(),
                OpenOptions.entry(Map.of(DraftGui.ATTR_DRAFT, draft), List.of()));
    }

    private boolean guiConfirmationEnabled(ListingType type) {
        return guis.definition(DraftGui.guiIdFor(type)).map(gui -> gui.settings().enabled()).orElse(false);
    }

    // Confirmed - shared by both paths above

    private void finalizeListing(Player player, PendingListing<E> context) {
        EconomyService economy = services.economy();
        EconomyService.ChargeResult fee = economy.fee(player, context.provider(), context.price());
        if (notifyIfDenied(player, economy.eligibleForFee(player, context.provider(), fee))) {
            return;
        }

        services.listings().finalizeCreation(player, context.snapshot(), context.amount(), context.slot(), context.provider(), fee,
                feePlaceholders(player, context.provider(), fee),
                item -> prepareListing(player, item, context.amount(), context.price(), context.provider(), context.extra()),
                prepared -> {
                    services.validator().markListed(player.getUniqueId());
                    announceCreated(player, prepared);
                });
    }

    // Parsing

    private Optional<String> extraToken(String[] args) {
        int index = extraArgIndex();
        return index >= 0 && index < args.length ? Optional.of(args[index]) : Optional.empty();
    }

    private Optional<EconomyProvider> resolveProvider(Player player, String[] args) {
        EconomyService economy = services.economy();
        if (!economy.multiCurrency()) {
            return requireProvider(player, economy.defaultProvider());
        }

        int index = economyArgIndex();
        if (index < 0 || index >= args.length) {
            return requireProvider(player, economy.defaultProvider());
        }

        Optional<EconomyProvider> provider = economy.findByKey(args[index]);
        if (provider.isEmpty()) {
            messenger.send(player, configs.lang().get("errors.economy.not-found"));
        }
        return provider;
    }

    private Optional<EconomyProvider> requireProvider(Player player, Optional<EconomyProvider> provider) {
        if (provider.isEmpty()) {
            messenger.send(player, configs.lang().get("errors.economy.not-found"));
        }
        return provider;
    }

    private OptionalDouble parsePrice(Player player, String raw, EconomyProvider provider) {
        EconomyService economy = services.economy();
        OptionalDouble price = economy.parsePrice(raw, provider);
        if (price.isEmpty()) {
            messenger.send(player, configs.lang().get("errors.economy.invalid-price"));
            return OptionalDouble.empty();
        }

        if (notifyIfDenied(player, economy.eligibleForPrice(price.getAsDouble(), provider))) {
            return OptionalDouble.empty();
        }
        return price;
    }

    private OptionalInt parseAmount(Player player, String[] args, ItemStack held) {
        int index = amountArgIndex();
        if (index < 0 || index >= args.length) {
            return OptionalInt.of(held.getAmount()); // not provided (disabled, or optional & omitted)
        }

        int amount;
        try {
            amount = Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            messenger.send(player, configs.lang().get("errors.economy.invalid-amount"));
            return OptionalInt.empty();
        }

        if (amount <= 0) {
            messenger.send(player, configs.lang().get("errors.economy.invalid-amount"));
            return OptionalInt.empty();
        }
        if (amount > held.getAmount()) {
            messenger.send(player, configs.lang().get("errors.item.insufficient"), Map.of("holding", String.valueOf(held.getAmount())));
            return OptionalInt.empty();
        }
        return OptionalInt.of(amount);
    }

    private Map<String, String> feePlaceholders(Player player, EconomyProvider provider, EconomyService.ChargeResult fee) {
        EconomyService economy = services.economy();
        Map<String, String> placeholders = new HashMap<>();
        economy.formatInto(placeholders, "fee", provider.id(), fee);
        economy.formatInto(placeholders, "amount", provider.id(), economy.missing(player, provider, fee.amount()));
        return placeholders;
    }

    // Messaging

    private boolean notifyIfDenied(Player player, Eligibility eligibility) {
        if (eligibility instanceof Eligibility.Denied denied) {
            messenger.send(player, configs.lang().get(denied.langKey()), denied.placeholders());
            return true;
        }
        return false;
    }

    private void sendConfirmationRequest(Player player, PendingListing<E> context) {
        Map<String, String> placeholders = baseMessagePlaceholders(player, context);
        placeholders.putAll(previewPlaceholders(player, context.extra()));
        placeholders.put("timeout", formatTimeout(flow().confirmationTimeout()));
        messenger.send(player, configs.lang().get(langPrefix + ".confirmation.request"), placeholders);
    }

    private void sendExpired(Player player, PendingListing<E> context) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(context.amount()));
        ItemDisplay.formatInto(placeholders, "item", context.snapshot(), configs.lang());
        messenger.send(player, configs.lang().get(langPrefix + ".confirmation.expired"), placeholders);
    }

    private Map<String, String> baseMessagePlaceholders(Player player, PendingListing<E> context) {
        EconomyService economy = services.economy();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(context.amount()));
        ItemDisplay.formatInto(placeholders, "item", context.snapshot(), configs.lang());
        economy.formatInto(placeholders, "price", context.provider().id(), context.price());
        economy.formatInto(placeholders, "fee", context.provider().id(), economy.fee(player, context.provider(), context.price()));
        return placeholders;
    }

    private String formatTimeout(int timeoutSeconds) {
        return timeoutSeconds < 0
                ? configs.lang().get("placeholders.never").getFirst()
                : TimeFormatter.duration(Duration.ofSeconds(timeoutSeconds), configs.main().formatting(), configs.lang());
    }

    // Internal types

    private record PreChecked<E>(String[] args, ItemStack held, int heldSlot, int amount, double price, EconomyProvider provider, E extra) {}

    private record PendingListing<E>(List<String> args, ItemStack snapshot, int slot, int amount, double price,
                                     EconomyProvider provider, E extra) {
    }

    /** Stands in for E when a listing type has no extra leading argument to parse or carry
     * across confirmation (sell has none, unlike bid's duration) */
    protected record NoExtra() {
    }
}
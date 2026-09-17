package com.ftxeven.airauctions.core.gui;

import com.ftxeven.airauctions.core.animation.AnimationManager;
import com.ftxeven.airauctions.core.condition.ConditionEvaluator;
import com.ftxeven.airauctions.core.gui.action.*;
import com.ftxeven.airauctions.core.gui.config.*;
import com.ftxeven.airauctions.core.gui.flag.FlagGate;
import com.ftxeven.airauctions.core.gui.input.InputRegistry;
import com.ftxeven.airauctions.core.gui.nav.GuiContext;
import com.ftxeven.airauctions.core.gui.nav.LockedContext;
import com.ftxeven.airauctions.core.gui.nav.ScreenContextStore;
import com.ftxeven.airauctions.core.gui.nav.ScreenKey;
import com.ftxeven.airauctions.core.gui.nav.ScreenState;
import com.ftxeven.airauctions.core.gui.render.GuiRenderer;
import com.ftxeven.airauctions.core.gui.render.ItemBuilder;
import com.ftxeven.airauctions.core.gui.render.ItemResolver;
import com.ftxeven.airauctions.core.gui.render.MaterialResolver;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.Placeholders;
import com.ftxeven.airauctions.util.Scheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Logger;

public final class GuiManager {

    private final Logger logger;
    private final GuiRegistry registry;
    private final InputRegistry input;
    private final Messenger messenger;
    private final ActionRegistry actions;
    private final ConditionEvaluator conditions;
    private final FlagGate flags;
    private final ActionDispatcher dispatcher;
    private final GuiRenderer renderer;
    private final ItemResolver items;
    private final AnimationManager animations;
    private final PlayerNameResolver players;
    private final CooldownFormatter cooldownFormatter;

    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final ScreenContextStore context = new ScreenContextStore();

    private BiConsumer<Player, GuiSession> staleClickHandler = (viewer, session) -> {};

    private GuiManager(JavaPlugin plugin, GuiRegistry registry, InputRegistry input, Messenger messenger,
                       MaterialResolver.HookResolver hooks, MaterialResolver.HeadResolver heads, PlayerNameResolver players,
                       AnimationManager animations, CooldownFormatter cooldownFormatter) {
        this.logger = plugin.getLogger();
        this.registry = registry;
        this.input = input;
        this.messenger = messenger;
        this.animations = animations;
        this.players = players;
        this.cooldownFormatter = cooldownFormatter;

        this.conditions = new ConditionEvaluator(logger::warning);
        this.flags = new FlagGate();
        this.actions = new ActionRegistry();
        this.dispatcher = new ActionDispatcher(actions, new ActionParser(logger), conditions, flags);

        MaterialResolver materials = new MaterialResolver(hooks, heads, logger);
        ItemBuilder builder = new ItemBuilder(materials, messenger, logger, flags);
        this.items = new ItemResolver(conditions, builder, animations);

        this.renderer = new GuiRenderer(items);
    }

    public static Builder builder(JavaPlugin plugin, Messenger messenger, AnimationManager animations) {
        return new Builder(plugin, messenger, animations);
    }

    public static final class Builder {
        private final JavaPlugin plugin;
        private final Messenger messenger;
        private final AnimationManager animations;
        private MaterialResolver.HookResolver hooks = MaterialResolver.HookResolver.NONE;
        private MaterialResolver.HeadResolver heads = MaterialResolver.HeadResolver.NONE;
        private PlayerNameResolver players = PlayerNameResolver.NONE;
        private CooldownFormatter cooldownFormatter = CooldownFormatter.NONE;
        private Set<String> reservedPaths = Set.of();
        private Set<String> layoutReplaceKeys = Set.of();

        private Builder(JavaPlugin plugin, Messenger messenger, AnimationManager animations) {
            this.plugin = plugin;
            this.messenger = messenger;
            this.animations = animations;
        }

        public Builder hooks(MaterialResolver.HookResolver hooks) {
            this.hooks = hooks;
            return this;
        }

        public Builder heads(MaterialResolver.HeadResolver heads) {
            this.heads = heads;
            return this;
        }

        public Builder players(PlayerNameResolver players) {
            this.players = players;
            return this;
        }

        public Builder cooldownFormatter(CooldownFormatter cooldownFormatter) {
            this.cooldownFormatter = cooldownFormatter;
            return this;
        }

        public Builder reservedPaths(Set<String> reservedPaths) {
            this.reservedPaths = Set.copyOf(reservedPaths);
            return this;
        }

        public Builder layoutReplaceKeys(Set<String> layoutReplaceKeys) {
            this.layoutReplaceKeys = Set.copyOf(layoutReplaceKeys);
            return this;
        }

        public @Nullable GuiManager build() {
            GuiRegistry registry = new GuiRegistry(plugin, reservedPaths, layoutReplaceKeys);
            if (!registry.load()) return null;

            InputRegistry input = new InputRegistry(plugin, messenger);
            if (!input.load(new AliasExpander(registry.shared().aliases(), plugin.getLogger()))) return null;

            return new GuiManager(plugin, registry, input, messenger, hooks, heads, players, animations, cooldownFormatter);
        }
    }

    public ItemResolver items() {
        return items;
    }

    public GuiRenderer renderer() {
        return renderer;
    }

    public GuiManager dynamicRenderer(String guiId, GuiRenderer.DynamicRenderer renderer) {
        this.renderer.dynamicRenderer(guiId, renderer);
        return this;
    }

    public ActionRegistry actions() {
        return actions;
    }

    public InputRegistry input() {
        return input;
    }

    public ActionDispatcher dispatcher() {
        return dispatcher;
    }

    public ConditionEvaluator conditions() {
        return conditions;
    }

    public Set<String> ids() {
        return registry.all().keySet();
    }

    public SharedConfig shared() {
        return registry.shared();
    }

    public Optional<GuiConfig> definition(String guiId) {
        return registry.get(guiId);
    }

    public boolean reload() {
        boolean guisOk = registry.reload();
        boolean inputOk = input.reload(new AliasExpander(registry.shared().aliases(), logger));
        closeAll();
        return guisOk && inputOk;
    }

    // Opening / closing

    // plain entry point for anything that isn't itself gui-to-gui navigation
    public void open(Player viewer, String guiId, Map<String, String> placeholders) {
        open(viewer, guiId, placeholders, OpenOptions.DEFAULT);
    }

    // full open, resolved by id
    public void open(Player viewer, String guiId, Map<String, String> placeholders, OpenOptions options) {
        Optional<GuiConfig> definition = registry.get(guiId);
        if (definition.isEmpty()) {
            logger.warning("Tried to open unknown GUI '" + guiId + "' for " + viewer.getName());
            return;
        }
        open(viewer, definition.get(), placeholders, options);
    }

    // Full open, config already in hand - the canonical implementation everything above (and
    // refresh()/resume(), below) funnels into
    public void open(Player viewer, GuiConfig definition, Map<String, String> placeholders, OpenOptions options) {
        GuiSession previous = sessions.get(viewer.getUniqueId());
        boolean reopen = options.kind() == OpenOptions.Kind.REOPEN && previous != null;
        boolean fireActions = options.kind() == OpenOptions.Kind.ENTRY;
        List<String> originChain = reopen ? previous.originChain() : options.ancestorChain();

        GuiConfig effective = definition.effective(originChain);
        GuiSettings settings = effective.settings();

        long openTick = reopen ? previous.openTick() : animations.currentTick();

        GuiSession session = new GuiSession(viewer.getUniqueId(), effective, placeholders, options.flagResolver(), openTick, originChain);
        if (reopen) {
            session.inheritRuntimeState(previous);
        } else if (previous != null) {
            session.inheritCooldowns(previous);
        }
        options.attributes().forEach(session::attribute);
        session.placeholders().put("previous_gui", previousGuiPlaceholder(session));
        session.placeholders().put("gui", session.definition().id());

        renderer.prepare(viewer, session);
        sessions.put(viewer.getUniqueId(), session);

        Inventory inventory = Bukkit.createInventory(null, Math.max(1, settings.rows()) * 9,
                messenger.renderLine(viewer, settings.title(), placeholders));
        session.attachInventory(inventory);

        if (previous != null) {
            previous.deactivate();
            if (fireActions) {
                runActions(previous.definition().settings().closeActions(), viewer, previous, "<close-actions>");
            }
        }

        renderer.draw(viewer, session);
        syncLiveContext(viewer, session);
        viewer.openInventory(inventory);
        if (fireActions) {
            runActions(settings.openActions(), viewer, session, "<open-actions>");
        }
        session.refreshTask(scheduleAutoRefresh(viewer, session));
    }

    private String previousGuiPlaceholder(GuiSession session) {
        GuiContext back = session.navBack();
        return back != null ? back.screen().guiId() : "null";
    }

    public void close(Player viewer) {
        endSession(viewer, true);
    }

    // Pausing for chat/dialog/sign input

    // closes the viewer's inventory without ending the session
    public void pauseForInput(Player viewer) {
        GuiSession session = sessions.get(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        session.awaitingInput(true);
        if (viewer.getOpenInventory().getTopInventory().equals(session.inventory())) {
            viewer.closeInventory();
        }
    }

    // Reopens the exact session paused by pauseForInput()
    public void resume(Player viewer) {
        GuiSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.awaitingInput()) {
            return;
        }
        session.awaitingInput(false);
        reopenOrRedraw(viewer, session, true);
    }

    // [open] context:true - back navigation
    public void openBack(Player viewer) {
        GuiSession current = sessions.get(viewer.getUniqueId());
        if (current == null) {
            return;
        }
        GuiContext back = current.navBack();
        if (back == null) {
            logger.warning("'context:true' back-navigation requested for " + viewer.getName() + " but there is no previous gui to return to");
            return;
        }

        ActionContext navContext = context(viewer, current, "<back-navigation>", current.placeholders(), back.flagResolver());
        ScreenState currentLive = ScreenState.liveStateOf(current);
        ScreenState restored = ForwardNavigation.resolveForwardState(navContext, back.screen(), currentLive, ForwardNavigation.parse("", navContext));

        OpenOptions options = OpenOptions.forScreen(back.flagResolver(), back.screen(), restored, back.previous(), back.originChain());
        open(viewer, back.screen().guiId(), new LinkedHashMap<>(current.placeholders()), options);
    }

    // Session teardown

    private @Nullable GuiSession removeSession(UUID uuid) {
        GuiSession session = sessions.remove(uuid);
        if (session != null) {
            session.deactivate();
        }
        return session;
    }

    private @Nullable GuiSession terminate(Player viewer, boolean runCloseActions) {
        GuiSession session = removeSession(viewer.getUniqueId());
        if (session != null && runCloseActions) {
            runActions(session.definition().settings().closeActions(), viewer, session, "<close-actions>");
        }
        return session;
    }

    private void endSession(Player viewer, boolean closeInventory) {
        GuiSession session = terminate(viewer, true);
        if (session == null) {
            return;
        }
        if (closeInventory && viewer.getOpenInventory().getTopInventory().equals(session.inventory())) {
            viewer.closeInventory();
        }
        forgetContext(viewer.getUniqueId());
        input.disconnect(viewer.getUniqueId()); // drop any input prompt this session was awaiting
    }

    public void disconnect(UUID uuid) {
        removeSession(uuid);
        forgetContext(uuid);
        input.disconnect(uuid);
    }

    public void closeAll() {
        for (UUID uuid : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                close(player);
            } else {
                disconnect(uuid);
            }
        }
    }

    public void shutdown() {
        for (GuiSession session : sessions.values()) {
            session.deactivate();
        }
        sessions.clear();
    }

    private void forgetContext(UUID uuid) {
        context.forget(uuid);
    }

    public @Nullable GuiSession session(Player viewer) {
        return sessions.get(viewer.getUniqueId());
    }

    public @Nullable UUID resolvePlayerName(String name) {
        return players.resolve(name);
    }

    public void refresh(Player viewer) {
        GuiSession session = sessions.get(viewer.getUniqueId());
        if (session != null) {
            reopenOrRedraw(viewer, session, false);
        }
    }

    private void reopenOrRedraw(Player viewer, GuiSession session, boolean reshow) {
        if (session.definition().settings().forceReopen()) {
            open(viewer, session.definition(), session.placeholders(), OpenOptions.silentReopen(session.flagResolver()));
            return;
        }
        renderer.render(viewer, session);
        syncLiveContext(viewer, session);
        if (reshow) {
            viewer.openInventory(session.inventory());
        }
    }

    private @Nullable ScheduledTask scheduleAutoRefresh(Player viewer, GuiSession session) {
        int interval = session.definition().settings().refreshInterval();
        if (interval < 0) {
            return null;
        }
        return Scheduler.runEntityTimer(viewer, () -> {
            if (session.active() && !session.awaitingInput()) { // don't pop the GUI back open mid-prompt
                refresh(viewer);
            }
        }, interval, interval).orElse(null);
    }

    // Per-screen context memory

    public ScreenState liveState(UUID player, ScreenKey screen) {
        return context.liveState(player, screen);
    }

    public LockedContext lockedContext(UUID player, ScreenKey screen) {
        return context.locked(player, screen);
    }

    public void lockContextFields(UUID player, ScreenKey screen, @Nullable Integer page, Map<String, String> attributes) {
        context.lock(player, screen, page, attributes);
    }

    private void syncLiveContext(Player viewer, GuiSession session) {
        context.recordLive(viewer.getUniqueId(), session.screenKey(), ScreenState.liveStateOf(session));
    }

    // Clicks

    public void handleClick(InventoryClickEvent event, Player viewer) {
        GuiSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !event.getInventory().equals(session.inventory())) {
            return;
        }
        event.setCancelled(true);

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(session.inventory())) {
            return; // click landed in the player's own inventory, not this GUI
        }

        ItemConfig.ClickType type = ItemConfig.ClickType.from(event);
        if (type == null) {
            return; // no action-list key for these
        }

        long now = System.currentTimeMillis();
        if (!session.passesGlobalFloor(now)) {
            return; // anti-spam floor
        }

        int slot = event.getSlot();
        GuiSession.DynamicSlot beforeResync = session.dynamicSlot(slot);
        boolean dynamicEntry = beforeResync != null;
        if (!hasLiveAction(viewer, session, slot, type)) {
            return; // nothing would run for this click
        }

        // an action is actually about to run off this click's flag checks
        if (dynamicEntry) {
            renderer.render(viewer, session);
        } else {
            renderer.prepare(viewer, session);
        }

        GuiSession.DynamicSlot dynamic = session.dynamicSlot(slot);
        if (dynamicEntry && (dynamic == null || identityChanged(beforeResync, dynamic))) {
            notifyStaleClick(viewer, session);
            return;
        }

        ItemConfig item = dynamic == null ? session.itemAt(slot) : null;
        if (dynamic == null && item == null) {
            return; // neither a dynamic entry nor a configured static item resolved here
        }

        ItemResolver.ResolvedFields resolved = dynamic != null
                ? dynamic.resolved()
                : items.resolveFields(item.template(), viewer, session.placeholders(), session.flagResolver(), session.openTick(), null);
        if (resolved == null) {
            return;
        }
        String key = dynamic != null ? dynamic.key() : item.key();

        double remaining = session.checkAndStartCooldown(key, resolved.cooldown(), now);
        if (remaining > 0) {
            if (resolved.cooldownMessage() != null) {
                messenger.send(viewer, List.of(resolved.cooldownMessage()), Map.of("timeout", formatCooldown(remaining)));
            }
            return;
        }

        List<String> clickActions = resolved.actions().get(type);
        if (clickActions != null && !clickActions.isEmpty()) {
            Map<String, String> placeholders = dynamic != null ? dynamic.placeholders() : session.placeholders();
            dispatcher.run(clickActions, context(viewer, session, key, placeholders, resolved.flagResolver()));
        }
    }

    // peeks at the currently-known (not yet resynced) state to check whether this click would
    // run anything at all
    private boolean hasLiveAction(Player viewer, GuiSession session, int slot, ItemConfig.ClickType type) {
        GuiSession.DynamicSlot dynamic = session.dynamicSlot(slot);
        ItemConfig item = dynamic == null ? session.itemAt(slot) : null;
        if (dynamic == null && item == null) {
            return false;
        }

        ItemResolver.ResolvedFields resolved = dynamic != null
                ? dynamic.resolved()
                : items.resolveFields(item.template(), viewer, session.placeholders(), session.flagResolver(), session.openTick(), null);
        if (resolved == null) {
            return false;
        }

        List<String> clickActions = resolved.actions().get(type);
        return clickActions != null && flags.anyApplicable(clickActions, resolved.flagResolver());
    }

    // A dynamic slot is keyed by position
    private static boolean identityChanged(GuiSession.DynamicSlot before, @Nullable GuiSession.DynamicSlot after) {
        String identity = before.entryId();
        if (identity == null) {
            return false;
        }
        return after == null || !identity.equals(after.entryId());
    }

    // a stale click's resync can silently correct session.page()/placeholders
    private void notifyStaleClick(Player viewer, GuiSession session) {
        if (session.definition().settings().forceReopen()) {
            open(viewer, session.definition(), session.placeholders(), OpenOptions.silentReopen(session.flagResolver()));
            GuiSession reopened = sessions.get(viewer.getUniqueId());
            if (reopened == null) {
                return;
            }
            session = reopened;
        }
        staleClickHandler.accept(viewer, session);
    }

    // Fires when a click resolves to a dynamic entry that's already gone by the time the
    // pre-click resync finishes
    public GuiManager onStaleClick(BiConsumer<Player, GuiSession> handler) {
        this.staleClickHandler = handler;
        return this;
    }

    public void handleClose(Player viewer, Inventory closedInventory) {
        GuiSession session = sessions.get(viewer.getUniqueId());
        if (session == null || !session.inventory().equals(closedInventory)) {
            return;
        }
        if (session.awaitingInput()) {
            return; // closed by pauseForInput() itself while awaiting an input answer, not a real close
        }
        endSession(viewer, false);
    }

    private String formatCooldown(double seconds) {
        return cooldownFormatter.format(seconds);
    }

    private void runActions(List<String> lines, Player viewer, GuiSession session, String itemKey) {
        if (!lines.isEmpty()) {
            dispatcher.run(lines, context(viewer, session, itemKey, session.placeholders(), session.flagResolver()));
        }
    }

    private ActionContext context(Player viewer, GuiSession session, String itemKey, Map<String, String> placeholders, Function<String, String> flagResolver) {
        return new ActionContext(viewer, session, this, messenger, placeholders,
                Placeholders.resolver(viewer, placeholders), flagResolver, itemKey, logger);
    }

    @FunctionalInterface
    public interface PlayerNameResolver {
        PlayerNameResolver NONE = name -> null;

        @Nullable UUID resolve(String name);
    }

    @FunctionalInterface
    public interface CooldownFormatter {
        CooldownFormatter NONE = seconds -> String.valueOf(Math.max(1L, (long) Math.ceil(seconds)));

        String format(double secondsRemaining);
    }
}
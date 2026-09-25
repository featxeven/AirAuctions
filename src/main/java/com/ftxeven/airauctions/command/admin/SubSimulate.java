package com.ftxeven.airauctions.command.admin;

import com.ftxeven.airauctions.command.CommandDispatcher;
import com.ftxeven.airauctions.command.SubCommand;
import com.ftxeven.airauctions.config.ConfigManager;
import com.ftxeven.airauctions.permission.Permissions;
import com.ftxeven.airauctions.service.ServiceManager;
import com.ftxeven.airauctions.service.simulation.SimulationService;
import com.ftxeven.airauctions.util.Messenger;
import com.ftxeven.airauctions.util.Scheduler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class SubSimulate implements SubCommand {

    private static final int DEFAULT_COUNT = 200;
    private static final int CONFIRM_THRESHOLD = 500;
    private static final long PROGRESS_INTERVAL_MS = 400;

    private final Messenger messenger;
    private final ConfigManager configs;
    private final ServiceManager services;
    private final Logger logger;

    public SubSimulate(Messenger messenger, ConfigManager configs, ServiceManager services, Logger logger) {
        this.messenger = messenger;
        this.configs = configs;
        this.services = services;
        this.logger = logger;
    }

    @Override
    public String name() { return "simulate"; }

    @Override
    public String permission() { return Permissions.ADMIN; }

    @Override
    public int minArgs() { return 1; }

    @Override
    public int maxArgs() { return -1; }

    @Override
    public String usage() { return "/airauctions simulate <generate|clear|status>"; }

    @Override
    public void execute(CommandSender sender, String label, String subLabel, String[] args) {
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "generate" -> generate(sender, args);
            case "clear" -> clear(sender);
            case "status" -> status(sender);
            default -> messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
        }
    }

    private void generate(CommandSender sender, String[] args) {
        Optional<Parsed> parsed = parseGenerate(args);
        if (parsed.isEmpty()) {
            messenger.send(sender, configs.lang().get("errors.access.incorrect-usage"), Map.of("usage", usage()));
            return;
        }

        Parsed request = parsed.get();
        if (request.count() <= 0) {
            messenger.send(sender, configs.lang().get("general.simulate.invalid-count"));
            return;
        }
        if (request.count() > CONFIRM_THRESHOLD && !request.confirmed()) {
            messenger.send(sender, configs.lang().get("general.simulate.confirm-required"),
                    Map.of("count", String.valueOf(request.count())));
            return;
        }

        messenger.send(sender, configs.lang().get("general.simulate.started"), Map.of("count", String.valueOf(request.count())));

        SimulationService.GenerateOptions options = new SimulationService.GenerateOptions(
                request.count(), request.mix(), request.seed(), request.playerPoolSize());

        long[] lastReport = {0L};

        runAsyncGuarded(sender, "generate", () -> {
            SimulationService.Result result = services.simulation().generate(options, (completed, total) -> {
                long now = System.currentTimeMillis();
                if (completed != total && now - lastReport[0] < PROGRESS_INTERVAL_MS) {
                    return;
                }
                lastReport[0] = now;
                int percent = (int) (completed * 100L / total);

                Scheduler.runTargetAware(sender, () -> messenger.send(sender, configs.lang().get("general.simulate.progress"), Map.of(
                        "percent", String.valueOf(percent),
                        "count", String.valueOf(completed),
                        "total", String.valueOf(total))));
            });

            Scheduler.runTargetAware(sender, () -> messenger.send(sender, configs.lang().get("general.simulate.result"), Map.of(
                    "auctions", String.valueOf(result.auctionsCreated()),
                    "bids", String.valueOf(result.bidsCreated()),
                    "purchases", String.valueOf(result.purchasesSimulated()),
                    "players", String.valueOf(result.playersUsed()),
                    "seed", String.valueOf(result.seed()))));
        });
    }

    private void clear(CommandSender sender) {
        runAsyncGuarded(sender, "clear", () -> {
            int removed = services.simulation().clear();

            Scheduler.runTargetAware(sender, () ->
                    messenger.send(sender, configs.lang().get("general.simulate.cleared"), Map.of("count", String.valueOf(removed))));
        });
    }

    private void status(CommandSender sender) {
        runAsyncGuarded(sender, "status", () -> {
            SimulationService.Status status = services.simulation().status();
            Scheduler.runTargetAware(sender, () -> messenger.send(sender, configs.lang().get("general.simulate.status"), Map.of(
                    "listings", String.valueOf(status.trackedListings()),
                    "players", String.valueOf(status.syntheticPlayers()),
                    "providers", String.valueOf(status.usableProviders()))));
        });
    }

    private void runAsyncGuarded(CommandSender sender, String action, Runnable task) {
        Scheduler.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.warning("Simulation '" + action + "' failed: " + e.getMessage());
                Scheduler.runTargetAware(sender, () -> messenger.send(sender, configs.lang().get("general.simulate.failed")));
            }
        });
    }

    private Optional<Parsed> parseGenerate(String[] args) {
        int count = DEFAULT_COUNT;
        SimulationService.Mix mix = SimulationService.Mix.MIXED;
        long seed = System.nanoTime();
        int playerPoolSize = -1;
        boolean confirmed = false;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("confirm")) {
                confirmed = true;
            } else if (startsWithIgnoreCase(arg, "seed=")) {
                Long value = parseLong(arg.substring(5));
                if (value == null) return Optional.empty();
                seed = value;
            } else if (startsWithIgnoreCase(arg, "players=")) {
                Integer value = parseInt(arg.substring(8));
                if (value == null) return Optional.empty();
                playerPoolSize = value;
            } else if (isMix(arg)) {
                mix = SimulationService.Mix.valueOf(arg.toUpperCase(Locale.ROOT));
            } else {
                Integer value = parseInt(arg);
                if (value == null) return Optional.empty();
                count = value;
            }
        }

        return Optional.of(new Parsed(count, mix, seed, playerPoolSize, confirmed));
    }

    private boolean isMix(String arg) {
        return arg.equalsIgnoreCase("auctions") || arg.equalsIgnoreCase("bids") || arg.equalsIgnoreCase("mixed");
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private Long parseLong(String raw) {
        try { return Long.parseLong(raw); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInt(String raw) {
        try { return Integer.parseInt(raw); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return CommandDispatcher.filterPrefix(candidates(args), args[args.length - 1]);
    }

    private List<String> candidates(String[] args) {
        if (args.length == 1) {
            return List.of("generate", "clear", "status");
        }
        if (args[0].equalsIgnoreCase("generate")) {
            return switch (args.length) {
                case 2 -> List.of("100", "200", "1000", "5000");
                case 3 -> List.of("mixed", "auctions", "bids");
                default -> unusedGenerateFlags(args);
            };
        }
        return List.of();
    }

    private List<String> unusedGenerateFlags(String[] args) {
        List<String> flags = new ArrayList<>(List.of("seed=", "players=", "confirm"));
        for (int i = 3; i < args.length - 1; i++) {
            String used = args[i];
            flags.removeIf(flag -> flag.equals("confirm")
                    ? used.equalsIgnoreCase("confirm")
                    : startsWithIgnoreCase(used, flag));
        }
        return flags;
    }

    private record Parsed(int count, SimulationService.Mix mix, long seed, int playerPoolSize, boolean confirmed) {}
}
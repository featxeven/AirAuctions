package com.ftxeven.airauctions.core.gui.input;

import com.ftxeven.airauctions.core.gui.action.ActionContext;
import com.ftxeven.airauctions.core.gui.config.AliasExpander;
import com.ftxeven.airauctions.core.gui.input.config.ChatInputConfig;
import com.ftxeven.airauctions.core.gui.input.config.DialogInputConfig;
import com.ftxeven.airauctions.core.gui.input.config.InputConfigReader;
import com.ftxeven.airauctions.core.gui.input.config.SignInputConfig;
import com.ftxeven.airauctions.core.gui.input.impl.ChatInputHandler;
import com.ftxeven.airauctions.core.gui.input.impl.DialogInputHandler;
import com.ftxeven.airauctions.core.gui.input.impl.SignInputHandler;
import com.ftxeven.airauctions.util.Messenger;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class InputRegistry {

    public static final String INPUT_FOLDER = ".input";

    private static final String CHAT_FILE = "chat.yml";
    private static final String DIALOG_FILE = "dialog.yml";
    private static final String SIGN_FILE = "sign.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path folder;
    private final InputConfigReader reader;

    private final ChatInputHandler chatHandler;
    private final DialogInputHandler dialogHandler;
    private final SignInputHandler signHandler;
    private final Map<InputType, Handler> handlers = new EnumMap<>(InputType.class);

    private volatile ChatInputConfig chat = ChatInputConfig.EMPTY;
    private volatile DialogInputConfig dialog = DialogInputConfig.EMPTY;
    private volatile SignInputConfig sign = SignInputConfig.EMPTY;

    public InputRegistry(JavaPlugin plugin, Messenger messenger) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.folder = plugin.getDataFolder().toPath().resolve("guis").resolve(INPUT_FOLDER);
        this.reader = new InputConfigReader(logger);

        this.chatHandler = new ChatInputHandler(this, messenger);
        handlers.put(InputType.CHAT, chatHandler);

        this.dialogHandler = new DialogInputHandler(this, messenger);
        handlers.put(InputType.DIALOG, dialogHandler);

        this.signHandler = new SignInputHandler(this, messenger);
        handlers.put(InputType.SIGN, signHandler);
    }

    public boolean load() {
        return load(new AliasExpander(Map.of(), logger));
    }

    public boolean load(AliasExpander expander) {
        extractBundledDefaults();
        boolean ok = loadFromDisk(expander);
        if (!ok) {
            logger.warning("Failed to fully load input configuration (see errors above)");
        }
        return ok;
    }

    public boolean reload() {
        return reload(new AliasExpander(Map.of(), logger));
    }

    public boolean reload(AliasExpander expander) {
        extractBundledDefaults();
        return loadFromDisk(expander);
    }

    public ChatInputConfig chat() {
        return chat;
    }

    public DialogInputConfig dialog() {
        return dialog;
    }

    public SignInputConfig sign() {
        return sign;
    }

    // Dispatch

    public void request(ActionContext context, String inputKey, Consumer<String> onSubmit) {
        InputType type = context.session().definition().settings().inputType();
        Player viewer = context.viewer();

        if (type == InputType.SIGN && !signHandler.isSupported(viewer)) {
            context.logger().warning("Sign input requested by item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' but " + viewer.getName()
                    + "'s client doesn't support editable signs (1.20+), falling back to chat input");
            type = InputType.CHAT;
        } else if (type == InputType.DIALOG && !dialogHandler.isSupported(viewer)) {
            context.logger().warning("Dialog input requested by item '" + context.itemKey() + "' in GUI '"
                    + context.guiId() + "' but " + viewer.getName()
                    + "'s client doesn't support dialogs (1.21.6+), falling back to chat input");
            type = InputType.CHAT;
        }

        Handler handler = handlers.get(type);
        if (handler == null) {
            context.logger().warning("Input type '" + type + "' has no handler registered yet, ignoring input request '"
                    + inputKey + "' from item '" + context.itemKey() + "' in GUI '" + context.guiId() + "'");
            return;
        }
        handler.request(context, inputKey, onSubmit);
    }

    public void handleChat(AsyncChatEvent event) {
        chatHandler.handle(event);
    }

    public void handleSign(UncheckedSignChangeEvent event) {
        signHandler.handle(event);
    }

    public void disconnect(UUID uuid) {
        handlers.values().forEach(handler -> handler.disconnect(uuid));
    }

    private void extractBundledDefaults() {
        for (String file : List.of(CHAT_FILE, DIALOG_FILE, SIGN_FILE)) {
            String resource = "guis/" + INPUT_FOLDER + "/" + file;
            if (!new File(plugin.getDataFolder(), resource).exists()) {
                plugin.saveResource(resource, false);
            }
        }
    }

    private boolean loadFromDisk(AliasExpander expander) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            logger.severe("Could not create guis/" + INPUT_FOLDER + " folder: " + e.getMessage());
            return false;
        }

        boolean ok = true;

        try {
            chat = reader.readChat(loadYaml(CHAT_FILE), expander);
        } catch (IOException | InvalidConfigurationException e) {
            logger.severe("Could not read guis/" + INPUT_FOLDER + "/" + CHAT_FILE + ": " + e.getMessage());
            chat = ChatInputConfig.EMPTY;
            ok = false;
        }

        try {
            dialog = reader.readDialog(loadYaml(DIALOG_FILE), expander);
        } catch (IOException | InvalidConfigurationException e) {
            logger.severe("Could not read guis/" + INPUT_FOLDER + "/" + DIALOG_FILE + ": " + e.getMessage());
            dialog = DialogInputConfig.EMPTY;
            ok = false;
        }

        try {
            sign = reader.readSign(loadYaml(SIGN_FILE), expander);
        } catch (IOException | InvalidConfigurationException e) {
            logger.severe("Could not read guis/" + INPUT_FOLDER + "/" + SIGN_FILE + ": " + e.getMessage());
            sign = SignInputConfig.EMPTY;
            ok = false;
        }

        return ok;
    }

    private YamlConfiguration loadYaml(String file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(folder.resolve(file).toFile());
        return yaml;
    }

    public interface Handler {
        void request(ActionContext context, String inputKey, Consumer<String> onSubmit);
        void disconnect(UUID uuid);
    }
}
package com.ftxeven.airauctions.config;

import com.ftxeven.airauctions.core.animation.Animation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimationsConfig extends BaseConfig {

    private volatile Map<String, Animation> animations;

    public AnimationsConfig(JavaPlugin plugin) {
        super(plugin, "data/animations.yml");
    }

    @Override
    protected void read(ConfigurationSection yaml) {
        animations = readAnimations(orEmpty(yaml.getConfigurationSection("animations")));
    }

    public Map<String, Animation> animations() {
        return animations;
    }

    public boolean has(String key) {
        return animations.containsKey(key);
    }

    // Section readers

    private Map<String, Animation> readAnimations(ConfigurationSection sec) {
        if (sec == null) {
            return Map.of();
        }
        Map<String, Animation> map = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            Animation animation = readAnimation(sec.getConfigurationSection(key), key);
            if (animation != null) {
                map.put(key, animation);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private Animation readAnimation(ConfigurationSection sec, String key) {
        sec = orEmpty(sec);

        List<String> frames = sec.getStringList("frames");
        if (frames.isEmpty()) {
            plugin.getLogger().warning("Animation '" + key + "' in " + fileName() + " has no frames, skipping");
            return null;
        }

        int interval = getInt(sec, "interval", 10);
        if (interval < 1) {
            plugin.getLogger().warning("interval must be at least 1 for animation '" + key + "' in " + fileName() + ", using 1");
            interval = 1;
        }

        return new Animation(frames, interval, enumOr(sec, "mode", Animation.Mode.class, Animation.Mode.LOOP));
    }
}
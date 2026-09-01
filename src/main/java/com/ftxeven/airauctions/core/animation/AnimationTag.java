package com.ftxeven.airauctions.core.animation;

import java.util.regex.Pattern;

public final class AnimationTag {

    public static final Pattern PATTERN = Pattern.compile("<(?:anim|a):([a-zA-Z0-9_-]+)(?::(\\d+))?>");

    private AnimationTag() {}
}
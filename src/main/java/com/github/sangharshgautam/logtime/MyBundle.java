package com.github.sangharshgautam.logtime;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class MyBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.MyBundle";

    private static final MyBundle INSTANCE = new MyBundle();

    private MyBundle() {
        super(BUNDLE);
    }

    public static String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    @SuppressWarnings("unused")
    public static String messagePointer(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object... params) {
        return INSTANCE.getLazyMessage(key, params).get();
    }
}

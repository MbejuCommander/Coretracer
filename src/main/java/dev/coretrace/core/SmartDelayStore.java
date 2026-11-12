package dev.coretrace.core;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Separate from capture snapshots: recovery must use the latest learned pacing. */
public final class SmartDelayStore {
    private Map<String, SmartDelay.State> profiles = new HashMap<>();
    public static String key(boolean perServer, String server) {
        return perServer ? "server:" + server.strip().toLowerCase(Locale.ROOT) : "global";
    }
    public SmartDelay.State state(boolean perServer, String server) {
        return profiles.computeIfAbsent(key(perServer, server), k -> new SmartDelay.State());
    }
    public void reset(boolean perServer, String server) { profiles.remove(key(perServer, server)); }
    public String json() { return new Gson().toJson(this); }
    public static SmartDelayStore load(Path file) throws IOException {
        if (!Files.exists(file)) return new SmartDelayStore();
        try {
            var store = new Gson().fromJson(Files.readString(file), SmartDelayStore.class);
            if (store == null || store.profiles == null) throw new IllegalArgumentException("Empty learning data");
            store.profiles.values().removeIf(Objects::isNull);
            return store;
        } catch (RuntimeException e) { throw new IOException("Invalid Smart Delay learning data", e); }
    }
}

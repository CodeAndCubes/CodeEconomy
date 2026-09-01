package com.mrleonardos.codeeconomy.internal;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;

/**
 * Подставной ConfigService: те же правила, что у ядра, то есть файлы на диске, атомарная запись и
 * карантин битого файла, но с корнем во временной папке теста.
 */
public final class TestConfigs implements ConfigService {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final Path root;
    private final Map<String, StubFile<?>> files = new LinkedHashMap<>();

    public TestConfigs(Path root) {
        this.root = root;
    }

    @Override
    public <T> ConfigFile<T> open(ConfigSpec<T> spec) {
        String key = spec.scope() + ":" + spec.modid() + "/" + spec.name();
        StubFile<?> existing = files.get(key);
        if (existing != null) {
            return cast(existing);
        }
        StubFile<T> file = new StubFile<>(spec, path(spec));
        files.put(key, file);
        file.load();
        return cast(file);
    }

    @Override
    public Path directory(String modid) {
        return root.resolve("config")
            .resolve(modid);
    }

    @Override
    public void reloadAll() {
        for (StubFile<?> file : files.values()) {
            file.reload();
        }
    }

    public Path settingsPath(String modid, String name) {
        return root.resolve("config")
            .resolve(modid)
            .resolve(name + ".json");
    }

    public Path worldPath(String modid, String name) {
        return root.resolve("world")
            .resolve(modid)
            .resolve(name + ".json");
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigFile<T> cast(StubFile<?> file) {
        return (ConfigFile<T>) file;
    }

    private Path path(ConfigSpec<?> spec) {
        return spec.scope() == ConfigScope.SETTINGS ? settingsPath(spec.modid(), spec.name())
            : worldPath(spec.modid(), spec.name());
    }

    static final class StubFile<T> implements ConfigFile<T> {

        private final ConfigSpec<T> spec;
        private final Path path;
        private T value;

        StubFile(ConfigSpec<T> spec, Path path) {
            this.spec = spec;
            this.path = path;
        }

        @Override
        public T get() {
            if (value == null) {
                throw new IllegalStateException("Config " + spec.name() + " is not loaded");
            }
            return value;
        }

        @Override
        public boolean loaded() {
            return value != null;
        }

        @Override
        public void save() {
            JsonObject data = GSON.toJsonTree(value)
                .getAsJsonObject();
            data.addProperty("schemaVersion", spec.schemaVersion());
            write(path, data);
        }

        @Override
        public void reload() {
            load();
        }

        @Override
        public Path path() {
            return path;
        }

        void load() {
            if (!Files.isRegularFile(path)) {
                value = spec.defaults()
                    .get();
                save();
                return;
            }
            JsonObject data = read(path);
            if (data == null) {
                quarantine(path);
                value = spec.defaults()
                    .get();
                save();
                return;
            }
            value = parse(data);
        }

        @SuppressWarnings("unchecked")
        private T parse(JsonObject data) {
            if (spec.type() == JsonObject.class) {
                return (T) data;
            }
            T parsed = GSON.fromJson(data, spec.type());
            return parsed == null ? spec.defaults()
                .get() : parsed;
        }

        private static void quarantine(Path path) {
            Path broken = path.resolveSibling(path.getFileName() + ".broken");
            try {
                Files.move(path, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        static JsonObject read(Path path) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = new JsonParser().parse(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    return null;
                }
                return parsed.getAsJsonObject();
            } catch (IOException | JsonParseException failure) {
                return null;
            }
        }

        static void write(Path path, JsonObject data) {
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    GSON.toJson(data, writer);
                }
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}

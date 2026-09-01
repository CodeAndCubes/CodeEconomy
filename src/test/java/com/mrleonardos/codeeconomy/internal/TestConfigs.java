package com.mrleonardos.codeeconomy.internal;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.ConfigData;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.SectionSpec;
import com.mrleonardos.codecore.api.config.StorageSettings;

/**
 * Подставной ConfigService: те же пути и то же поведение, что у ядра, то есть файлы на диске, атомарная
 * запись и карантин битого файла, но с корнем во временной папке теста.
 *
 * <p>
 * Содержимое пишется json даже для файлов, объявленных как toml: разбор toml, комментарии и наложение на
 * прочитанное это работа ядра, и проверяется она там же. Отсюда важны имена файлов, папки ролей и то,
 * какие файлы мод вообще открыл.
 */
public final class TestConfigs implements ConfigService {

    /** Папка линейки внутри config и внутри мира. */
    public static final String LINEUP = "code";

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final Path root;
    private final Map<String, StubFile<?>> files = new LinkedHashMap<>();
    private final Map<String, SectionValue<?>> sections = new LinkedHashMap<>();
    private final List<String> opened = new ArrayList<>();

    private StorageSettings storage = new StorageSettings("json", 30);
    private AuditSettings audit = new AuditSettings(true, false);
    private String serverId = "main";

    public TestConfigs(Path root) {
        this.root = root;
    }

    /** Что мод спросит у ядра про хранилище своей роли. */
    public void storage(String provider, int autosaveSeconds) {
        storage = new StorageSettings(provider, autosaveSeconds);
    }

    /** Что мод спросит у ядра про журнал в логе. */
    public void audit(boolean logChanges, boolean logChecks) {
        audit = new AuditSettings(logChanges, logChecks);
    }

    /** Имена файлов, которые мод открыл: отход мода не должен открывать ни одного. */
    public List<String> opened() {
        return opened;
    }

    @Override
    public <T> ConfigFile<T> open(ConfigSpec<T> spec) {
        String key = key(spec);
        StubFile<?> existing = files.get(key);
        if (existing != null) {
            return cast(existing);
        }
        StubFile<T> file = new StubFile<>(spec, path(spec));
        files.put(key, file);
        opened.add(key);
        file.load();
        return cast(file);
    }

    @Override
    public <T> ConfigFile<T> section(SectionSpec<T> spec) {
        SectionValue<?> existing = sections.get(spec.name());
        if (existing != null) {
            throw new IllegalStateException("Section " + spec.name() + " of the main config is already declared");
        }
        SectionValue<T> declared = new SectionValue<>(
            spec.defaults()
                .get());
        sections.put(spec.name(), declared);
        return declared;
    }

    @Override
    public ConfigData main() {
        throw new UnsupportedOperationException("The main config document is the core's own business");
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public StorageSettings storage(String role) {
        return storage;
    }

    @Override
    public AuditSettings audit(String role) {
        return audit;
    }

    @Override
    public Path directory(String role) {
        return root.resolve("config")
            .resolve(LINEUP)
            .resolve(role);
    }

    @Override
    public void reloadAll() {
        for (StubFile<?> file : files.values()) {
            file.reload();
        }
    }

    /** Путь файла по тем же правилам, что у ядра: {@code <владелец>[-<имя>].<расширение>}. */
    public Path path(ConfigSpec<?> spec) {
        Path directory = spec.scope() == ConfigScope.WORLD_STATE ? root.resolve("world")
            .resolve(LINEUP)
            .resolve(spec.role()) : directory(spec.role());
        return directory.resolve(fileName(spec));
    }

    /** Имя файла без папки. */
    public static String fileName(ConfigSpec<?> spec) {
        String owner = spec.modid()
            .startsWith("code")
                ? spec.modid()
                    .substring("code".length())
                : spec.modid();
        String suffix = spec.name()
            .isEmpty() ? "" : "-" + spec.name();
        return owner + suffix
            + spec.format()
                .extension();
    }

    private static String key(ConfigSpec<?> spec) {
        return spec.scope() + ":" + spec.role() + "/" + fileName(spec);
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigFile<T> cast(StubFile<?> file) {
        return (ConfigFile<T>) file;
    }

    /** Секция главного файла: значения живут в памяти, файл пишет ядро. */
    static final class SectionValue<T> implements ConfigFile<T> {

        private final T value;

        SectionValue(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public boolean loaded() {
            return true;
        }

        @Override
        public void save() {}

        @Override
        public void reload() {}

        @Override
        public Path path() {
            return null;
        }
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

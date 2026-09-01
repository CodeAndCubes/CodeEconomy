package com.mrleonardos.codeeconomy.internal;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.ConfigSpec;

/**
 * Настоящий ConfigService ядра поверх временной папки теста.
 *
 * <p>
 * Своей заглушки у мода больше нет: формат файлов, комментарии, раскладка путей и порядок ключей это
 * поведение ядра, и проверять их подделкой значит проверять подделку. Реализация лежит в dev-джаре
 * ядра, который и так стоит на тестовом classpath, поэтому она достаётся по имени класса.
 *
 * <p>
 * Папка мира привязывается сразу: чекпоинт со счетами это состояние мира, и без привязки он бы не
 * открылся. На живом сервере это делает ядро при старте мира.
 */
public final class TestConfigs {

    /** Папка линейки внутри папки конфигов игры и внутри мира. */
    public static final String LINEUP = "code";

    public static final String MAIN_FILE = "config.toml";

    private static final String PATHS_TYPE = "com.mrleonardos.codecore.internal.config.ConfigPaths";
    private static final String SERVICE_TYPE = "com.mrleonardos.codecore.internal.config.ConfigServiceImpl";
    private static final Logger LOG = LogManager.getLogger(TestConfigs.class);

    private final Path root;
    private final Object paths;
    private final ConfigService service;

    private TestConfigs(Path root, Object paths, ConfigService service) {
        this.root = root;
        this.paths = paths;
        this.service = service;
    }

    /**
     * Ядро над {@code <root>/config} с привязанной папкой {@code <root>/world}.
     *
     * <p>
     * Главный файл, если он нужен, пишется до этого вызова: ядро читает его при сборке.
     */
    public static TestConfigs of(Path root) {
        try {
            Class<?> pathsType = Class.forName(PATHS_TYPE);
            Object paths = pathsType.getConstructor(Path.class)
                .newInstance(root.resolve("config"));
            Class<?> serviceType = Class.forName(SERVICE_TYPE);
            Constructor<?> constructor = serviceType.getConstructor(pathsType, Logger.class);
            ConfigService service = (ConfigService) constructor.newInstance(paths, LOG);
            serviceType.getMethod("attachWorld", Path.class)
                .invoke(service, root.resolve("world"));
            return new TestConfigs(root, paths, service);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                "ConfigService ядра не собрался, проверьте dev-джар CodeCore на тестовом classpath",
                failure);
        }
    }

    public ConfigService service() {
        return service;
    }

    public <T> ConfigFile<T> open(ConfigSpec<T> spec) {
        return service.open(spec);
    }

    /** Путь файла по правилам ядра, а не по их копии в тесте. */
    public Path path(ConfigSpec<?> spec) {
        try {
            Method resolve = paths.getClass()
                .getMethod("resolve", ConfigSpec.class);
            return (Path) resolve.invoke(paths, spec);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Путь файла не собрался", failure);
        }
    }

    /** Имя файла по правилам ядра: {@code <владелец>[-<имя>].<расширение>}. */
    public static String fileName(ConfigSpec<?> spec) {
        try {
            Method fileName = Class.forName(PATHS_TYPE)
                .getMethod("fileName", ConfigSpec.class);
            return (String) fileName.invoke(null, spec);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Имя файла не собралось", failure);
        }
    }

    public Path mainFile() {
        return mainFile(root);
    }

    public static Path mainFile(Path root) {
        return root.resolve("config")
            .resolve(LINEUP)
            .resolve(MAIN_FILE);
    }

    /** Написать главный файл линейки до того, как ядро его прочитает. */
    public static void writeMain(Path root, String... lines) {
        write(mainFile(root), lines);
    }

    public static void write(Path file, String... lines) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Файл " + file + " не записан", failure);
        }
    }

    public static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Файл " + file + " не прочитан", failure);
        }
    }
}

package com.airtribe.prism.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DotenvLoader {

    private static final Map<String, String> LOADED = new HashMap<>();
    private static volatile boolean initialized = false;

    public static synchronized Map<String, String> load() {
        if (initialized) {
            return LOADED;
        }

        List<Path> candidatePaths = List.of(
                Path.of(".env"),
                Path.of("prism-java/.env"),
                Path.of("../.env"),
                Path.of("../prism-java/.env")
        );

        for (Path path : candidatePaths) {
            if (Files.isRegularFile(path)) {
                loadFile(path);
            }
        }

        initialized = true;
        return LOADED;
    }

    private static void loadFile(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    LOADED.putIfAbsent(key, value);
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static String get(String key) {
        load();
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }
        return LOADED.get(key);
    }
}

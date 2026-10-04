package com.airtribe.prism.repository;

import com.airtribe.prism.config.GatewayProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.springframework.stereotype.Component;

/** Small durable store for the single-instance capstone deployment. */
@Component
public class PersistentJsonStore {
    private final ObjectMapper objectMapper;
    private final Path directory;

    public PersistentJsonStore(ObjectMapper objectMapper, GatewayProperties properties) {
        this.objectMapper = objectMapper;
        this.directory = Path.of(properties.storagePath());
    }

    public synchronized <T> List<T> readList(String fileName, TypeReference<List<T>> type) {
        Path path = directory.resolve(fileName);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(Files.readString(path), type);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read persistent Prism state: " + path, exception);
        }
    }

    public synchronized void write(String fileName, Object value) {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName);
            Path temporary = directory.resolve(fileName + ".tmp");
            Files.writeString(temporary, objectMapper.writeValueAsString(value));
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot persist Prism state", exception);
        }
    }
}

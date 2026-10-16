package store;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class CsvFile {

    private final Path path;

    public CsvFile(Path path) {
        this.path = path;
    }

    public List<String[]> rows() {
        try {
            return Files.exists(path) ? Files.readAllLines(path).stream().map(l -> l.split(",", -1)).toList() : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<String[]> find(String key) {
        return rows().stream().filter(r -> r[0].equals(key)).findFirst();
    }

    public void upsert(String... row) {
        for (String value : row) {
            if (value.contains(",") || value.contains("\n")) {
                throw new IllegalArgumentException("Commas and line breaks are not allowed: " + value);
            }
        }
        List<String> lines = new ArrayList<>(rows().stream().filter(r -> !r[0].equals(row[0])).map(r -> String.join(",", r)).toList());
        lines.add(String.join(",", row));
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, lines);
            Files.move(tmp, path, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

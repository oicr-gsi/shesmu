package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class JarHashRepository<T> {
  private final Map<Class<?>, Pair<String, String>> jarHashes = new ConcurrentHashMap<>();

  public void add(T item) {
    final CodeSource source = item.getClass().getProtectionDomain().getCodeSource();
    if (source == null) {
      jarHashes.put(item.getClass(), new Pair<>("unknown", "unknown"));
      return;
    }
    final File jarFile = new File(source.getLocation().getPath());
    try (final FileInputStream input = new FileInputStream(jarFile)) {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[1024];
      int bytesRead = 0;

      while ((bytesRead = input.read(buffer)) != -1) digest.update(buffer, 0, bytesRead);

      final StringBuilder sb = new StringBuilder();
      for (byte b : digest.digest()) {
        sb.append(String.format("%02x", b));
      }
      jarHashes.put(item.getClass(), new Pair<>(jarFile.toString(), sb.toString()));
    } catch (NoSuchAlgorithmException | IOException e) {
      e.printStackTrace();
      jarHashes.put(item.getClass(), new Pair<>(jarFile.toString(), e.getMessage()));
    }
  }

  public Stream<Map.Entry<Class<?>, Pair<String, String>>> stream() {
    return jarHashes.entrySet().stream();
  }
}

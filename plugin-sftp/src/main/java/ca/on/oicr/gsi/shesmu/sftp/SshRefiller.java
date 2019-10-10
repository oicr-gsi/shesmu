package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SshRefiller<T> extends Refiller<T> {
  final List<BiConsumer<T, ObjectNode>> writers = new ArrayList<>();
  private final Supplier<SftpServer> server;
  private final String command;
  private final String name;
  private String lastHash = "";

  public SshRefiller(Supplier<SftpServer> server, String name, String command) {
    this.server = server;
    this.command = command;
    this.name = name;
  }

  @Override
  public void consume(Stream<T> items) {
    // For every incoming object, compute its value, hash that value, and stick the value in a JSON
    // array and the hash in a sorted set
    final Set<byte[]> hashes =
        new TreeSet<>(
            (left, right) -> {
              for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
                int a = (left[i] & 0xff);
                int b = (right[j] & 0xff);
                if (a != b) {
                  return a - b;
                }
              }
              return left.length - right.length;
            });
    final ArrayNode output = SftpServer.MAPPER.createArrayNode();
    items.forEach(
        item -> {
          final ObjectNode outputNode = output.addObject();
          for (final BiConsumer<T, ObjectNode> writer : writers) {
            writer.accept(item, outputNode);
          }
          try {
            hashes.add(
                MessageDigest.getInstance("SHA1")
                    .digest(SftpServer.MAPPER.writeValueAsBytes(outputNode)));
          } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        });

    // Now, we can take a hash of the sorted hashes and be confident that if the data is the same,
    // the has will be the same even if the data was supplied in a different order
    try {
      final MessageDigest digest = MessageDigest.getInstance("SHA1");
      for (final byte[] hash : hashes) {
        digest.update(hash);
      }
      final String totalHash = Utils.bytesToHex(digest.digest());
      if (totalHash.equals(lastHash)) {
        return;
      }
      if (server.get().refill(name, command + " " + totalHash, output)) {
        lastHash = totalHash;
      }

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}

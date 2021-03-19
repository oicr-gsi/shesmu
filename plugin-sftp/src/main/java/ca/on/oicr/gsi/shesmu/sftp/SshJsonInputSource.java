package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.input.JsonInputSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;

public class SshJsonInputSource implements JsonInputSource {
  private final String command;
  private final SshConnectionPool connections;
  private final Optional<Integer> ttl;

  public SshJsonInputSource(SshConnectionPool connections, Optional<Integer> ttl, String command) {
    this.connections = connections;
    this.ttl = ttl;
    this.command = command;
  }

  @Override
  public InputStream fetch() throws Exception {

    final var connection = connections.get();
    final var session = connection.client().startSession();
    final var process = session.exec(command);
    final var stream = process.getInputStream();
    return new InputStream() {
      @Override
      public int available() throws IOException {
        return stream.available();
      }

      @Override
      public void close() throws IOException {
        stream.close();
        process.join();
        try (final var err = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
          System.err.printf("=== SSH Errors from %s ===\n", command);
          err.lines().forEach(System.err::println);
          System.err.println("=== END ===");
        }
        session.close();
        connection.close();
      }

      @Override
      public synchronized void mark(int readlimit) {
        stream.mark(readlimit);
      }

      @Override
      public boolean markSupported() {
        return stream.markSupported();
      }

      @Override
      public int read(byte[] b) throws IOException {
        return stream.read(b);
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        return stream.read(b, off, len);
      }

      @Override
      public int read() throws IOException {
        return stream.read();
      }

      @Override
      public synchronized void reset() throws IOException {
        stream.reset();
      }

      @Override
      public long skip(long n) throws IOException {
        return stream.skip(n);
      }
    };
  }

  @Override
  public Optional<Integer> ttl() {
    return ttl;
  }
}

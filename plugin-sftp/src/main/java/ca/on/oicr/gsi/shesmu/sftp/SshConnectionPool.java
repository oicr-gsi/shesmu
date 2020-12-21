package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.sftp.SshConnectionPool.PooledSshConnection;
import java.io.IOError;
import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

public class SshConnectionPool implements Supplier<PooledSshConnection>, AutoCloseable {
  private final class ConnectionInfo {
    private final long epoch = Instant.now().toEpochMilli();
    private final String host;
    private final int port;
    private final String user;

    private ConnectionInfo(String host, int port, String user) {
      this.host = host;
      this.user = user;
      this.port = port;
    }
  }

  public final class PooledSshConnection implements AutoCloseable {
    private final SSHClient client;
    private final long epoch;
    private final SFTPClient sftp;

    public PooledSshConnection(ConnectionInfo info) throws IOException {
      this.epoch = info.epoch;
      client = new SSHClient();
      client.addHostKeyVerifier(new PromiscuousVerifier());
      client.connect(info.host, info.port);
      client.authPublickey(info.user);
      sftp = client.newSFTPClient();
    }

    public SSHClient client() {
      return client;
    }

    @Override
    public void close() {
      if (client.isConnected()) {
        connections.add(this);
      }
    }

    private void destroy() {
      try {
        client.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public SFTPClient sftp() {
      return sftp;
    }
  }

  private AtomicReference<ConnectionInfo> connectionInfo = new AtomicReference<>();
  private final Deque<PooledSshConnection> connections = new ConcurrentLinkedDeque<>();

  @Override
  public void close() {
    PooledSshConnection connection;
    while ((connection = connections.pollLast()) != null) {
      connection.destroy();
    }
  }

  public void configure(String host, int port, String user) {
    final ConnectionInfo info = new ConnectionInfo(host, port, user);
    connectionInfo.updateAndGet(i -> i != null && i.epoch > info.epoch ? i : info);
  }

  @Override
  public PooledSshConnection get() {
    final ConnectionInfo info = connectionInfo.get();
    if (info == null) {
      throw new IllegalStateException("Connection pool is not initalised");
    }
    PooledSshConnection connection;
    while ((connection = connections.pollLast()) != null) {
      if (connection.epoch < info.epoch) {
        connection.destroy();
      } else {
        return connection;
      }
    }
    try {
      return new PooledSshConnection(info);
    } catch (IOException e) {
      throw new IOError(e);
    }
  }
}

package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.sftp.SshConnectionPool.PooledSshConnection;
import io.prometheus.client.Counter;
import java.io.IOError;
import java.io.IOException;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

public class SshConnectionPool implements Supplier<PooledSshConnection>, AutoCloseable {
  private static final class ConnectionInfo {
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
    private Long acquisition = System.nanoTime();
    private final SSHClient client;
    private final ConnectionInfo info;
    private final SFTPClient sftp;

    public PooledSshConnection(ConnectionInfo info) throws IOException {
      this.info = info;
      try {
        client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(info.host, info.port);
        client.authPublickey(info.user);
        sftp = client.newSFTPClient();
        createdConnections.labels(info.host, Integer.toString(info.port), info.user).inc();
      } catch (IOException e) {
        maxConnections.release();
        throw e;
      }
    }

    public SSHClient client() {
      return client;
    }

    @Override
    public void close() {
      if (acquisition != null) {
        connectionLife.observe(acquisition, info.host, Integer.toString(info.port), info.user);
        acquisition = null;
      }
      if (client.isConnected()) {
        connections.offerLast(this);
      } else {
        destroy();
      }
    }

    private void destroy() {
      maxConnections.release();
      destroyedConnections.labels(info.host, Integer.toString(info.port), info.user).inc();
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

  private static final LatencyHistogram connectionLife =
      new LatencyHistogram(
          "shesmu_ssh_pool_conn_lifespan",
          "The length of time a connection is used",
          "host",
          "port",
          "user");
  private static final Counter createdConnections =
      Counter.build("shesmu_ssh_pool_conn_created", "The number of SSH connections created")
          .labelNames("host", "port", "user")
          .register();
  private static final Counter destroyedConnections =
      Counter.build("shesmu_ssh_pool_conn_destroyed", "The number of SSH connections destroyed")
          .labelNames("host", "port", "user")
          .register();
  private final AtomicReference<ConnectionInfo> connectionInfo = new AtomicReference<>();
  private final Deque<PooledSshConnection> connections = new ConcurrentLinkedDeque<>();
  private final Semaphore maxConnections = new Semaphore(100);

  @Override
  public void close() {
    PooledSshConnection connection;
    while ((connection = connections.pollLast()) != null) {
      connection.destroy();
    }
  }

  public void configure(String host, int port, String user) {
    final var info = new ConnectionInfo(host, port, user);
    connectionInfo.updateAndGet(i -> i != null && i.epoch > info.epoch ? i : info);
  }

  @Override
  public PooledSshConnection get() {
    int attempts = 100;
    final var info = connectionInfo.get();
    if (info == null) {
      throw new IllegalStateException("Connection pool is not initialised");
    }
    PooledSshConnection connection;
    while (attempts-- > 0) {
      while ((connection = connections.pollLast()) != null) {
        if (connection.info.epoch < info.epoch) {
          connection.destroy();
        } else {
          connection.acquisition = System.nanoTime();
          return connection;
        }
      }
      if (maxConnections.tryAcquire()) {
        try {
          return new PooledSshConnection(info);
        } catch (IOException e) {
          throw new IOError(e);
        }
      } else {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
    throw new IllegalStateException("Exhausted maximum attempts waiting to get an SSH connection.");
  }
}

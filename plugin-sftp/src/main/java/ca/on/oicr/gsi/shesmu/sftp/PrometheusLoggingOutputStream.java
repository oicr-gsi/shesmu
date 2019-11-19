package ca.on.oicr.gsi.shesmu.sftp;

import io.prometheus.client.Gauge;
import java.io.IOException;
import java.io.OutputStream;

public class PrometheusLoggingOutputStream extends OutputStream {
  private long count;
  private final Gauge gauge;
  private final OutputStream inner;
  private final String[] labels;

  public PrometheusLoggingOutputStream(OutputStream inner, Gauge gauge, String... labels) {
    this.inner = inner;
    this.gauge = gauge;
    this.labels = labels;
  }

  @Override
  public void close() throws IOException {
    inner.close();
    gauge.labels(labels).set(count);
  }

  @Override
  public void flush() throws IOException {
    inner.flush();
  }

  @Override
  public void write(byte[] b) throws IOException {
    inner.write(b);
    count += b.length;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    inner.write(b, off, len);
    count += len;
  }

  @Override
  public void write(int i) throws IOException {
    inner.write(i);
    count++;
  }
}

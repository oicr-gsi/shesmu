package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.function.DoubleSupplier;

/** Interface for caches so that records can communicate with their containers */
public interface Owner {
  DoubleSupplier CPU_TIME =
      ManagementFactory.getThreadMXBean().isCurrentThreadCpuTimeSupported()
          ? new DoubleSupplier() {
            private final ThreadMXBean bean = ManagementFactory.getThreadMXBean();

            @Override
            public double getAsDouble() {
              return bean.getCurrentThreadCpuTime() / 1E9;
            }
          }
          : () -> 0.0;
  /** The name of the cache for use in monitoring */
  String name();

  /** The time-to-live for a record in cache */
  long ttl();
}

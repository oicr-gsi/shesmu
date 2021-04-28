package ca.on.oicr.gsi.shesmu.server;

import java.util.concurrent.ThreadFactory;

public class ShesmuThreadFactory implements ThreadFactory {
  public static void unhandledException(Thread thread, Throwable throwable) {
    System.err.printf("Unhandled error in thread %s (%d)\n", thread.getName(), thread.getId());
    throwable.printStackTrace();
  }

  private int id;
  private final String prefix;
  private final int priority;

  public ShesmuThreadFactory(String prefix, int priority) {
    this.prefix = prefix;
    this.priority = priority;
  }

  @Override
  public Thread newThread(Runnable runnable) {
    final var thread = new Thread(runnable);
    thread.setName(prefix + "-" + id++);
    thread.setPriority(priority);
    thread.setUncaughtExceptionHandler(ShesmuThreadFactory::unhandledException);
    return thread;
  }
}

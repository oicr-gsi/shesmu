package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class CallSiteRegistry<K> {
  private final Map<K, WeakReference<MutableCallSite>> registry = new ConcurrentHashMap<>();

  public CallSiteRegistry() {
    super();
  }

  public MutableCallSite get(K name) {
    return registry.get(name).get();
  }

  public Stream<Pair<K, MethodType>> stream() {
    return registry
        .entrySet()
        .stream()
        .filter(e -> e.getValue().get() != null)
        .map(e -> new Pair<>(e.getKey(), e.getValue().get().type()));
  }

  public MutableCallSite upsert(K name, MethodHandle handle) {
    // Now, go check our map of call sites that translate file names to instances.
    // We need to get a mutable call site from the global cache. It's possible that
    // we've done this before, but then the file and any olives using it were
    // deleted, so we'll end up with a dead reference. Either way, if there's no
    // useful call site, create one.
    MutableCallSite callsite =
        registry
            .compute(
                name,
                (n, reference) ->
                    reference == null || reference.get() == null
                        ? new WeakReference<>(new MutableCallSite(handle))
                        : reference)
            .get();
    // Update this call site with our current reference. If we just created it, this
    // is redundant.
    callsite.setTarget(handle);
    return callsite;
  }
}

import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.ratelimit.RateLimitThrottler;

module ca.on.oicr.gsi.shesmu.plugin.ratelimit {
  exports ca.on.oicr.gsi.shesmu.ratelimit;

  requires ca.on.oicr.gsi.serverutils;
  requires ca.on.oicr.gsi.shesmu;
  requires com.fasterxml.jackson.databind;
  requires simpleclient;

  provides PluginFileType with
      RateLimitThrottler;
}

package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import java.io.IOException;
import java.io.OutputStream;

public abstract class Extractor {
  public interface ExtractVisitor {
    OutputStream error() throws IOException;

    OutputStream success(String mimeType) throws IOException;
  }

  public abstract void run(InputProvider input, ExtractVisitor visitor) throws IOException;
}

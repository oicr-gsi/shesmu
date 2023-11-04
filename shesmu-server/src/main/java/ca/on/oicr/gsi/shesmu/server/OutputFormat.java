package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.OptionalImyhat;
import java.util.stream.Stream;

public enum OutputFormat {
  CSV_EXCEL(false, OutputMime.CSV),
  CSV_MONGO(false, OutputMime.CSV),
  CSV_MYSQL(false, OutputMime.CSV),
  CSV_POSTGRESQL(false, OutputMime.CSV),
  CSV_RFC4180(false, OutputMime.CSV),
  JSON(true, OutputMime.JSON),
  JSON_SECS(true, OutputMime.JSON),
  JSON_MILLIS(true, OutputMime.JSON),
  TSV(false, OutputMime.TSV),
  TSV_MONGO(false, OutputMime.TSV),
  XML(true, OutputMime.XML),
  XML_SECS(true, OutputMime.XML),
  XML_MILLIS(true, OutputMime.XML);

  public enum OutputMime {
    CSV,
    JSON,
    TSV,
    XML;

    public String mimeType() {
      return switch (this) {
        case CSV -> "text/csv";
        case JSON -> "application/json";
        case TSV -> "text/tab-separated-values";
        case XML -> "text/xml";
      };
    }
  }

  static boolean isStringable(Imyhat type) {
    return Stream.of(
            Imyhat.FLOAT, Imyhat.INTEGER, Imyhat.DATE, Imyhat.JSON, Imyhat.PATH, Imyhat.STRING)
        .anyMatch((type instanceof OptionalImyhat optional ? optional.inner() : type)::isSame);
  }

  private final boolean anyTypeAllowed;
  private final OutputMime outputMime;

  OutputFormat(boolean anyTypeAllowed, OutputMime outputMime) {
    this.anyTypeAllowed = anyTypeAllowed;
    this.outputMime = outputMime;
  }

  public boolean isAllowedType(Imyhat type) {
    return anyTypeAllowed || isStringable(type);
  }

  public final String mimeType() {
    return outputMime.mimeType();
  }
}

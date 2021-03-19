package ca.on.oicr.gsi.shesmu.pinery.barcodes;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;

/** @author mlaszloffy */
public class BasesMask {

  private final Integer readOneIncludeLength;
  private final Integer readOneIgnoreLength;

  private final Integer indexOneIncludeLength;
  private final Integer indexOneIgnoreLength;

  private final Integer indexTwoIncludeLength;
  private final Integer indexTwoIgnoreLength;

  private final Integer readTwoIncludeLength;
  private final Integer readTwoIgnoreLength;

  public BasesMask(
      Integer readOneIncludeLength,
      Integer readOneIgnoreLength,
      Integer indexOneIncludeLength,
      Integer indexOneIgnoreLength,
      Integer indexTwoIncludeLength,
      Integer indexTwoIgnoreLength,
      Integer readTwoIncludeLength,
      Integer readTwoIgnoreLength) {
    this.readOneIncludeLength = readOneIncludeLength;
    this.readOneIgnoreLength = readOneIgnoreLength;
    this.indexOneIncludeLength = indexOneIncludeLength;
    this.indexOneIgnoreLength = indexOneIgnoreLength;
    this.indexTwoIncludeLength = indexTwoIncludeLength;
    this.indexTwoIgnoreLength = indexTwoIgnoreLength;
    this.readTwoIncludeLength = readTwoIncludeLength;
    this.readTwoIgnoreLength = readTwoIgnoreLength;
  }

  public BasesMask(
      String readOneIncludeLength,
      String readOneIgnoreLength,
      String indexOneIncludeLength,
      String indexOneIgnoreLength,
      String indexTwoIncludeLength,
      String indexTwoIgnoreLength,
      String readTwoIncludeLength,
      String readTwoIgnoreLength) {
    this.readOneIncludeLength = getInt(readOneIncludeLength);
    this.readOneIgnoreLength = getInt(readOneIgnoreLength);
    this.indexOneIncludeLength = getInt(indexOneIncludeLength);
    this.indexOneIgnoreLength = getInt(indexOneIgnoreLength);
    this.indexTwoIncludeLength = getInt(indexTwoIncludeLength);
    this.indexTwoIgnoreLength = getInt(indexTwoIgnoreLength);
    this.readTwoIncludeLength = getInt(readTwoIncludeLength);
    this.readTwoIgnoreLength = getInt(readTwoIgnoreLength);
  }

  public Integer getReadOneIncludeLength() {
    return readOneIncludeLength;
  }

  public Integer getReadOneIgnoreLength() {
    return readOneIgnoreLength;
  }

  public Integer getIndexOneIncludeLength() {
    return indexOneIncludeLength;
  }

  public Integer getIndexOneIgnoreLength() {
    return indexOneIgnoreLength;
  }

  public Integer getIndexTwoIncludeLength() {
    return indexTwoIncludeLength;
  }

  public Integer getIndexTwoIgnoreLength() {
    return indexTwoIgnoreLength;
  }

  public Integer getReadTwoIncludeLength() {
    return readTwoIncludeLength;
  }

  public Integer getReadTwoIgnoreLength() {
    return readTwoIgnoreLength;
  }

  private String getVal(String prefix, Integer length) {
    if (length != null && length != Integer.MAX_VALUE) {
      return prefix + length.toString();
    } else if (length != null && length == Integer.MAX_VALUE) {
      return prefix + "*";
    } else if (length != null && length == 0) {
      return "";
    } else {
      return "";
    }
  }

  private Integer getInt(String length) {
    if (length == null) {
      return null;
    } else if ("*".equals(length)) {
      return Integer.MAX_VALUE;
    } else if (StringUtils.isNumeric(length)) {
      Integer i = Integer.parseInt(length);
      if (i == 0) {
        return null;
      } else {
        return Integer.parseInt(length);
      }
    } else {
      throw new IllegalArgumentException("Input value [" + length + "] is not supported");
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getVal("y", readOneIncludeLength));
    sb.append(getVal("n", readOneIgnoreLength));
    if (indexOneIncludeLength != null || indexOneIgnoreLength != null) {
      sb.append(",");
      sb.append(getVal("i", indexOneIncludeLength));
      sb.append(getVal("n", indexOneIgnoreLength));
    }
    if (indexTwoIncludeLength != null || indexTwoIgnoreLength != null) {
      sb.append(",");
      sb.append(getVal("i", indexTwoIncludeLength));
      sb.append(getVal("n", indexTwoIgnoreLength));
    }
    if (readTwoIncludeLength != null || readTwoIgnoreLength != null) {
      sb.append(",");
      sb.append(getVal("y", readTwoIncludeLength));
      sb.append(getVal("n", readTwoIgnoreLength));
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 83 * hash + Objects.hashCode(this.readOneIncludeLength);
    hash = 83 * hash + Objects.hashCode(this.readOneIgnoreLength);
    hash = 83 * hash + Objects.hashCode(this.indexOneIncludeLength);
    hash = 83 * hash + Objects.hashCode(this.indexOneIgnoreLength);
    hash = 83 * hash + Objects.hashCode(this.indexTwoIncludeLength);
    hash = 83 * hash + Objects.hashCode(this.indexTwoIgnoreLength);
    hash = 83 * hash + Objects.hashCode(this.readTwoIncludeLength);
    hash = 83 * hash + Objects.hashCode(this.readTwoIgnoreLength);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final BasesMask other = (BasesMask) obj;
    if (!Objects.equals(this.readOneIncludeLength, other.readOneIncludeLength)) {
      return false;
    }
    if (!Objects.equals(this.readOneIgnoreLength, other.readOneIgnoreLength)) {
      return false;
    }
    if (!Objects.equals(this.indexOneIncludeLength, other.indexOneIncludeLength)) {
      return false;
    }
    if (!Objects.equals(this.indexOneIgnoreLength, other.indexOneIgnoreLength)) {
      return false;
    }
    if (!Objects.equals(this.indexTwoIncludeLength, other.indexTwoIncludeLength)) {
      return false;
    }
    if (!Objects.equals(this.indexTwoIgnoreLength, other.indexTwoIgnoreLength)) {
      return false;
    }
    if (!Objects.equals(this.readTwoIncludeLength, other.readTwoIncludeLength)) {
      return false;
    }
    return Objects.equals(this.readTwoIgnoreLength, other.readTwoIgnoreLength);
  }

  public static BasesMask fromString(String basesMaskString) {
    Pattern p =
        Pattern.compile(
            "y(?<readOneInclude>\\*|\\d+)(n(?<readOneIgnore>\\*|\\d+))?"
                + "(,(i(?<indexOneInclude>\\*|\\d+))?(n(?<indexOneIgnore>\\*|\\d+))?)?"
                + "(,?(i(?<indexTwoInclude>\\*|\\d+))?(n(?<indexTwoIgnore>\\*|\\d+))?)?"
                + "(,?(y(?<readTwoInclude>\\*|\\d+))?(n(?<readTwoIgnore>\\*|\\d+))?)?",
            Pattern.CASE_INSENSITIVE);
    if (p.matcher(basesMaskString).matches()) {
      Matcher m = p.matcher(basesMaskString);
      m.find();
      return new BasesMask(
          m.group("readOneInclude"), m.group("readOneIgnore"),
          m.group("indexOneInclude"), m.group("indexOneIgnore"),
          m.group("indexTwoInclude"), m.group("indexTwoIgnore"),
          m.group("readTwoInclude"), m.group("readTwoIgnore"));
    } else {
      return null;
    }
  }

  public static class BasesMaskBuilder {

    private Integer readOneIncludeLength = null;
    private Integer readOneIgnoreLength = null;
    private Integer indexOneIncludeLength = null;
    private Integer indexOneIgnoreLength = null;
    private Integer indexTwoIncludeLength = null;
    private Integer indexTwoIgnoreLength = null;
    private Integer readTwoIncludeLength = null;
    private Integer readTwoIgnoreLength = null;

    public BasesMaskBuilder() {}

    @Override
    public String toString() {
      return "BasesMaskBuilder{"
          + "readOneIncludeLength="
          + readOneIncludeLength
          + ", readOneIgnoreLength="
          + readOneIgnoreLength
          + ", indexOneIncludeLength="
          + indexOneIncludeLength
          + ", indexOneIgnoreLength="
          + indexOneIgnoreLength
          + ", indexTwoIncludeLength="
          + indexTwoIncludeLength
          + ", indexTwoIgnoreLength="
          + indexTwoIgnoreLength
          + ", readTwoIncludeLength="
          + readTwoIncludeLength
          + ", readTwoIgnoreLength="
          + readTwoIgnoreLength
          + '}';
    }

    public void setReadOneIncludeLength(Integer readOneIncludeLength) {
      this.readOneIncludeLength = readOneIncludeLength;
    }

    public void setReadOneIgnoreLength(Integer readOneIgnoreLength) {
      this.readOneIgnoreLength = readOneIgnoreLength;
    }

    public void setIndexOneIncludeLength(Integer indexOneIncludeLength) {
      this.indexOneIncludeLength = indexOneIncludeLength;
    }

    public void setIndexOneIgnoreLength(Integer indexOneIgnoreLength) {
      this.indexOneIgnoreLength = indexOneIgnoreLength;
    }

    public void setIndexTwoIncludeLength(Integer indexTwoIncludeLength) {
      this.indexTwoIncludeLength = indexTwoIncludeLength;
    }

    public void setIndexTwoIgnoreLength(Integer indexTwoIgnoreLength) {
      this.indexTwoIgnoreLength = indexTwoIgnoreLength;
    }

    public void setReadTwoIncludeLength(Integer readTwoIncludeLength) {
      this.readTwoIncludeLength = readTwoIncludeLength;
    }

    public void setReadTwoIgnoreLength(Integer readTwoIgnoreLength) {
      this.readTwoIgnoreLength = readTwoIgnoreLength;
    }

    public BasesMask createBasesMask() {
      return new BasesMask(
          readOneIncludeLength,
          readOneIgnoreLength,
          indexOneIncludeLength,
          indexOneIgnoreLength,
          indexTwoIncludeLength,
          indexTwoIgnoreLength,
          readTwoIncludeLength,
          readTwoIgnoreLength);
    }
  }
}

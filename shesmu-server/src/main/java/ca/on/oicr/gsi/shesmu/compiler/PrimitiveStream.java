package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.LambdaBuilder.LambdaType;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.DoubleSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import org.objectweb.asm.Type;

public enum PrimitiveStream {
  DOUBLE(
      "mapToDouble",
      Type.getType(DoubleStream.class),
      Type.getType(DoubleSummaryStatistics.class),
      Type.DOUBLE_TYPE) {
    @Override
    public LambdaType lambdaOf(Imyhat input) {
      return LambdaBuilder.toDoubleFunction(input);
    }
  },
  LONG(
      "mapToLong",
      Type.getType(LongStream.class),
      Type.getType(LongSummaryStatistics.class),
      Type.LONG_TYPE) {
    @Override
    public LambdaType lambdaOf(Imyhat input) {
      return LambdaBuilder.toLongFunction(input);
    }
  };

  private final String methodName;
  private final Type outputStreamType;
  private final Type resultType;
  private final Type summaryStatisticsType;

  PrimitiveStream(
      String methodName, Type outputStreamType, Type summaryStatisticsType, Type resultType) {
    this.methodName = methodName;
    this.outputStreamType = outputStreamType;
    this.summaryStatisticsType = summaryStatisticsType;
    this.resultType = resultType;
  }

  public abstract LambdaType lambdaOf(Imyhat input);

  public String methodName() {
    return methodName;
  }

  public Type outputStreamType() {
    return outputStreamType;
  }

  public Type resultType() {
    return resultType;
  }

  public Type summaryStatisticsType() {
    return summaryStatisticsType;
  }
}

package ca.on.oicr.gsi.shesmu.guanyin;

import static org.objectweb.asm.Type.LONG_TYPE;

import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.guanyin.ReportActionRepository.GuanyinFile;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;

public class ReportDefinition extends ActionDefinition {
	private static final Type ACTION_TYPE = Type.getType(RunReport.class);

	private final RuntimeBinding<GuanyinFile>.CustomBinding guanyin;
	private final long reportId;
	private final String reportName;

	public ReportDefinition(RuntimeBinding<GuanyinFile>.CustomBinding guanyin, long reportId, String name,
			String version, String category, Stream<ParameterDefinition> parameters) {
		super(name + "_" + version.replaceAll("[^A-Za-z0-9_]", "_"), ACTION_TYPE,
				String.format("Runs report %s-%s (%d) on Guanyin instance defined in %s.", name, version, reportId,
						guanyin.fileName()),
				parameters);
		this.guanyin = guanyin;
		this.reportId = reportId;
		reportName = String.format("%s %s[%s]", name, version, category);
	}

	@Override
	public void initialize(GeneratorAdapter methodGen) {
		methodGen.newInstance(ACTION_TYPE);
		methodGen.dup();
		guanyin.push(methodGen);
		methodGen.push(reportId);
		methodGen.push(reportName);
		methodGen.invokeConstructor(ACTION_TYPE, new Method("<init>", Type.VOID_TYPE,
				new Type[] { guanyin.type(), LONG_TYPE, Type.getType(String.class) }));
	}

}

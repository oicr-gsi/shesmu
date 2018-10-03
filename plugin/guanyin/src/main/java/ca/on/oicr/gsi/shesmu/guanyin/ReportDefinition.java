package ca.on.oicr.gsi.shesmu.guanyin;

import static org.objectweb.asm.Type.LONG_TYPE;

import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;

public class ReportDefinition extends ActionDefinition {
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type ACTION_TYPE = Type.getType(RunReport.class);

	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, A_STRING_TYPE, LONG_TYPE, A_STRING_TYPE });

	private final String drmaaPsk;
	private final String drmaaUrl;
	private final long reportId;
	private final String script;
	private final String 观音Url;
	private final String reportName;

	public ReportDefinition(String 观音Url, String drmaaUrl, String drmaaPsk, String script, long reportId, String name,
			String version, String category, Stream<ParameterDefinition> parameters) {
		super(name + "_" + version.replaceAll("[^A-Za-z0-9_]", "_"), ACTION_TYPE,
				String.format("Runs report %s-%s (%d) in %s from %s using %s.", name, version, reportId, script, 观音Url,
						drmaaUrl),
				parameters);
		this.观音Url = 观音Url;
		this.drmaaUrl = drmaaUrl;
		this.drmaaPsk = drmaaPsk;
		this.script = script;
		this.reportId = reportId;
		this.reportName = String.format("%s %s[%s]", name, version, category);
	}

	@Override
	public void initialize(GeneratorAdapter methodGen) {
		methodGen.newInstance(ACTION_TYPE);
		methodGen.dup();
		methodGen.push(观音Url);
		methodGen.push(drmaaUrl);
		methodGen.push(drmaaPsk);
		methodGen.push(script);
		methodGen.push(reportId);
		methodGen.push(reportName);
		methodGen.invokeConstructor(ACTION_TYPE, CTOR);
	}

}

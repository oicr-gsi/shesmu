package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class OliveClauseNodeSmash extends OliveClauseNodeBaseBy<SmashNode> {

	public OliveClauseNodeSmash(int line, int column, List<SmashNode> smashes, List<String> discriminators) {
		super("Smash", line, column, smashes, discriminators);
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		children().forEach(expression -> expression.collectFreeVariables(freeVariables));

		oliveBuilder.line(line);
		final RegroupVariablesBuilder smasher = oliveBuilder.smash(oliveBuilder.loadableValues()
				.filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));

		discriminators().forEach(discriminator -> {
			smasher.addKey(discriminator.type().asmType(), discriminator.name(), context -> {
				context.loadStream();
				context.methodGen().invokeVirtual(context.streamType(),
						new Method(discriminator.name(), discriminator.type().asmType(), new Type[] {}));
			});
		});
		children().forEach(smash -> smash.render(smasher, builder));
		smasher.finish();

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}
}

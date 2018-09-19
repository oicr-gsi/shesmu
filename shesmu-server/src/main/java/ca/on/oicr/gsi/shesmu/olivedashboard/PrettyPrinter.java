package ca.on.oicr.gsi.shesmu.olivedashboard;

public interface PrettyPrinter {
	public static PrettyPrinter keyword(String keyword) {
		return new PrettyPrinter() {

			@Override
			public boolean forceLineBreak() {
				return false;
			}

			@Override
			public int linelength() {
				return keyword.length();
			}

			@Override
			public void write(int indent, StringBuilder builder) {
				builder.append(keyword);
			}
		};
	}

	public static PrettyPrinter literal(String literal) {
		return new PrettyPrinter() {

			@Override
			public boolean forceLineBreak() {
				return false;
			}

			@Override
			public int linelength() {
				return literal.length();
			}

			@Override
			public void write(int indent, StringBuilder builder) {
				builder.append("<span class=\"\">");
				builder.append(literal);
				builder.append("</span>");
			}

		};
	}

	public static PrettyPrinter symbol(String symbol) {
		return new PrettyPrinter() {

			@Override
			public boolean forceLineBreak() {
				return false;
			}

			@Override
			public int linelength() {
				return symbol.length();
			}

			@Override
			public void write(int indent, StringBuilder builder) {
				builder.append(symbol);
			}

		};
	}

	public boolean forceLineBreak();

	public int linelength();

	public void write(int indent, StringBuilder builder);
}

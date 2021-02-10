package ca.on.oicr.gsi.shesmu.compiler;

public interface ImportRewriter {

  ImportRewriter NULL =
      new ImportRewriter() {
        @Override
        public String rewrite(String name) {
          return name;
        }

        @Override
        public String strip(String name) {
          return name;
        }
      };

  String rewrite(String name);

  String strip(String name);
}

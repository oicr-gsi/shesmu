ace.define(
  "ace/mode/shesmu_highlight_rules",
  [
    "require",
    "exports",
    "module",
    "ace/lib/oop",
    "ace/mode/text_highlight_rules"
  ],
  function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextHighlightRules = require("./text_highlight_rules")
      .TextHighlightRules;

    var escapeRe = "\\\\(?:[\\\\0abtrn;#=:]|x[a-fA-F\\d]{4})";

    var ShesmuHighlightRules = function() {
      var keywordMapper = (this.$keywords = this.createKeywordMapper(
        {
          "keyword.control":
            "Alert|All|Annotations|Any|Argument|As|Begin|By|Count|Default|Define|Description|Dict|Distinct|Dump|Else|End|EpochMilli|EpochSecond|Export|First|Fixed|FixedConcat|Flatten|For|Frequency|From|Function|Group|If|In|Into|Input|Join|Labels|LeftJoin|Let|LexicalConcat|Limit|List|Location|Max|Min|Monitor|None|Olive|OnlyIf|PartitionCount|Prefix|Pick|Reduce|Refill|Reject|Require|Return|Reverse|RequiredServices|Run|Univalued|Skip|Sort|Splitting|Squish|Subsample|Switch|Tag|Then|Timeout|To|TypeAlias|Using|When|Where|While|With|Without|Zipping",
          "storage.type": "boolean|date|float|integer|json|path|string",
          "keyword.operator": "`|~|:|<=?|>=?|==|\\|\\||-|!=?|/|\\*|&&|\\?",
          "constant.language":
            "False|True|json_signature|sha1_signature|signature_names|\\d+[kMG]i?|\\d+(weeks|days|hours|mins)|Date\\s*\\d\\d\\d\\d-\\d\\d-\\d\\d\\(T\\d\\d\\:\\d\\d:\\d\\d\\(Z|[+-]\\d\\d\\(:\\d\\d)?))?"
        },
        "identifier"
      ));
      this.$rules = {
        start: [
          {
            token: keywordMapper,
            regex: "[a-zA-Z_$][a-zA-Z0-9_$]*"
          },
          {
            token: "punctuation.definition.comment.shesmu",
            regex: "#.*",
            push_: [
              {
                token: "comment.line.number-sign.shesmu",
                regex: "$|^",
                next: "pop"
              },
              {
                defaultToken: "comment.line.number-sign.shesmu"
              }
            ]
          },
          {
            token: [
              "keyword.other.definition.shesmu",
              "text",
              "punctuation.separator.key-value.shesmu"
            ],
            regex: "\\b([a-zA-Z0-9_]+)\\b(\\s*)(=)"
          },
          {
            token: "punctuation.definition.string.begin.shesmu",
            regex: "'",
            push: [
              {
                token: "punctuation.definition.string.end.shesmu",
                regex: "'",
                next: "pop"
              },
              {
                token: "constant.language.escape",
                regex: escapeRe
              },
              {
                defaultToken: "string.quoted.single.shesmu"
              }
            ]
          },
          {
            token: "punctuation.definition.regex.begin.shesmu",
            regex: "/",
            push: [
              {
                token: "constant.language.escape",
                regex: /\\[\\\[\]AbBdDGsSwWzZ\/.]|[\[\].*?^$()]/
              },
              {
                token: "punctuation.definition.regex.end.shesmu",
                regex: "/[ceimsu]*",
                next: "pop"
              },
              {
                defaultToken: "string.regex.shesmu"
              }
            ]
          },
          {
            token: "punctuation.definition.string.begin.shesmu",
            regex: '"',
            push: [
              {
                token: "constant.language.escape",
                regex: escapeRe
              },
              {
                token: "punctuation.definition.string.end.shesmu",
                regex: '"',
                next: "pop"
              },
              {
                defaultToken: "string.quoted.double.shesmu"
              }
            ]
          }
        ]
      };

      this.normalizeRules();
    };

    ShesmuHighlightRules.metaData = {
      fileTypes: ["shesmu"],
      keyEquivalent: "^~S",
      name: "Shesmu",
      scopeName: "source.shesmu"
    };

    oop.inherits(ShesmuHighlightRules, TextHighlightRules);

    exports.ShesmuHighlightRules = ShesmuHighlightRules;
  }
);

ace.define(
  "ace/mode/folding/shesmu",
  [
    "require",
    "exports",
    "module",
    "ace/lib/oop",
    "ace/range",
    "ace/mode/folding/fold_mode"
  ],
  function(require, exports, module) {
    "use strict";

    var oop = require("../../lib/oop");
    var Range = require("../../range").Range;
    var BaseFoldMode = require("./fold_mode").FoldMode;

    var FoldMode = (exports.FoldMode = function() {});
    oop.inherits(FoldMode, BaseFoldMode);

    (function() {
      this.foldingStartMarker = /^(\s*Olive|(Function|Define)\s*[a-z][a-zA-Z0-9_]*)[^;]*(#.*)?$/;
      this.getFoldWidgetRange = function(session, foldStyle, row) {
        var first = session.getLine(row).match(this.foldingStartMarker);
        if (!first) return;

        var maxRow = session.getLength();
        var endRow = row;
        var line;

        while (++endRow < maxRow) {
          line = session.getLine(endRow);
          if (/.*;[^'"]*$/.test(line)) break;
        }

        var endColumn = session.getLine(endRow).lastIndexOf(";") + 1;
        return new Range(row, first[1].length, endRow, endColumn);
      };
    }.call(FoldMode.prototype));
  }
);

ace.define(
  "ace/mode/shesmu",
  [
    "require",
    "exports",
    "module",
    "ace/lib/oop",
    "ace/mode/text",
    "ace/mode/shesmu_highlight_rules",
    "ace/mode/folding/shesmu"
  ],
  function(require, exports, module) {
    "use strict";

    var oop = require("../lib/oop");
    var TextMode = require("./text").Mode;
    var ShesmuHighlightRules = require("./shesmu_highlight_rules")
      .ShesmuHighlightRules;
    var FoldMode = require("./folding/shesmu").FoldMode;

    var Mode = function() {
      this.HighlightRules = ShesmuHighlightRules;
      this.foldingRules = new FoldMode();
      this.$behaviour = this.$defaultBehaviour;
    };
    oop.inherits(Mode, TextMode);

    (function() {
      this.lineCommentStart = "#";
      this.blockComment = null;
      this.$id = "ace/mode/shesmu";
    }.call(Mode.prototype));

    exports.Mode = Mode;
  }
);
(function() {
  ace.require(["ace/mode/shesmu"], function(m) {
    if (typeof module == "object" && typeof exports == "object" && module) {
      module.exports = m;
    }
  });
})();

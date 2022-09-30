import { fetchJsonWithBusyDialog, locallyStored } from "./io.js";
import {
  DisplayElement,
  IconName,
  TreePath,
  UIElement,
  blank,
  br,
  button,
  dialog,
  dropdown,
  hr,
  indented,
  inputSearch,
  inputText,
  italic,
  link,
  mono,
  pane,
  preformatted,
  setRootDashboard,
  sidepanel,
  singleState,
  table,
  tableFromRows,
  tableRow,
  text,
  tree,
} from "./html.js";
import { parse } from "./parser.js";
import * as valueParser from "./parser.js";
import { commonPathPrefix, mapModel } from "./util.js";

export type Definition =
  | ActionDefintion
  | ConstantDefinition
  | FunctionDefinition
  | OliveDefinition
  | RefillerDefinition
  | SignatureDefinition;
export interface ActionDefintion {
  kind: "action";
  name: string;
  description: string;
  parameters: { name: string; type: string; required: boolean }[];
  filename: string | null;
  supplementaryInformation: { label: DisplayElement; value: DisplayElement }[];
}
export interface ConstantDefinition {
  kind: "constant";
  type: string;
  name: string;
  description: string;
  filename: string | null;
}
export interface FunctionDefinition {
  kind: "function";
  return: string;
  name: string;
  description: string;
  parameters: { description: string; type: string }[];
  filename: string | null;
}
export interface OliveDefinition {
  kind: "olive";
  name: string;
  isRoot: boolean;
  output: { [s: string]: string };
  parameters: string[];
  filename: string | null;
  format: string;
}
export interface RefillerDefinition {
  kind: "refiller";
  name: string;
  description: string;
  parameters: { name: string; type: string }[];
  filename: string | null;
  supplementaryInformation: { label: DisplayElement; value: DisplayElement }[];
}
export interface SignatureDefinition {
  kind: "signature";
  type: string;
  name: string;
  filename: string | null;
}

interface TypeTransformer<T> {
  a: (inner: T) => T;
  b: T;
  d: T;
  j: T;
  f: T;
  i: T;
  m: (key: T, value: T) => T;
  o: (fields: { [name: string]: T }) => T;
  p: T;
  q: (inner: T) => T;
  s: T;
  t: (inner: T[]) => T;
  u: (unionTypes: { [type: string]: T | null }) => T;
}

type JsonDescriptor =
  | string
  | JsonDescriptor[]
  | { is: "optional"; inner: JsonDescriptor }
  | { is: "list"; inner: JsonDescriptor }
  | { is: "dictionary"; key: JsonDescriptor; value: JsonDescriptor };

export interface TypeResponse {
  humanName: string;
  descriptor: string;
  wdlType: string;
  jsonDescriptor: JsonDescriptor;
}
export interface ValueResponse {
  value?: any;
  error?: string;
}

const nameForType: TypeTransformer<UIElement> = {
  a: (inner: UIElement) => ["[ ", inner, " ]"],
  b: "boolean",
  d: "date",
  j: "json",
  f: "float",
  i: "integer",
  m: (key: UIElement, value: UIElement) => [key, " → ", value],
  o: (fields: { [name: string]: UIElement }) => [
    "{",
    indented(
      Object.entries(fields)
        .sort((a, b) => a[0].localeCompare(b[0]))
        .map(([n, v], index, arr) => [
          n,
          " = ",
          v,
          index < arr.length - 1 ? [",", br()] : blank(),
        ])
    ),
    "}",
  ],

  p: "path",
  q: (inner: UIElement) => [inner, "?"],
  s: "string",
  t: (inner: UIElement[]) => [
    "{",
    indented(
      inner.map((type, index, arr) => [
        type,
        index < arr.length - 1 ? [",", br()] : blank(),
      ])
    ),
    "}",
  ],
  u: (unionTypes: { [type: string]: UIElement | null }) =>
    Object.entries(unionTypes)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([n, t], index, arr) => [
        t ? [n, " ", t] : n,
        index < arr.length - 1 ? [" | ", br()] : blank(),
      ]),
};
const exampleForType: TypeTransformer<string> = {
  a: (inner: UIElement) => `[${inner}, ${inner}]`,
  b: "True",
  d: "Date 2020-01-01",
  j: "null",
  f: "3.14",
  i: "7",
  m: (key: string, value: string) => `Dict {${key} = ${value}}`,
  o: (fields: { [name: string]: string }) =>
    "{" +
    Object.entries(fields)
      .map(([k, v]) => `${k} = ${v}`)
      .join(", ") +
    "}",

  p: "'/foo'",
  q: (inner: UIElement) => `\`${inner}\``,
  s: '"stuff"',
  t: (inner: string[]) => "{" + inner.join(", ") + "}",
  u: (unionTypes: { [type: string]: string | null }) => {
    const [n, t] = Object.entries(unionTypes)[0];
    return t ? `${n} ${t}` : n;
  },
};

export function parseType() {
  const format = document.getElementById("format") as HTMLSelectElement;
  fetchJsonWithBusyDialog(
    "type",
    {
      value: (document.getElementById("typeValue") as HTMLInputElement).value,
      format: format.options[format.selectedIndex].value,
    },
    (data) => {
      document.getElementById("humanType")!.innerText = data.humanName;
      document.getElementById("descriptorType")!.innerText = data.descriptor;
      document.getElementById("wdlType")!.innerText = data.wdlType;
      document.getElementById("jsonDescriptorType")!.innerText = JSON.stringify(
        data.jsonDescriptor
      );
    }
  );
}

function showValue(data: ValueResponse): void {
  dialog((_close) =>
    data.hasOwnProperty("value")
      ? preformatted(JSON.stringify(data.value, null, 2))
      : text(data.error || "Unknown error")
  );
}

type GroupOrder = "namespace" | "plugin" | "kind" | "plugin-kind";

export function initialiseDefinitionDash(definitions: Definition[]): void {
  const filePrefix = commonPathPrefix(
    definitions.map((d) => d.filename).filter((d): d is string => d != null)
  );
  const { ui: details, model: detailsModel } = singleState(
    (definition: Definition) => {
      let output: UIElement;

      switch (definition.kind) {
        case "action":
          {
            output = [
              italic("Description: "),
              definition.description,
              br(),
              table(
                definition.supplementaryInformation,
                ["Extra Information", (x) => x.label],
                ["Value", (x) => x.value]
              ),
              table(
                definition.parameters,
                ["Name", (p) => p.name],
                ["Required?", (p) => (p.required ? "Yes" : "No")],
                ["Type", (p) => prettyType(p.type)]
              ),
            ];
          }
          break;
        case "constant":
          {
            output = [
              italic("Description: "),
              definition.description,
              br(),
              italic("Returns: "),
              prettyType(definition.type),
              br(),
              button(
                [{ type: "icon", icon: "cloud-download" }, "Get"],
                "Get current value for this constant",
                () =>
                  fetchJsonWithBusyDialog(
                    "constant",
                    definition.name,
                    showValue
                  )
              ),
            ];
          }
          break;
        case "function":
          {
            output = [
              italic("Description: "),
              definition.description,
              br(),
              italic("Returns: "),
              prettyType(definition.return),
              table(
                definition.parameters,
                ["Description", (p) => p.description],
                ["Type", (p) => prettyType(p.type)]
              ),
              button("Try It", "Test function from the browser", () =>
                testFunction(definition)
              ),
            ];
          }
          break;
        case "olive":
          {
            output = [
              italic("Input format: "),
              link(
                `inputdefs#${definition.format}`,
                definition.format,
                "View input format definition"
              ),
              definition.isRoot ? " Output is same" : " Output is modified",
              br(),
              table(definition.parameters, ["Parameter", prettyType]),
              table(
                Object.entries(definition.output),
                ["Variable", ([n, _]) => n],
                ["Type", ([_, v]) => prettyType(v)]
              ),
            ];
          }
          break;
        case "refiller":
          {
            output = [
              italic("Description: "),
              definition.description,
              br(),
              table(
                definition.supplementaryInformation,
                ["Extra Information", (x) => x.label],
                ["Value", (x) => x.value]
              ),
              table(
                definition.parameters,
                ["Name", (p) => p.name],
                ["Type", (p) => prettyType(p.type)]
              ),
            ];
          }
          break;
        case "signature":
          {
            output = ["Returns: ", prettyType(definition.type)];
          }
          break;
      }

      return [
        { type: "icon", icon: iconForKind(definition.kind) },
        { type: "b", contents: definition.name },
        " [",
        prettyKind(definition.kind),
        "]",
        br(),
        hr(),
        definition.filename
          ? [
              italic("From plugin: "),
              [filePrefix(definition.filename)]
                .flat()
                .map((e) => (typeof e == "string" ? mono(e) : e)),
            ]
          : "Built-in",
        br(),
        output,
      ];
    }
  );
  const {
    ui: treeUi,
    buttons: treeButtons,
    data,
    grouping,
  } = tree(detailsModel, (definition) => [
    { type: "icon", icon: iconForKind(definition.kind) },
    definition.name.split(/::/).map((n, i) => {
      const chunks = n
        .split(/_/)
        .map((nn, ni) => (ni > 0 ? [null, "_", null, nn] : nn));
      return i > 0 ? [null, "::", null, chunks] : chunks;
    }),
  ]);
  const savedGroupOrder = locallyStored<GroupOrder>(
    "shesmu_group_order",
    "namespace"
  );
  const groupingUi = dropdown(
    (label: GroupOrder, selected: boolean) => {
      switch (label) {
        case "namespace":
          return [
            { type: "icon", icon: "diagram-2" },
            selected ? blank() : "Namespace",
          ];
        case "plugin":
          return [
            { type: "icon", icon: "plug" },
            selected ? blank() : "Plugin ",
            { type: "icon", icon: "diagram-2" },
            selected ? blank() : "Namespace",
          ];
        case "kind":
          return [
            { type: "icon", icon: "patch-question" },
            selected ? blank() : "Kind ",
            { type: "icon", icon: "diagram-2" },
            selected ? blank() : "Namespace",
          ];
        case "plugin-kind":
          return [
            { type: "icon", icon: "plug" },
            selected ? blank() : "Plugin ",
            { type: "icon", icon: "patch-question" },
            selected ? blank() : "Kind ",
            { type: "icon", icon: "diagram-2" },
            selected ? blank() : "Namespace",
          ];
      }
    },
    (i) => i == savedGroupOrder.get(),
    mapModel(grouping, (order: GroupOrder): ((d: Definition) => TreePath[]) => {
      const forNamespace = (d: Definition, ...prefix: TreePath[]): TreePath[] =>
        prefix.concat(
          d.name.split(/::/).map((x) => ({ value: x, display: x, elide: true }))
        );
      switch (order) {
        case "namespace":
          return (d) => forNamespace(d);
        case "kind":
          return (d: Definition) =>
            forNamespace(d, {
              value: d.kind,
              display: [
                { type: "icon" as const, icon: iconForKind(d.kind) },
                prettyKind(d.kind),
              ],
              elide: false,
            });
        case "plugin":
          return (d: Definition) =>
            forNamespace(d, {
              value: d.filename || "Built-in",
              display: d.filename
                ? [{ type: "icon", icon: "plug" }, filePrefix(d.filename)]
                : [{ type: "icon", icon: "outlet" }, "Built-in"],
              elide: false,
            });
        case "plugin-kind":
          return (d: Definition) =>
            forNamespace(
              d,
              {
                value: d.filename || "Built-in",
                display: d.filename
                  ? [{ type: "icon", icon: "plug" }, filePrefix(d.filename)]
                  : [{ type: "icon", icon: "outlet" }, "Built-in"],
                elide: false,
              },
              {
                value: d.kind,
                display: [
                  { type: "icon" as const, icon: iconForKind(d.kind) },
                  prettyKind(d.kind),
                ],
                elide: false,
              }
            );
      }
    }),
    {
      synchronizer: savedGroupOrder,
      predicate: (recovered, item) => recovered == item,
      extract: (x) => x,
    },
    "namespace",
    "plugin",
    "kind",
    "plugin-kind"
  );
  const inputFilter = inputSearch((input) => {
    const keywords = input
      .toLowerCase()
      .split(/\W+/)
      .filter((s) => s);
    data.statusChanged(
      definitions
        .filter((def) =>
          keywords.every((k) => {
            if (
              def.name.toLowerCase().indexOf(k) !== -1 ||
              (def.filename
                ? def.filename.toLowerCase().indexOf(k) !== -1
                : false)
            ) {
              return true;
            }
            switch (def.kind) {
              case "action":
                return (
                  def.description.toLowerCase().indexOf(k) !== -1 ||
                  def.parameters.some((p) => p.name.indexOf(k) !== -1)
                );
              case "constant":
                return def.description.toLowerCase().indexOf(k) !== -1;
              case "function":
                return (
                  def.description.toLowerCase().indexOf(k) !== -1 ||
                  def.parameters.some((p) => p.description.indexOf(k) !== -1)
                );
              case "olive":
                return (
                  def.format.toLowerCase().indexOf(k) !== -1 ||
                  Object.keys(def.output).some((o) => o.indexOf(k) !== -1)
                );
              case "refiller":
                return def.parameters.some((p) => p.name.indexOf(k) !== -1);
              case "signature":
                return false;
            }
          })
        )
        .sort((a, b) => a.name.localeCompare(b.name))
    );
  });

  data.statusChanged(definitions.sort((a, b) => a.name.localeCompare(b.name)));
  setRootDashboard(
    "definitiondash",
    sidepanel(
      [
        "Filter: ",
        inputFilter,
        br(),
        "Group by: ",
        groupingUi,
        treeButtons,
        br(),
        treeUi,
      ],
      details
    )
  );
}

function iconForKind(kind: Definition["kind"]) {
  let result: IconName = "question-circle";
  switch (kind) {
    case "action":
      result = "camera-reels";
      break;
    case "constant":
      result = "braces";
      break;
    case "function":
      result = "code-square";
      break;
    case "olive":
      result = "puzzle";
      break;
    case "refiller":
      result = "bucket";
      break;
    case "signature":
      result = "vector-pen";
      break;
  }
  return result;
}

function prettyKind(kind: Definition["kind"]): string {
  switch (kind) {
    case "action":
      return "Action";
    case "constant":
      return "Constant";
    case "function":
      return "Function";
    case "olive":
      return "Callable Olive Definition";
    case "refiller":
      return "Refiller";
    case "signature":
      return "Signature";
  }
}

export function parseDescriptor<T>(
  type: string,
  transformer: TypeTransformer<T>
): [T, string] {
  switch (type.charAt(0)) {
    case "b":
      return [transformer.b, type.substring(1)];
    case "d":
      return [transformer.d, type.substring(1)];
    case "f":
      return [transformer.f, type.substring(1)];
    case "i":
      return [transformer.i, type.substring(1)];
    case "j":
      return [transformer.j, type.substring(1)];
    case "p":
      return [transformer.p, type.substring(1)];
    case "s":
      return [transformer.s, type.substring(1)];
    case "a": {
      const [inner, remainder] = parseDescriptor(
        type.substring(1),
        transformer
      );
      return [transformer.a(inner), remainder];
    }
    case "m": {
      const [key, keyRemainder] = parseDescriptor(
        type.substring(1),
        transformer
      );
      const [value, valueRemainder] = parseDescriptor(
        keyRemainder,
        transformer
      );
      return [transformer.m(key, value), valueRemainder];
    }
    case "q": {
      const [inner, remainder] = parseDescriptor(
        type.substring(1),
        transformer
      );
      return [transformer.q(inner), remainder];
    }
    case "t":
    case "o":
      return parseComplex(type, transformer, (): T => {
        throw new Error("Malformed descriptor");
      });
    case "u": {
      let match;
      if ((match = /^([0-9]*)([^0-9].*)$/.exec(type.substring(1))) === null) {
        throw new Error("Malformed descriptor");
      }
      let rest = match[2];
      const count = parseInt(match[1]);
      const unions: { [s: string]: T | null } = {};
      for (let index = 0; index < count; index++) {
        if ((match = /^([^$]*)\$(.*)$/.exec(rest)) === null) {
          throw new Error("Malformed descriptor");
        }
        rest = match[2];
        const [type, r] = parseComplex(rest, transformer, () => null);
        rest = r;
        unions[match[1]] = type;
      }
      return [transformer.u(unions), rest];
    }
    default:
      throw new Error("Malformed descriptor");
  }
}
function parseComplex<T, E extends T | null>(
  type: string,
  transformer: TypeTransformer<T>,
  whenEmpty: () => E
): [T | E, string] {
  let match;
  if ((match = /^([0-9]*)([^0-9].*|)$/.exec(type.substring(1))) === null) {
    throw new Error("Malformed descriptor");
  }
  let rest = match[2];
  const count = parseInt(match[1]);
  if (count == 0) {
    return [whenEmpty(), rest];
  }
  if (type.charAt(0) == "t") {
    const types = [];
    for (let index = 0; index < count; index++) {
      const [type, r] = parseDescriptor(rest, transformer);
      rest = r;
      types.push(type);
    }
    return [transformer.t(types), rest];
  } else {
    const fields: { [s: string]: T } = {};
    for (let index = 0; index < count; index++) {
      if ((match = /^([^$]*)\$(.*)$/.exec(rest)) === null) {
        throw new Error("Malformed descriptor");
      }
      rest = match[2];
      const [type, r] = parseDescriptor(rest, transformer);
      rest = r;
      fields[match[1]] = type;
    }
    return [transformer.o(fields), rest];
  }
}

function prettyType(type: string): UIElement {
  return parseDescriptor(type, nameForType)[0];
}

function testFunction(func: FunctionDefinition): void {
  const parsers = func.parameters.map(
    (p) => parseDescriptor(p.type, valueParser)[0]
  );
  const inputs = func.parameters.map(() => inputText());
  const errors = func.parameters.map(() => pane("blank"));
  dialog((_close) => [
    "Test function ",
    func.name,
    br(),
    tableFromRows(
      func.parameters.map((p, index) =>
        tableRow(
          null,
          { contents: `Argument ${index + 1}`, header: true },
          { contents: inputs[index].ui },
          { contents: prettyType(p.type) },
          { contents: parseDescriptor(p.type, exampleForType)[0] },
          { contents: errors[index].ui }
        )
      )
    ),
    br(),

    button(
      [{ type: "icon", icon: "play" }, "Run"],
      "Run function with parameters",
      () => {
        const args: any[] = [];
        if (
          parsers.every((parser, index) =>
            parse(
              inputs[index].value,
              parser,
              (x) => {
                args.push(x);
                errors[index].model.statusChanged(blank());
              },
              (message, position) =>
                errors[index].model.statusChanged({
                  type: "b",
                  contents: `${position + 1}: ${message}`,
                })
            )
          )
        ) {
          fetchJsonWithBusyDialog(
            "function",
            { name: func.name, args: args },
            showValue
          );
        }
      }
    ),
  ]);
}

import {
  UIElement,
  StateSynchronizer,
  FindHandler,
  inputText,
  collapsible,
  paragraph,
  italic,
  mono,
  button,
  buttonAccessory,
  text,
  pane,
  dialog,
  br,
  findProxy,
  singleState,
  blank,
  inputTextArea,
  tableFromRows,
  tableRow,
  dateEditor,
  inputNumber,
  temporaryState,
  dropdown,
  pickFromSet,
  pickFromSetCustom,
  inputCheckbox,
  tile,
  ActiveItemRenderer,
  buttonClose,
  buttonEdit,
  preformatted,
} from "./html.js";
import { AddRangeSearch, AddPropertySearch } from "./stats.js";
import { Status, statusButton, statusDescription, statuses } from "./action.js";
import { actionRender } from "./actions.js";
import {
  StatefulModel,
  SourceLocation,
  mergingModel,
  filterModel,
  combineModels,
  mapModel,
  errorModel,
  promiseModel,
  commonPathPrefix,
  FilenameFormatter,
  formatTimeSpan,
  computeDuration,
  mergeLocations,
} from "./util.js";
import {
  refreshable,
  fetchJsonWithBusyDialog,
  fetchCustomWithBusyDialog,
} from "./io.js";

/**
 * A filter that the server can use to limit the actions returned.
 */
export type ActionFilter =
  | ActionFilterAddedAgo
  | ActionFilterAdded
  | ActionFilterAnd
  | ActionFilterCheckedAgo
  | ActionFilterChecked
  | ActionFilterExternalAgo
  | ActionFilterExternal
  | ActionFilterIds
  | ActionFilterOr
  | ActionFilterRegex
  | ActionFilterSourceFile
  | ActionFilterSourceLocation
  | ActionFilterStatusChangedAgo
  | ActionFilterStatusChanged
  | ActionFilterStatus
  | ActionFilterTag
  | ActionFilterText
  | ActionFilterType;
export interface ActionFilterAddedAgo {
  offset: number;
  type: "addedago";
}

export interface ActionFilterAdded {
  end: number | null;
  start: number | null;
  type: "added";
}

export interface ActionFilterAnd {
  filters: ActionFilter[];
  type: "and";
}

export interface ActionFilterCheckedAgo {
  offset: number;
  type: "checkedago";
}

export interface ActionFilterChecked {
  end: number | null;
  start: number | null;
  type: "checked";
}

export interface ActionFilterExternalAgo {
  offset: number;
  type: "externalago";
}

export interface ActionFilterExternal {
  end: number | null;
  start: number | null;
  type: "external";
}

export interface ActionFilterIds {
  ids: string[];
  type: "id";
}

export interface ActionFilterOr {
  filters: ActionFilter[];
  type: "or";
}

export interface ActionFilterRegex {
  matchCase: boolean;
  pattern: string;
  type: "regex";
}

export interface ActionFilterSourceFile {
  files: string[];
  type: "sourcefile";
}

export interface ActionFilterSourceLocation {
  locations: SourceLocation[];
  type: "sourcelocation";
}

export interface ActionFilterStatusChangedAgo {
  offset: number;
  type: "statuschangedago";
}

export interface ActionFilterStatusChanged {
  end: number | null;
  start: number | null;
  type: "statuschanged";
}

export interface ActionFilterStatus {
  states: Status[];
  type: "status";
}

export interface ActionFilterTag {
  tags: string[];
  type: "tag";
}

export interface ActionFilterText {
  matchCase: boolean;
  text: string;
  type: "text";
}

export interface ActionFilterType {
  types: string[];
  type: "type";
}

/**
 * The information used in the _Basic_ action search interface
 */
export interface BasicQuery {
  added?: TimeSpan;
  addedago?: number;
  checked?: TimeSpan;
  checkedago?: number;
  external?: TimeSpan;
  externalago?: number;
  id?: string[];
  regex?: BasicRegex[];
  sourcefile?: string[];
  sourcelocation?: SourceLocation[];
  status?: Status[];
  statuschanged?: TimeSpan;
  statuschangedago?: number;
  tag?: string[];
  text?: BasicText[];
  type?: string[];
}
interface BasicRegex {
  pattern: string;
  matchCase: boolean;
}
interface BasicText {
  text: string;
  matchCase: boolean;
}
/**
 * Standard definitions to handle all the time manipulation gracefull
 */
interface BasicQueryTimeAccessor {
  rangeType: TimeRangeType;
  horizonType: TimeAgoType;
  horizon(query: BasicQuery): number | null;
  range(query: BasicQuery): TimeSpan | null;
}
const timeAccessors: BasicQueryTimeAccessor[] = [
  {
    rangeType: "added",
    horizonType: "addedago",
    horizon: (query) => query.addedago || null,
    range: (query) => query.added || null,
  },
  {
    rangeType: "checked",
    horizonType: "checkedago",
    horizon: (query) => query.checkedago || null,
    range: (query) => query.checked || null,
  },
  {
    rangeType: "statuschanged",
    horizonType: "statuschangedago",
    horizon: (query) => query.statuschangedago || null,
    range: (query) => query.statuschanged || null,
  },
  {
    rangeType: "external",
    horizonType: "externalago",
    horizon: (query) => query.externalago || null,
    range: (query) => query.external || null,
  },
];
/**
 * The response format from the server when attempting to parse a text query
 */
interface ParseQueryRespose {
  errors: ParseQueryError[];
  formatted?: string;
  filter?: ActionFilter;
}
interface ParseQueryError {
  line: number;
  column: number;
  message: string;
}

export type PropertyType = "status" | "sourcefile" | "sourcelocation" | "type";

export type SetType = "id" | "status" | "tag" | "type";
/**
 * The client supports two different action querying modes: basic/GUI and advanced/text search
 *
 * This is how each query format interacts with the rest of the dashboard
 */
interface SearchPlatform {
  buttons: UIElement;
  entryBar: UIElement;
  find: FindHandler;
  addRangeSearch: AddRangeSearch;
  addPropertySearch: AddPropertySearch;
}
type TextHandler = (text: string, matchedCase: boolean) => void;
export type TimeAgoType =
  | "addedago"
  | "checkedago"
  | "statuschangedago"
  | "externalago";
export type TimeRangeType = "added" | "checked" | "statuschanged" | "external";
/**
 * A time range with optional fixed end points
 */
interface TimeSpan {
  start: number | null;
  end: number | null;
}
function addFilterDialog(
  onActionPage: boolean,
  sources: SourceLocation[],
  tags: string[],
  timeRange: (
    accessor: BasicQueryTimeAccessor,
    start: number | null,
    end: number | null
  ) => void,
  timeAgo: (accessor: BasicQueryTimeAccessor, offset: number | null) => void,
  addSet: (type: SetType, items: string[]) => void,
  addLocations: (locations: SourceLocation[]) => void,
  setText: TextHandler,
  setRegex: TextHandler
): void {
  dialog((close) => [
    button(
      "🕑 Fixed Time Range",
      "Add a filter that restricts between two absolute times.",
      () => {
        close();
        timeDialog((n) =>
          editTimeRange({ start: null, end: null }, (start, end) =>
            timeRange(n, start, end)
          )
        );
      }
    ),
    button(
      "🕑 Time Since Now",
      "Add a filter that restricts using a sliding window.",
      () => {
        close();
        timeDialog((n) => editTimeHorizon(0, (update) => timeAgo(n, update)));
      }
    ),
    button("👾 Action Identifier", "Add a unique action identifier.", () => {
      close();
      const { ui, getter } = inputTextArea();
      dialog((close) => [
        "Action Identifiers:",
        br(),
        ui,
        br(),
        button(
          "Add All",
          "Add any action IDs in the text to the filter.",
          () => {
            close();
            const ids = Array.from(
              getter().matchAll(/shesmu:([0-9A-Fa-f]{40})/g),
              (m) => "shesmu:" + m[1].toUpperCase()
            );
            addSet("id", ids);
          }
        ),
      ]);
    }),
    button(
      "🔠 Text",
      "Add a filter that looks for actions with specific text.",
      () => {
        close();
        editText({ text: "", matchCase: false }, setText);
      }
    ),
    button(
      "*️⃣  Regular Expression",
      "Add a filter that looks for actions that match a regular expression.",
      () => {
        close();
        editRegex({ pattern: "", matchCase: false }, setRegex);
      }
    ),
    button(
      "🏁 Status",
      "Add a filter that searches for actions in a particular state.",
      () => {
        close();
        pickFromSetCustom(
          statuses,
          (status) => addSet("status", status),
          (status, click) => [
            statusButton(status, click),
            statusDescription(status),
          ],
          (status, keywords) =>
            keywords.every(
              (k) =>
                status.toLowerCase().indexOf(k) != -1 ||
                statusDescription(status).toLowerCase().indexOf(k) != -1
            ),
          true
        );
      }
    ),
    onActionPage
      ? button(
          "🎬 Action Type",
          "Add a filter that searches for actions of a particular type.",
          () => {
            close();
            pickFromSet(
              Array.from(actionRender.keys()).sort(),
              (type) => addSet("type", type),
              (type) => [type, ""],
              (type, keywords) =>
                keywords.every((k) => type.toLowerCase().indexOf(k) != -1),
              false
            );
          }
        )
      : blank(),
    tags.length
      ? button(
          "🏷️ Tags",
          "Add a filter that searches for actions marked with a particular tag by an olive.",
          () => {
            close();
            pickFromSet(
              tags.sort(),
              (tag) => addSet("tag", tag),
              (tag) => [tag, ""],
              (tag, keywords) =>
                keywords.every((k) => tag.toLowerCase().indexOf(k) != -1),
              false
            );
          }
        )
      : blank(),
    sources.length
      ? button(
          "📍 Source Olive",
          "Add a filter that searches for actions that came from a particular olive (even if that olive has been replaced or deleted).",
          () => {
            close();
            const fileNameFormatter = commonPathPrefix(
              sources.map((s) => s.file)
            );
            pickFromSet(
              sources
                .sort(
                  (a, b) =>
                    a.file.localeCompare(b.file) ||
                    (a.line || 0) - (b.line || 0) ||
                    (a.column || 0) - (b.column || 0) ||
                    (a.hash || "").localeCompare(b.hash || "")
                )
                .flatMap((source, index, array) => {
                  const previous = index == 0 ? null : array[index - 1];
                  const result: SourceLocation[] = [];
                  if (index == 0 || source.file != previous?.file) {
                    result.push({
                      file: source.file,
                      line: null,
                      column: null,
                      hash: null,
                    });
                  }
                  if (
                    index == 0 ||
                    source.file != previous?.file ||
                    source.line != previous?.line
                  ) {
                    result.push({
                      file: source.file,
                      line: source.line,
                      column: null,
                      hash: null,
                    });
                  }
                  if (
                    index == 0 ||
                    source.file != previous?.file ||
                    source.line != previous?.line ||
                    source.column != previous?.column
                  ) {
                    result.push({
                      file: source.file,
                      line: source.line,
                      column: source.column,
                      hash: null,
                    });
                  }
                  result.push(source);
                  return result;
                }),
              addLocations,
              (sourceLocation) => [
                fileNameFormatter(sourceLocation.file) +
                  (sourceLocation.line
                    ? ":" +
                      sourceLocation.line +
                      (sourceLocation.column
                        ? ":" +
                          sourceLocation.column +
                          (sourceLocation.hash
                            ? "[" + sourceLocation.hash + "]"
                            : "")
                        : "")
                    : ""),
                sourceLocation.file,
              ],
              (sourceLocation, keywords) =>
                keywords.every(
                  (k) => sourceLocation.file.toLowerCase().indexOf(k) != -1
                ),
              true
            );
          }
        )
      : blank(),
  ]);
}

/**
 * Transform a basic query object into real filters we can send to the server
 */
function createFilters(query: BasicQuery): ActionFilter[] {
  const filters: ActionFilter[] = [];
  if (query.type && query.type.length > 0) {
    filters.push({ type: "type", types: query.type });
  }
  if (query.status && query.status.length > 0) {
    filters.push({ type: "status", states: query.status });
  }
  if (query.tag && query.tag.length > 0) {
    filters.push({ type: "tag", tags: query.tag });
  }
  if (query.id && query.id.length > 0) {
    filters.push({ type: "id", ids: query.id });
  }
  if (query.sourcefile && query.sourcefile.length > 0) {
    filters.push({ type: "sourcefile", files: query.sourcefile });
  }

  if (query.sourcelocation && query.sourcelocation.length > 0) {
    filters.push({
      type: "sourcelocation",
      locations: query.sourcelocation,
    });
  }

  for (const accessor of timeAccessors) {
    const range = accessor.range(query);
    if (range) {
      if (range.start || range.end) {
        filters.push({ ...range, type: accessor.rangeType });
      }
    }
    const ago = accessor.horizon(query);
    if (ago) {
      filters.push({ type: accessor.horizonType, offset: ago });
    }
  }
  if (query.text) {
    query.text.forEach(({ text, matchCase }) =>
      filters.push({
        type: "text",
        matchCase: matchCase,
        text: text,
      })
    );
  }
  if (query.regex) {
    query.regex.forEach((regex) => filters.push({ ...regex, type: "regex" }));
  }
  return filters;
}
export function createSearch(
  synchronizer: StateSynchronizer<string | BasicQuery>,
  model: StatefulModel<ActionFilter[]>,
  onActionPage: boolean,
  filenameFormatter: FilenameFormatter,
  sources: SourceLocation[],
  tags: string[]
): {
  buttons: UIElement;
  entryBar: UIElement;
  model: StatefulModel<ActionFilter[]>;
  addRangeSearch: AddRangeSearch;
  addPropertySearch: AddPropertySearch;
  find: FindHandler;
} {
  const buttons = pane();
  const entryBar = pane();
  const find = findProxy();
  const [baseModel, queryModel] = mergingModel(
    filterModel(model, "Missing base search."),
    (left: ActionFilter[] | null, right: ActionFilter[] | null) =>
      left ? left.concat(right || []) : null
  );
  let search: SearchPlatform;
  synchronizer.listen((query) => {
    if (typeof query == "string") {
      search = searchAdvanced(
        query,
        queryModel,
        synchronizer,
        onActionPage,
        sources,
        tags
      );
    } else {
      search = searchBasic(
        query,
        queryModel,
        synchronizer,
        onActionPage,
        filenameFormatter,
        sources,
        tags
      );
    }
    buttons.update(search.buttons);
    entryBar.update(search.entryBar);
    find.updateHandle(search.find);
  });
  return {
    buttons: buttons.ui,
    entryBar: entryBar.ui,
    model: baseModel,
    addRangeSearch: (typeName, start, end) =>
      search.addRangeSearch(typeName, start, end),
    addPropertySearch: (...limits) => search.addPropertySearch(...limits),
    find: find.find,
  };
}
function editText(original: BasicText, callback: TextHandler): void {
  dialog((close) => {
    const text = inputText(original.text);
    const matched = inputCheckbox("Case sensitive", original.matchCase);
    return [
      "Search for text: ",
      text.ui,
      br(),
      matched.ui,
      br(),
      button("Save", "Update text search filter in current search.", () => {
        close();
        callback(text.getter(), matched.getter());
      }),
    ];
  });
}
function editRegex(original: BasicRegex, callback: TextHandler) {
  dialog((close) => {
    const pattern = inputText(original.pattern);
    const error = pane();
    const matched = inputCheckbox("Case sensitive", original.matchCase);
    return [
      "Search for regex: ",
      pattern.ui,
      " ",
      error.ui,
      br(),
      matched.ui,
      br(),
      button("Save", "Update text search filter in current search.", () => {
        try {
          new RegExp(pattern.getter());
        } catch (e) {
          error.update(e.message);
          return;
        }
        close();
        callback(pattern.getter(), matched.getter());
      }),
    ];
  });
}

function editTimeRange(
  original: TimeSpan,
  callback: (start: number | null, end: number | null) => void
): void {
  dialog((close) => {
    const start = dateEditor(original.start);
    const end = dateEditor(original.end);
    return [
      tableFromRows([
        tableRow(null, { contents: "Start date:" }, { contents: "End date: " }),
        tableRow(null, { contents: start.ui }, { contents: end.ui }),
      ]),
      button("Save", "Update time range filter in current search.", () => {
        close();
        callback(start.getter(), end.getter());
      }),
    ];
  });
}

function editTimeHorizon(
  original: number | null,
  callback: (offset: number | null) => void
): void {
  dialog((close) => {
    const offset = inputNumber(original || 0, 0, null);
    const units = temporaryState([3600_000, "hours"] as [number, string]);
    return [
      offset.ui,
      dropdown(
        ([, n]) => n,
        units.get(),
        units,
        null,
        [1, "milliseconds"],
        [1000, "seconds"],
        [60000, "minutes"],
        [3600000, "hours"],
        [86400000, "days"]
      ),
      br(),
      button("Save", "Update time range filter in current search.", () => {
        close();
        callback(
          Number.isNaN(offset.getter())
            ? null
            : offset.getter() * units.get()[0]
        );
      }),
    ];
  });
}

/**
 * Get the name for a time property
 */
export function nameForBin(name: TimeAgoType | TimeRangeType): string {
  switch (name) {
    case "added":
    case "addedago":
      return "Time Since Action was Last Generated by an Olive";
    case "checked":
    case "checkedago":
      return "Last Time Action was Last Run";
    case "statuschanged":
    case "statuschangedago":
      return "Last Time Action's Status Last Changed";
    case "external":
    case "externalago":
      return "External Last Modification Time";
    default:
      return name;
  }
}
function renderFilters(
  query: BasicQuery,
  filenameFormatter: FilenameFormatter,
  update: (query: BasicQuery) => void
): UIElement[] {
  const ui: UIElement[] = [];
  if (query.type && query.type.length > 0) {
    ui.push(
      renderSet(
        query.type,
        "Action Type",
        (type, click) => button(type, "Click to remove.", click),
        (items) => update({ ...query, type: items })
      )
    );
  }
  if (query.status && query.status.length > 0) {
    ui.push(
      renderSet(
        query.status,
        "Action Status",
        (status, click) => statusButton(status, click),
        (items) => update({ ...query, status: items })
      )
    );
  }
  if (query.tag && query.tag.length > 0) {
    ui.push(
      renderSet(
        query.tag,
        "Tags",
        (tag, click) => button(tag, "Click to remove.", click),
        (items) => update({ ...query, tag: items })
      )
    );
  }
  if (query.id && query.id.length > 0) {
    ui.push(
      renderSet(
        query.id,
        "Action IDs",
        (id, click) => [button(id, "Click to remove.", click), br()],
        (items) => update({ ...query, id: items })
      )
    );
  }
  if (query.sourcefile && query.sourcefile.length > 0) {
    ui.push(
      renderSet(
        query.sourcefile,
        "Source Files",
        (filename, click) => [
          button(
            filenameFormatter(filename),
            "Click to remove: " + filename,
            click
          ),
          br(),
        ],
        (items) => update({ ...query, sourcefile: items })
      )
    );
  }

  if (query.sourcelocation && query.sourcelocation.length > 0) {
    const locations = query.sourcelocation;
    ui.push(
      tile(
        [],
        "Source Locations",
        br(),
        tableFromRows(
          locations.map((item, index) =>
            tableRow(
              () => {
                const replacement = [...locations];
                replacement.splice(index, 1);
                update({ ...query, sourcelocation: replacement });
              },
              { contents: filenameFormatter(item.file), title: item.file },
              { contents: item.line?.toString() || "*" },
              { contents: item.column?.toString() || "*" },
              { contents: item.hash || "*" }
            )
          )
        )
      )
    );
  }

  for (const accessor of timeAccessors) {
    const range = accessor.range(query);
    if (range) {
      if (range.start || range.end) {
        ui.push(
          tile(
            [],
            nameForBin(accessor.rangeType),
            buttonClose("Remove range", () =>
              update({ ...query, [accessor.rangeType]: undefined })
            ),
            buttonEdit("Change range", () =>
              editTimeRange(range, (replacement) =>
                update({ ...query, [accessor.rangeType]: replacement })
              )
            ),
            br(),
            timeRangeAnchor("⇤ ", range.start, " —"),
            range.start && range.end
              ? ["🕑 ", formatTimeSpan(range.end - range.start)]
              : blank(),
            timeRangeAnchor("— ", range.start, " ⇥")
          )
        );
      }
    }
    const horizon = accessor.horizon(query);
    if (horizon) {
      ui.push(
        tile(
          [],
          nameForBin(accessor.horizonType),
          buttonClose("Remove horizon", () =>
            update({ ...query, [accessor.horizonType]: undefined })
          ),
          buttonEdit("Change horizon", () =>
            editTimeHorizon(horizon, (replacement) =>
              update({ ...query, [accessor.horizonType]: replacement })
            )
          ),
          br(),
          "🕑 ",
          formatTimeSpan(horizon)
        )
      );
    }
  }
  if (query.text) {
    const textSearches = query.text;
    textSearches.forEach((filter, index) =>
      ui.push(
        tile(
          [],
          filter.matchCase
            ? "Case-Sensitive Text Search"
            : "Case-Insensitive Text Search",
          buttonClose("Remove text", () => {
            const replacement = [...textSearches];
            replacement.splice(index, 1);
            update({ ...query, text: replacement });
          }),
          buttonEdit("Change text", () =>
            editText(filter, (text, matchedCase) => {
              const replacement = [
                ...textSearches,
                { text: text, matchCase: matchedCase },
              ];
              replacement.splice(index, 1);
              update({ ...query, text: replacement });
            })
          ),
          br(),
          preformatted(filter.text)
        )
      )
    );
  }
  if (query.regex) {
    const textSearches = query.regex;
    textSearches.forEach((filter, index) =>
      ui.push(
        tile(
          [],
          filter.matchCase
            ? "Case-Sensitive Regular Expression"
            : "Case-Insensitive Regular Expression",
          buttonClose("Remove regular expression", () => {
            const replacement = [...textSearches];
            replacement.splice(index, 1);
            update({ ...query, regex: replacement });
          }),
          buttonEdit("Change regular expression", () =>
            editRegex(filter, (pattern, matchedCase) => {
              const replacement = [
                ...textSearches,
                { pattern: pattern, matchCase: matchedCase },
              ];
              replacement.splice(index, 1);
              update({ ...query, regex: replacement });
            })
          ),
          br(),
          preformatted(filter.pattern)
        )
      )
    );
  }
  return ui;
}

function renderSet<T>(
  items: T[],
  title: string,
  renderer: ActiveItemRenderer<T>,
  update: (items: T[]) => void
): UIElement {
  return tile(
    [],
    title,
    br(),
    items.map((item, index) =>
      renderer(item, () => {
        const replacement = [...items];
        replacement.splice(index, 1);
        update(replacement);
      })
    )
  );
}

function searchAdvanced(
  filter: string,
  model: StatefulModel<ActionFilter[]>,
  synchronizer: StateSynchronizer<string | BasicQuery>,
  onActionPage: boolean,
  sources: SourceLocation[],
  tags: string[]
): SearchPlatform {
  const search = document.createElement("input");

  const searchModel: StatefulModel<string> = refreshable(
    "parsequery",
    (query) => ({
      method: "POST",
      body: JSON.stringify(query),
    }),
    combineModels(
      errorModel(model, (response: ParseQueryRespose) =>
        response.filter
          ? { type: "ok", value: [response.filter] }
          : {
              type: "error",
              message:
                response.errors
                  .map((e) => `${e.line}:${e.column}: ${e.message}`)
                  .join("; ") || "Unknown error.",
            }
      ),
      mapModel(
        filterModel(synchronizer, "Parse error."),
        (response) => response.formatted || null
      ),
      {
        reload: () => {},
        statusChanged: (response: ParseQueryRespose) => {
          if (response.formatted) {
            search.value = response.formatted;
          }
        },
        statusFailed: () => {},
        statusWaiting: () => {},
      }
    )
  );

  search.type = "search";
  search.value = filter;
  search.style.width = "100%";
  search.addEventListener("keydown", (e) => {
    if (e.keyCode === 13) {
      e.preventDefault();
      searchModel.statusChanged(search.value);
    }
  });
  searchModel.statusChanged(filter);
  return {
    buttons: [
      button(
        "🖱️ Basic",
        "Switch to basic query interface. Current query will be lost.",
        () =>
          dialog((close) => [
            "Switching to basic query interface will discard current query.",
            br(),
            button("Stay here", "Stay in the advanced query interface.", close),
            button(
              "Switch to basic",
              "Switch to the basic query interface.",
              () => {
                close();
                synchronizer.statusChanged({});
              }
            ),
          ])
      ),
      ...[
        [
          "🙴 And Filter",
          "and",
          "Add a filter that restricts the existing query.",
        ],
        ["❚ Or Filter", "or", "Add a filter that expands the existing query."],
      ].map(([label, operator, description]) =>
        buttonAccessory(label, description, () => {
          const replaceQuery = (...filters: ActionFilter[]) =>
            fetchCustomWithBusyDialog(
              "printquery",
              {
                method: "POST",
                body: JSON.stringify({
                  type: operator,
                  filters: filters,
                }),
              },
              (p) =>
                p
                  .then((response) => response.text())
                  .then(searchModel.statusChanged)
            );
          const showDialog = (callback: (...filters: ActionFilter[]) => void) =>
            addFilterDialog(
              onActionPage,
              sources,
              tags,
              (accessor, start, end) =>
                callback({ start: start, end: end, type: accessor.rangeType }),
              (accessor, value) => {
                if (value) {
                  callback({ offset: value, type: accessor.horizonType });
                }
              },
              (type, values) => {
                switch (type) {
                  case "id":
                    callback({ ids: values, type: "id" });
                    break;
                  case "status":
                    callback({ states: values as Status[], type: "status" });
                    break;
                  case "tag":
                    callback({ tags: values, type: "tag" });
                    break;
                  case "type":
                    callback({ types: values, type: "type" });
                    break;
                  default:
                    throw new Error("Unsupported type: " + type);
                }
              },
              (locations) => {
                callback({ locations: locations, type: "sourcelocation" });
              },
              (text, matchCase) =>
                callback({
                  type: "text",
                  text: text,
                  matchCase: matchCase,
                }),
              (pattern, matchCase) =>
                callback({
                  type: "regex",
                  pattern: pattern,
                  matchCase: matchCase,
                })
            );
          if (search.value.trim()) {
            fetchJsonWithBusyDialog<ParseQueryRespose>(
              "parsequery",
              {
                method: "POST",
                body: JSON.stringify(search.value),
              },
              (result) => {
                const existing = result.filter;
                if (existing) {
                  showDialog((filter) => replaceQuery(existing, filter));
                  return Promise.resolve(existing);
                } else {
                  return Promise.reject("Can't add clauses to a broken query.");
                }
              }
            );
          } else {
            showDialog((filter) => replaceQuery(filter));
          }
        })
      ),
    ],
    entryBar: [
      search,
      br(),
      collapsible(
        "Help",
        paragraph(
          "Conjunction: ",
          italic("expr"),
          mono(" and "),
          italic("expr")
        ),
        paragraph(
          "Disjunction: ",
          italic("expr"),
          mono(" or "),
          italic("expr")
        ),
        paragraph("Negation: ", mono("not "), italic("expr")),
        paragraph("Action ID: ", mono("shesmu:"), italic("hash")),
        paragraph("Saved Search: ", mono("shesmusearch:"), italic("uglystuff")),
        paragraph(
          "Text: ",
          mono('text = "'),
          italic("string"),
          mono('"'),
          " or ",
          mono('text != "'),
          italic("string"),
          mono('"'),
          " or ",
          mono("text ~ /"),
          italic("regex"),
          mono("/"),

          " or ",
          mono("text !~ /"),
          italic("regex"),
          mono("/"),
          "[",
          mono("i"),
          "]"
        ),
        paragraph(
          "Sets of stuff: ",
          "(",
          mono("file"),
          "|",
          mono("status"),
          "|",
          mono("tag"),
          "|",
          mono("type"),
          ") (",
          mono(" = "),
          italic("name"),
          " | ",
          mono(" != "),
          italic("name"),
          " | ",
          mono(" in ("),
          italic("name1"),
          mono(", "),
          italic("name2"),
          ", ...",
          mono(")"),
          " | ",
          mono(" not in ("),
          italic("name1"),
          mono(", "),
          italic("name2"),
          ", ...",
          mono(")"),
          ")"
        ),

        paragraph(
          "Times: ",
          "(",
          mono("generated"),
          " | ",
          mono("checked"),
          " | ",
          mono("external"),
          " | ",
          mono("status_changed"),

          ") (",
          mono("last "),
          italic("timespan"),
          " | ",
          mono("prior "),
          italic("timespan"),
          " | ",
          mono("after "),
          italic("datetime"),
          " | ",
          mono("before "),
          italic("datetime"),
          " | ",
          mono("between "),
          italic("datetime"),
          mono(" to "),
          italic("datetime"),
          " | ",
          mono("outside "),
          italic("datetime"),
          mono(" to "),
          italic("datetime"),
          " )"
        ),

        paragraph(
          "Timespan: ",
          italic("number"),
          "(",
          mono("days"),
          "|",
          mono("hours"),
          "|",
          mono("mins"),
          "|",
          mono("secs"),
          "|",
          mono("millis"),
          ")"
        ),
        paragraph(
          "Date-time: (",
          mono("today"),
          " | ",
          mono("yesterday"),
          " | ",
          mono("monday"),
          " | ... | ",
          mono("friday"),
          " | ",
          italic("YYYY"),
          mono("-"),
          italic("mm"),
          mono("-"),
          italic("dd"),
          ") (",
          mono("current"),
          " | ",
          mono("midnight"),
          " | ",
          mono("noon"),
          " | ",
          italic("HH"),
          mono(":"),
          italic("MM"),
          mono(":"),
          italic("SS"),
          mono(":"),
          ") (",
          mono("server"),
          " | ",
          mono("utc"),
          ")?"
        )
      ),
    ],
    find: null,
    addRangeSearch: (typeName, start, end) => {
      searchModel.statusChanged(
        `(${search.value}) and ${typeName} between ${new Date(
          start
        ).toISOString()} to ${new Date(end).toISOString()}`
      );
    },
    addPropertySearch: (...limits) => {
      const propertyQuery = limits
        .map(
          ([name, value]) =>
            `${name == "sourcefile" ? "source" : name} = "${value.replace(
              '"',
              '\\"'
            )}"`
        )
        .join(" and ");
      searchModel.statusChanged(`(${search.value}) and ${propertyQuery}`);
    },
  };
}

function searchBasic(
  initial: BasicQuery,
  model: StatefulModel<ActionFilter[]>,
  synchronizer: StateSynchronizer<string | BasicQuery>,
  onActionPage: boolean,
  filenameFormatter: FilenameFormatter,
  sources: SourceLocation[],
  tags: string[]
): SearchPlatform {
  let current = initial;
  const {
    ui: searchView,
    model: viewModel,
  } = singleState((query: BasicQuery) =>
    renderFilters(query, filenameFormatter, searchModel.statusChanged)
  );
  const searchModel: StatefulModel<BasicQuery> = combineModels(
    mapModel(model, createFilters),
    mapModel(synchronizer, (x) => x),
    viewModel
  );

  searchModel.statusChanged(current);
  return {
    buttons: [
      buttonAccessory(
        "➕ Add Filter",
        "Add a filter to limit the actions displayed.",
        () =>
          addFilterDialog(
            onActionPage,
            sources,
            tags,
            (type, start, end) =>
              searchModel.statusChanged({
                ...current,
                [type.rangeType]: {
                  start: start,
                  end: end,
                },
              }),
            (type, value) =>
              searchModel.statusChanged({
                ...current,
                [type.horizonType]: value,
              }),
            (type, values) => {
              searchModel.statusChanged({
                ...current,
                [type]: values
                  .concat(current[type] || [])
                  .sort()
                  .filter(
                    (item, index, arr) => index == 0 || arr[index - 1] != item
                  ),
              });
            },
            (locations) => {
              searchModel.statusChanged({
                ...current,
                sourcelocation: mergeLocations(
                  locations,
                  current.sourcelocation || []
                ),
              });
            },
            (text, matchCase) =>
              searchModel.statusChanged({
                ...current,
                text: [
                  {
                    text: text,
                    matchCase: matchCase,
                  },
                ].concat(current.text || []),
              }),
            (pattern, matchCase) =>
              searchModel.statusChanged({
                ...current,
                regex: [
                  {
                    pattern: pattern,
                    matchCase: matchCase,
                  },
                ].concat(current.regex || []),
              })
          )
      ),

      button(
        "⌨️ Advanced",
        "Switch to advanced query interface. Query will be saved, but cannot be converted back.",
        () =>
          promiseModel(synchronizer).statusChanged(
            fetch("/printquery", {
              method: "POST",
              body: JSON.stringify({
                type: "and",
                filters: createFilters(current),
              }),
            }).then((response) =>
              response.ok
                ? response.text()
                : Promise.reject(
                    new Error(
                      `Failed to load: ${response.status} ${response.statusText}`
                    )
                  )
            )
          )
      ),
      buttonAccessory(
        "⌫ Clear",
        "Remove all search filters and view everything.",
        () => searchModel.statusChanged({})
      ),
    ],

    entryBar: searchView,
    find: () => {
      editText({ text: "", matchCase: false }, (text, matchedCase) =>
        searchModel.statusChanged({
          ...current,
          text: [{ text: text, matchCase: matchedCase }].concat(
            current.text || []
          ),
        })
      );
      return true;
    },
    addRangeSearch: (typeName, start, end) =>
      searchModel.statusChanged({
        ...current,
        [typeName]: {
          start: start,
          end: end,
        },
      }),

    addPropertySearch: (...properties) => {
      const replacement = { ...current };
      for (const [property, value] of properties) {
        replacement[property] = [...(replacement[property] || []), value];
      }
      searchModel.statusChanged(replacement);
    },
  };
}
function timeDialog(
  callback: (accessor: BasicQueryTimeAccessor) => void
): void {
  dialog((close) =>
    timeAccessors.map((accessor) => [
      button(nameForBin(accessor.rangeType), "", () => {
        close();
        callback(accessor);
      }),
      br(),
    ])
  );
}
function timeRangeAnchor(
  leader: string,
  time: number | null,
  trailer: string
): UIElement {
  if (time) {
    const { ago, absolute } = computeDuration(time);
    return text(leader + ago + trailer, absolute);
  } else {
    return blank();
  }
}

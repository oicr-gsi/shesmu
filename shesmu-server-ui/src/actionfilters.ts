import {
  ActiveItemRenderer,
  DisplayElement,
  FindHandler,
  IconName,
  StateSynchronizer,
  UIElement,
  blank,
  br,
  butter,
  button,
  buttonAccessory,
  buttonClose,
  buttonEdit,
  collapsible,
  dateEditor,
  dialog,
  dropdown,
  groupWithFind,
  inputCheckbox,
  inputNumber,
  inputSearchBar,
  inputText,
  inputTextArea,
  italic,
  mono,
  pane,
  paragraph,
  pickFromSet,
  pickFromSetCustom,
  preformatted,
  singleState,
  tableFromRows,
  tableRow,
  temporaryState,
  text,
  tile,
} from "./html.js";
import { AddRangeSearch, AddPropertySearch, PropertySearch } from "./stats.js";
import { Status, statusButton, statusDescription, statuses } from "./action.js";
import { actionRender } from "./actions.js";
import {
  FilenameFormatter,
  SourceLocation,
  StatefulModel,
  bypassModel,
  combineModels,
  commonPathPrefix,
  computeDuration,
  errorModel,
  filterModel,
  formatTimeSpan,
  mapModel,
  mergeLocations,
  mergingModel,
  promiseModel,
  observableModel,
  ObservableModel,
} from "./util.js";
import { fetchJsonWithBusyDialog, refreshable, fetchAsPromise } from "./io.js";

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
  negate?: boolean;
  offset: number;
  type: "addedago";
}

export interface ActionFilterAdded {
  end: number | null;
  negate?: boolean;
  start: number | null;
  type: "added";
}

export interface ActionFilterAnd {
  filters: ActionFilter[];
  negate?: boolean;
  type: "and";
}

export interface ActionFilterCheckedAgo {
  negate?: boolean;
  offset: number;
  type: "checkedago";
}

export interface ActionFilterChecked {
  end: number | null;
  negate?: boolean;
  start: number | null;
  type: "checked";
}

export interface ActionFilterExternalAgo {
  negate?: boolean;
  offset: number;
  type: "externalago";
}

export interface ActionFilterExternal {
  end: number | null;
  negate?: boolean;
  start: number | null;
  type: "external";
}

export interface ActionFilterIds {
  ids: string[];
  negate?: boolean;
  type: "id";
}

export interface ActionFilterOr {
  filters: ActionFilter[];
  negate?: boolean;
  type: "or";
}

export interface ActionFilterRegex {
  matchCase: boolean;
  negate?: boolean;
  pattern: string;
  type: "regex";
}

export interface ActionFilterSourceFile {
  files: string[];
  negate?: boolean;
  type: "sourcefile";
}

export interface ActionFilterSourceLocation {
  locations: SourceLocation[];
  negate?: boolean;
  type: "sourcelocation";
}

export interface ActionFilterStatusChangedAgo {
  offset: number;
  negate?: boolean;
  type: "statuschangedago";
}

export interface ActionFilterStatusChanged {
  end: number | null;
  start: number | null;
  negate?: boolean;
  type: "statuschanged";
}

export interface ActionFilterStatus {
  negate?: boolean;
  states: Status[];
  type: "status";
}

export interface ActionFilterTag {
  negate?: boolean;
  tags: string[];
  type: "tag";
}

export interface ActionFilterText {
  matchCase: boolean;
  negate?: boolean;
  text: string;
  type: "text";
}

export interface ActionFilterType {
  negate?: boolean;
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

interface LogicalOperator {
  operator: "and" | "or";
  icon: IconName;
  tip: string;
}
/**
 * The response format from the server when attempting to parse a text query
 */
export interface ParseQueryResponse {
  errors: ParseQueryError[];
  formatted?: string;
  filter?: ActionFilter;
}
export interface ParseQueryError {
  line: number;
  column: number;
  message: string;
}

export type PropertyType = "status" | "sourcefile" | "tag" | "type";

export type SetType = "id" | "tag" | "type";
/**
 * The client supports two different action querying modes: basic/GUI and advanced/text search
 *
 * This is how each query format interacts with the rest of the dashboard
 */
interface SearchPlatform {
  /**
   * The toolbar for this search interface
   */
  buttons: UIElement;
  /**
   * The filter list or search box area
   */
  entryBar: UIElement;
  /**
   * The Ctrl-F handler
   */
  find: FindHandler;
  /**
   * A check if the input query is compatible with this search platform
   */
  handles(query: string | BasicQuery): boolean;
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
  getFilters: (callback: (filters: ActionFilter[]) => void) => void,
  timeRange: (
    accessor: BasicQueryTimeAccessor,
    start: number | null,
    end: number | null
  ) => void,
  timeAgo: (accessor: BasicQueryTimeAccessor, offset: number | null) => void,
  addSet: (type: SetType, items: string[]) => void,
  addStatus: (status: Status[]) => void,
  addLocations: (locations: SourceLocation[]) => void,
  setText: TextHandler,
  setRegex: TextHandler
): void {
  dialog((close) => [
    { type: "icon", icon: "clock-fill" },
    { type: "b", contents: "Time" },
    br(),
    button(
      [{ type: "icon", icon: "calendar-range" }, "Fixed Time Range"],
      "Add a filter that restricts between two absolute times.",
      () => {
        close();
        timeDialog((n) =>
          editTimeRange(
            {
              start: new Date().getTime() - 3_600_000,
              end: new Date().getTime(),
            },
            (start, end) => timeRange(n, start, end)
          )
        );
      }
    ),
    button(
      [{ type: "icon", icon: "clock-history" }, "Time Since Now"],
      "Add a filter that restricts using a sliding window.",
      () => {
        close();
        timeDialog((n) => editTimeHorizon(0, (update) => timeAgo(n, update)));
      }
    ),
    br(),
    { type: "icon", icon: "camera-reels-fill" },
    { type: "b", contents: "From the Action" },
    br(),

    button(
      [{ type: "icon", icon: "eye" }, "Action Identifier"],
      "Add a unique action identifier.",
      () => {
        close();
        const input = inputTextArea();
        dialog((close) => [
          "Action Identifiers (",
          mono("shesmu:"),
          italic("40 hex characters"),
          " - other text will be ignored):",
          br(),
          input.ui,
          br(),
          button(
            "Add All",
            "Add any action IDs in the text to the filter.",
            () => {
              const ids = Array.from(
                input.value.matchAll(/shesmu:([0-9A-Fa-f]{40})/g),
                (m) => "shesmu:" + m[1].toUpperCase()
              );
              if (ids.length) {
                close();
                addSet("id", ids);
              } else {
                butter(3000, "The text you entered has no valid identifiers.");
              }
            }
          ),
        ]);
      }
    ),
    button(
      [{ type: "icon", icon: "chat-left-text" }, "Text"],
      "Add a filter that looks for actions with specific text.",
      () => {
        close();
        editText({ text: "", matchCase: false }, setText);
      }
    ),
    button(
      [{ type: "icon", icon: "chat-left-dots" }, "Regular Expression"],
      "Add a filter that looks for actions that match a regular expression.",
      () => {
        close();
        editRegex({ pattern: "", matchCase: false }, setRegex);
      }
    ),
    button(
      [{ type: "icon", icon: "flag-fill" }, "Status"],
      "Add a filter that searches for actions in a particular state.",
      () => {
        close();
        pickFromSetCustom(
          statuses,
          (status) => addStatus(status),
          (status, click) => [
            statusButton(status, click),
            statusDescription(status),
          ],
          (status, keywords) =>
            keywords.every(
              (k) =>
                status.toLowerCase().indexOf(k) != -1 ||
                statusDescription(status)
                  .toLowerCase()
                  .indexOf(k) != -1
            ),
          true
        );
      }
    ),
    onActionPage
      ? button(
          [{ type: "icon", icon: "camera-reels" }, "Action Type"],
          "Add a filter that searches for actions of a particular type.",
          () => {
            close();
            pickFromSet(
              Array.from(actionRender.keys()).sort(),
              (type) => addSet("type", type),
              (type) => ({ label: type, title: "" }),
              (type, keywords) =>
                keywords.every((k) => type.toLowerCase().indexOf(k) != -1),
              false
            );
          }
        )
      : blank(),
    br(),
    { type: "icon", icon: "file-code-fill" },
    { type: "b", contents: "From the Olive" },
    br(),
    button(
      [{ type: "icon", icon: "tags" }, "Tags"],
      "Add a filter that searches for actions marked with a particular tag by an olive.",
      () => {
        close();
        getFilters((filters) =>
          fetchJsonWithBusyDialog("tags", filters, (tags) => {
            if (tags.length) {
              pickFromSet(
                tags.sort(),
                (tag) => addSet("tag", tag),
                (tag) => ({ label: tag, title: "" }),
                (tag, keywords) =>
                  keywords.every((k) => tag.toLowerCase().indexOf(k) != -1),
                false
              );
            } else {
              dialog((_close) => text("No tags are available."));
            }
          })
        );
      }
    ),
    sources.length
      ? button(
          [{ type: "icon", icon: "geo-alt-fill" }, "Source Olive"],
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
              (sourceLocation) => {
                const label: DisplayElement[] = [
                  fileNameFormatter(sourceLocation.file),
                ];
                if (sourceLocation.line) {
                  label.push(":" + sourceLocation.line);
                  if (sourceLocation.column) {
                    label.push(":" + sourceLocation.column);
                  }
                  if (sourceLocation.hash) {
                    label.push("[" + sourceLocation.hash + "]");
                  }
                }
                return {
                  label: label,
                  title: sourceLocation.file,
                };
              },
              (sourceLocation, keywords) =>
                keywords.every(
                  (k) => sourceLocation.file.toLowerCase().indexOf(k) != -1
                ),
              true,
              "Entries like ",
              mono("foo.shesmu"),
              " will match any action produced by any olive from that file. Entires like ",
              mono("foo.shesmu:5"),
              " and ",
              mono("foo.shesmu:5:3"),
              " match specific olives by line and columns.",
              mono("foo.shesmu:5:3[0123456789ABCDEF]"),
              " matches a specific version of script, even if it has been replaced."
            );
          }
        )
      : blank(),
  ]);
}

function combineSet<T>(list: T[] | undefined | null, item: T): T[] {
  const set = new Set(list || []);
  set.add(item);
  return [...set];
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
  sources: SourceLocation[]
): {
  buttons: UIElement;
  entryBar: UIElement;
  model: StatefulModel<ActionFilter[]>;
  addRangeSearch: AddRangeSearch;
  addPropertySearch: AddPropertySearch;
} {
  const buttons = pane("blank");
  const entryBar = pane("blank");
  const baseFilters = observableModel<ActionFilter[]>([]);
  const [baseModel, queryModel] = mergingModel(
    filterModel(model, "Missing base search."),
    (left: ActionFilter[] | null, right: ActionFilter[] | null) =>
      left ? left.concat(right || []) : null
  );
  let search: SearchPlatform | undefined;
  synchronizer.listen((query, internal) => {
    // This callback will get two kinds of events: when the user hits the back button (external) and when the search platform updates itself. Normally, we don't care, but a search platform updates itself when it wants to switch between advanced and basic queries. So, if the current search platform can't handle the query, we will reinitialise it. Otherwise, we just let it handle the event itself.
    if (internal && (!search || search.handles(query))) {
      return;
    }
    // We reset the search platform so that during initialisation, when we hit this callback again, we hit the early return.
    search = undefined;
    if (typeof query == "string") {
      search = searchAdvanced(
        query,
        queryModel,
        synchronizer,
        onActionPage,
        sources,
        baseFilters
      );
    } else {
      search = searchBasic(
        query,
        queryModel,
        synchronizer,
        onActionPage,
        filenameFormatter,
        sources,
        baseFilters
      );
    }
    buttons.model.statusChanged(search.buttons);
    entryBar.model.statusChanged(search.entryBar);
  });
  return {
    buttons: buttons.ui,
    entryBar: groupWithFind(() => {
      const find = search?.find;
      return find ? find() : false;
    }, entryBar.ui),
    model: combineModels(baseModel, baseFilters),
    addRangeSearch: (typeName, start, end) =>
      search?.addRangeSearch(typeName, start, end),
    addPropertySearch: (...limits) => search?.addPropertySearch(...limits),
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
        callback(text.value, matched.value);
      }),
    ];
  });
}
function editRegex(original: BasicRegex, callback: TextHandler) {
  dialog((close) => {
    const pattern = inputText(original.pattern);
    const error = pane("blank");
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
          new RegExp(pattern.value);
        } catch (e) {
          error.model.statusChanged(e.message);
          return;
        }
        close();
        callback(pattern.value, matched.value);
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
        const startTime = start.getter();
        const endTime = end.getter();
        if (startTime === null && endTime === null) {
          butter(3000, "You have selected neither a start nor end time.");
        } else {
          close();
          callback(start.getter(), end.getter());
        }
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
        (unit) => unit == units.get(),
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
          Number.isNaN(offset.value) ? null : offset.value * units.get()[0]
        );
      }),
    ];
  });
}
/** Convert the format used for property search descriptions into action filters */
export function filtersForPropertySearch(
  ...limits: PropertySearch[]
): ActionFilter[] {
  return limits.map(
    (limit): ActionFilter => {
      switch (limit.type) {
        case "status":
          return { type: "status", states: [limit.value] };
        case "tag":
          return { type: "tag", tags: [limit.value] };
        case "sourcefile":
          return { type: "sourcefile", files: [limit.value] };
        case "type":
          return { type: "type", types: [limit.value] };
        default:
          throw new Error("Unhandled limit");
      }
    }
  );
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
function recomposeFilter(filter: ActionFilter): BasicQuery | null {
  const result: BasicQuery = {};
  return recomposeFilterHelper(filter, result) ? result : null;
}
function recomposeFilterHelper(
  filter: ActionFilter,
  result: BasicQuery
): boolean {
  if (filter.negate) {
    return false;
  }
  switch (filter.type) {
    case "added":
    case "checked":
    case "external":
    case "statuschanged":
      if (result[filter.type]) {
        return false;
      } else {
        result[filter.type] = { start: filter.start, end: filter.end };
        return true;
      }
    case "addedago":
    case "checkedago":
    case "externalago":
    case "statuschangedago":
      if (result[filter.type]) {
        return false;
      } else {
        result[filter.type] = filter.offset;
        return true;
      }
    case "id":
      if (result.id) {
        return false;
      } else {
        result.id = filter.ids;
        return true;
      }
    case "sourcefile":
      if (result.sourcefile) {
        return false;
      } else {
        result.sourcefile = filter.files;
        return true;
      }
    case "sourcelocation":
      if (result.sourcelocation) {
        return false;
      } else {
        result.sourcelocation = filter.locations;
        return true;
      }
    case "tag":
      if (result.tag) {
        return false;
      } else {
        result.tag = filter.tags;
        return true;
      }
    case "status":
      if (result.status) {
        return false;
      } else {
        result.status = filter.states;
        return true;
      }
    case "type":
      if (result.type) {
        return false;
      } else {
        result.type = filter.types;
        return true;
      }
    case "regex":
      result.regex = [
        ...(result.regex || []),
        { pattern: filter.pattern, matchCase: filter.matchCase },
      ];
      return true;
    case "text":
      result.text = [
        ...(result.text || []),
        { text: filter.text, matchCase: filter.matchCase },
      ];
      return true;
    case "and":
      for (const child of filter.filters) {
        if (!recomposeFilterHelper(child, result)) {
          return false;
        }
      }
      return true;
    case "or":
      switch (filter.filters.length) {
        case 0:
          return true;
        case 1:
          return recomposeFilterHelper(filter.filters[0], result);
        default:
          return false;
      }

    default:
      return false;
  }
}
function renderFilters(
  query: BasicQuery,
  filenameFormatter: FilenameFormatter,
  update: (query: BasicQuery) => void
): UIElement {
  const ui: UIElement[] = [];
  if (query.type && query.type.length > 0) {
    ui.push(
      renderSet(
        query.type,
        "Action Type",
        (type, click) => button(type, "Click to remove.", click),
        (items) => update({ ...query, type: items.length ? items : undefined })
      )
    );
  }
  if (query.status && query.status.length > 0) {
    ui.push(
      renderSet(
        query.status,
        "Action Status",
        (status, click) => statusButton(status, click),
        (items) =>
          update({ ...query, status: items.length ? items : undefined })
      )
    );
  }
  if (query.tag && query.tag.length > 0) {
    ui.push(
      renderSet(
        query.tag,
        "Tags",
        (tag, click) => button(tag, "Click to remove.", click),
        (items) => update({ ...query, tag: items.length ? items : undefined })
      )
    );
  }
  if (query.id && query.id.length > 0) {
    ui.push(
      renderSet(
        query.id,
        "Action IDs",
        (id, click) => [button(id, "Click to remove.", click), br()],
        (items) => update({ ...query, id: items.length ? items : undefined })
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
        (items) =>
          update({ ...query, sourcefile: items.length ? items : undefined })
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
                update({
                  ...query,
                  sourcelocation: replacement.length ? replacement : undefined,
                });
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
              ? [
                  { type: "icon", icon: "clock" },
                  formatTimeSpan(range.end - range.start),
                ]
              : blank(),
            timeRangeAnchor("— ", range.end, " ⇥")
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
          { type: "icon", icon: "clock-history" },
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
  return ui.length ? ui : "No filters.";
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
  baseFilters: ObservableModel<ActionFilter[]>
): SearchPlatform {
  const searchModel: StatefulModel<string> = bypassModel(
    combineModels(
      errorModel(model, (response: ParseQueryResponse | null) => {
        if (!response) {
          return { type: "ok", value: [] };
        }
        if (response.filter) {
          return { type: "ok", value: [response.filter] };
        }
        return {
          type: "error",
          message:
            response.errors
              .map((e) => `${e.line}:${e.column}: ${e.message}`)
              .join("; ") || "Unknown error.",
        };
      }),
      mapModel(filterModel(synchronizer, "Parse error."), (response) =>
        response ? response.formatted || null : ""
      ),
      {
        reload: () => {},
        statusChanged: (response: ParseQueryResponse | null) => {
          if (response?.formatted) {
            search.value = response.formatted;
          }
        },
        statusFailed: () => {},
        statusWaiting: () => {},
      }
    ),
    (output: StatefulModel<ParseQueryResponse | null>) =>
      refreshable("parsequery", output, true),
    (input: string) => {
      if (input.trim().length) {
        return { bypass: false, value: input };
      } else {
        return {
          bypass: true,
          value: null,
        };
      }
    }
  );

  const search = inputSearchBar(filter, searchModel);

  searchModel.statusChanged(filter);
  const updateFromClick = (...filters: ActionFilter[]) => {
    const doUpdate = (existingQuery: ActionFilter[]) => {
      fetchJsonWithBusyDialog(
        "printquery",
        {
          type: "and",
          filters: filters.concat(existingQuery),
        },
        searchModel.statusChanged
      );
    };
    if (search.value.trim()) {
      fetchJsonWithBusyDialog("parsequery", search.value, (result) => {
        const existing = result.filter;
        if (existing) {
          doUpdate([existing]);
          return Promise.resolve(existing);
        } else {
          butter(3000, "Can't add conditions to a broken query.");
          return Promise.reject("Can't add conditions to a broken query.");
        }
      });
    } else {
      doUpdate([]);
    }
  };
  return {
    buttons: [
      button(
        [{ type: "icon", icon: "mouse" }, "Basic"],
        "Switch to basic query interface. Current query will be lost.",
        () => {
          if (search.value.trim()) {
            fetchJsonWithBusyDialog("parsequery", search.value, (result) => {
              const existing = result.filter;
              const convertedQuery = existing
                ? recomposeFilter(existing)
                : null;
              if (convertedQuery) {
                synchronizer.statusChanged(convertedQuery);
              } else {
                dialog((close) => [
                  "Switching to basic query interface will discard current query.",
                  br(),
                  "The query does not have an equivalent basic form.",
                  br(),
                  button(
                    "Stay here",
                    "Stay in the advanced query interface.",
                    close
                  ),
                  button(
                    "Discard and Switch",
                    "Switch to the basic query interface.",
                    () => {
                      close();
                      synchronizer.statusChanged({});
                    }
                  ),
                ]);
              }
            });
          } else {
            synchronizer.statusChanged({});
          }
        }
      ),
      buttonAccessory(
        [{ type: "icon", icon: "funnel" }, "Add Filter"],
        "Add a filter to limit the actions displayed.",
        () => {
          const replaceQuery = (
            operator: "and" | "or",
            ...filters: ActionFilter[]
          ) =>
            fetchJsonWithBusyDialog(
              "printquery",
              {
                type: operator,
                filters: filters,
              },
              searchModel.statusChanged
            );
          const showDialog = (callback: (...filters: ActionFilter[]) => void) =>
            addFilterDialog(
              onActionPage,
              sources,
              (filterCallback) =>
                fetchJsonWithBusyDialog("parsequery", search.value, (result) =>
                  filterCallback(
                    result.filter
                      ? baseFilters.value.concat([result.filter])
                      : []
                  )
                ),
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
              (values) => {
                callback({ states: values, type: "status" });
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
            fetchJsonWithBusyDialog("parsequery", search.value, (result) => {
              const existing = result.filter;
              if (existing) {
                showDialog((filter) =>
                  dialog((close) =>
                    [
                      {
                        operator: "and",
                        icon: "intersect",
                        tip: "Add a filter that restricts the existing query.",
                      } as LogicalOperator,
                      {
                        operator: "or",
                        icon: "union",
                        tip: "Add a filter that expands the existing query.",
                      } as LogicalOperator,
                    ].map((operation) =>
                      button(
                        [
                          { type: "icon", icon: operation.icon },
                          "Existing Query ",
                          italic(operation.operator),
                          " New Filter",
                        ],
                        operation.tip,
                        () => {
                          close();
                          replaceQuery(operation.operator, existing, filter);
                        }
                      )
                    )
                  )
                );
                return Promise.resolve(existing);
              } else {
                return Promise.reject("Can't add clauses to a broken query.");
              }
            });
          } else {
            showDialog((filter) => replaceQuery("and", filter));
          }
        }
      ),
    ],
    entryBar: [
      "Action query: ",
      search.ui,
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
    handles(query: string | BasicQuery): boolean {
      return typeof query == "string";
    },
    addRangeSearch: (typeName, start, end) => {
      updateFromClick({ type: typeName, start: start, end: end });
    },
    addPropertySearch: (...limits) => {
      updateFromClick(...filtersForPropertySearch(...limits));
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
  baseFilters: ObservableModel<ActionFilter[]>
): SearchPlatform {
  let current = { ...initial };
  const { ui: searchView, model: viewModel } = singleState(
    (query: BasicQuery) =>
      tile(
        ["filters"],
        renderFilters(query, filenameFormatter, (modification) => {
          current = modification;
          searchModel.statusChanged(modification);
        })
      )
  );
  const searchModel: StatefulModel<BasicQuery> = combineModels(
    mapModel(model, createFilters),
    mapModel(synchronizer, (x) => x),
    viewModel
  );

  searchModel.statusChanged({ ...current });
  return {
    buttons: [
      buttonAccessory(
        [{ type: "icon", icon: "funnel" }, "Add Filter"],
        "Add a filter to limit the actions displayed.",
        () =>
          addFilterDialog(
            onActionPage,
            sources,
            (filterCallback) =>
              filterCallback(baseFilters.value.concat(createFilters(current))),
            (type, start, end) => {
              current[type.rangeType] = {
                start: start,
                end: end,
              };
              searchModel.statusChanged({ ...current });
            },
            (type, value) => {
              if (value) {
                current[type.horizonType] = value;
              } else {
                delete current[type.horizonType];
              }
              searchModel.statusChanged({ ...current });
            },
            (type, values) => {
              current[type] = values
                .concat(current[type] || [])
                .sort()
                .filter(
                  (item, index, arr) => index == 0 || arr[index - 1] != item
                );
              searchModel.statusChanged({ ...current });
            },
            (values) => {
              current.status = values
                .concat(current.status || [])
                .sort()
                .filter(
                  (item, index, arr) => index == 0 || arr[index - 1] != item
                );
              searchModel.statusChanged({ ...current });
            },
            (locations) => {
              const newLocations = mergeLocations(
                locations,
                current.sourcelocation || []
              );
              if (newLocations.length) {
                current.sourcelocation = newLocations;
              } else {
                delete current.sourcelocation;
              }

              searchModel.statusChanged({ ...current });
            },
            (text, matchCase) => {
              current.text = [
                {
                  text: text,
                  matchCase: matchCase,
                },
              ].concat(current.text || []);

              searchModel.statusChanged({ ...current });
            },
            (pattern, matchCase) => {
              current.regex = [
                {
                  pattern: pattern,
                  matchCase: matchCase,
                },
              ].concat(current.regex || []);
              searchModel.statusChanged({ ...current });
            }
          )
      ),

      button(
        [{ type: "icon", icon: "keyboard-fill" }, "Advanced"],
        "Switch to advanced query interface. Query will be saved, but cannot be converted back.",
        () =>
          promiseModel(synchronizer).statusChanged(
            fetchAsPromise("printquery", {
              type: "and",
              filters: createFilters(current),
            })
          )
      ),
      buttonAccessory(
        [{ type: "icon", icon: "backspace" }, "Clear Filters"],
        "Remove all search filters and view everything.",
        () => {
          current = {};
          searchModel.statusChanged(current);
        }
      ),
    ],

    entryBar: searchView,
    find: () => {
      editText({ text: "", matchCase: false }, (text, matchedCase) => {
        current.text = [{ text: text, matchCase: matchedCase }].concat(
          current.text || []
        );
        searchModel.statusChanged({
          ...current,
        });
      });
      return true;
    },
    handles(query: string | BasicQuery): boolean {
      return typeof query != "string";
    },
    addRangeSearch: (typeName, start, end) => {
      current[typeName] = {
        start: start,
        end: end,
      };
      searchModel.statusChanged({ ...current });
    },

    addPropertySearch: (...limits) => {
      const result = updateBasicQueryForPropertySearch(limits, current);
      if (result instanceof Promise) {
        promiseModel(synchronizer).statusChanged(result);
      } else {
        searchModel.statusChanged(result);
      }
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

export function updateBasicQueryForPropertySearch(
  limits: PropertySearch[],
  current: BasicQuery
): Promise<string> | BasicQuery {
  // If we have to switch to advanced mode, collect all those filters here
  let advancedFilters: ActionFilter[] = [];
  for (const limit of limits) {
    if (limit.type == "status") {
      current[limit.type] = combineSet(current[limit.type], limit.value);
    } else {
      if (limit.type == "tag" && current.tag?.length) {
        advancedFilters.push({ type: "tag", tags: [limit.value] });
      } else {
        current[limit.type] = combineSet(current[limit.type], limit.value);
      }
    }
  }
  if (advancedFilters.length) {
    butter(
      5000,
      "Switching to advanced mode. Use the browser's back button to return to the previous basic-mode search."
    );
    // Take what we have and switch to advanced mode
    return fetchAsPromise("printquery", {
      type: "and",
      filters: createFilters(current).concat(advancedFilters),
    });
  } else {
    return { ...current };
  }
}

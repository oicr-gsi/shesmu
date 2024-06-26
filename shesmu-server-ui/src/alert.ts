import { fetchJsonWithBusyDialog, refreshable } from "./io.js";
import {
  DisplayElement,
  StateSynchronizer,
  UIElement,
  UpdateableList,
  blank,
  br,
  button,
  buttonAccessory,
  buttonClose,
  dialog,
  flexGroup,
  group,
  groupWithFind,
  historyState,
  inputText,
  link,
  multipaneState,
  objectTable,
  pager,
  radioSelector,
  refreshButton,
  setRootDashboard,
  sharedPane,
  singleState,
  statefulListBind,
  synchronizerFields,
  table,
  tableFromRows,
  tableRow,
  text,
  tile,
} from "./html.js";
import {
  FilenameFormatter,
  SourceLocation,
  commonPathPrefix,
  computeDuration,
} from "./util.js";
import { helpArea } from "./help.js";

/**
 * An alert metatype
 *
 * Since simulation and real olives produce different alert formats, this is the base shared between both of them.
 */
export interface Alert<L> {
  live: boolean;
  annotations: { [label: string]: string };
  labels: { [label: string]: string };
  locations: L[];
  startsAt: string;
  endsAt: string;
}
/**
 * An alert filter
 */
export type AlertFilter<R> =
  | AlertFilterEq
  | AlertFilterHas
  | AlertFilterHasRegex<R>
  | AlertFilterLive
  | AlertFilterNe
  | AlertFilterRegExp<R>
  | AlertFilterSourceLocation;

/**
 * An alert filter that checks for a value to be equal to a reference value.
 */
export interface AlertFilterEq {
  label: string;
  value: string;
  type: "eq";
}
/**
 * An alert filter that checks for a certain label to be present.
 */
export interface AlertFilterHas {
  label: string;
  value: null;
  type: "has";
}
/**
 * An alert filter that checks for a certain label to match a regular expression.
 */
export interface AlertFilterHasRegex<R> {
  label: R;
  value: null;
  type: "has-regex";
}
/**
 * An alert filter that checks for an alert to have a certain liveness.
 */
export interface AlertFilterLive {
  label: null;
  value: boolean;
  type: "live";
}
/**
 * An alert filter that checks for a value to be not equal to a reference value.
 */
export interface AlertFilterNe {
  label: string;
  value: string;
  type: "ne";
}
/**
 * An alert filter that checks for a value to match a regular expression.
 */
export interface AlertFilterRegExp<R> {
  label: string;
  value: R;
  type: "regex";
}

export interface AlertFilterSourceLocation {
  locations: SourceLocation[];
  type: "sourcelocation";
}

type AlertFilterRenderer = (alerts: AlertFilter<RegExp>[]) => UIElement;

/**
 * The column formats for displaying the source location of the olives that produced an alert
 */
export type LocationColumns<L> = [string, (location: L) => UIElement][];

/**
 * The type of real alerts
 */
export interface PrometheusAlert extends Alert<SourceLocation> {
  generatorURL: string;
}

export type ServerAlertFilter =
  | { type: "and"; filters: ServerAlertFilter[]; negate?: boolean }
  | { type: "sourcelocation"; locations: SourceLocation[]; negate?: boolean }
  | { type: "has"; isRegex: boolean; name: string; negate?: boolean }
  | {
      type: "eq";
      isRegex: boolean;
      name: string;
      value: string;
      negate?: boolean;
    }
  | { type: "is_live"; negate?: boolean }
  | { type: "or"; filters: ServerAlertFilter[]; negate?: boolean };
/**
 * Create a display for alerts with user-customisable filtering
 */
export function alertNavigator<L, A extends Alert<L>>(
  allAlerts: A[],
  makeHeader: (a: A) => UIElement,
  filterState: StateSynchronizer<AlertFilter<RegExp>[]>,
  locationColumns: LocationColumns<L>,
  ...toolbarExtras: UIElement[]
): {
  main: UIElement;
  toolbar: UIElement;
} {
  const { list: filterList, register } = statefulListBind(filterState);
  const state = multipaneState<
    AlertFilter<RegExp>[],
    { alertlist: AlertFilterRenderer; filterbar: AlertFilterRenderer }
  >(null, {
    alertlist: (filters: AlertFilter<RegExp>[]) => {
      const selectedAlerts = applyFilters(allAlerts, filters);
      if (selectedAlerts.length == 0) {
        return "No matching alerts.";
      }
      const liveCount = selectedAlerts.filter((a) => a.live).length;
      let total: DisplayElement;
      if (liveCount == 0) {
        total = [
          { type: "icon", icon: "volume-mute" },
          `${selectedAlerts.length} expired alerts`,
        ];
      } else if (liveCount == selectedAlerts.length) {
        total = [
          { type: "icon", icon: "volume-up" },
          `${selectedAlerts.length} firing alerts`,
        ];
      } else {
        total = [
          `${selectedAlerts.length} alerts`,
          { type: "icon", icon: "volume-up" },
          `${liveCount} firing`,
          { type: "icon", icon: "volume-mute" },
          `${selectedAlerts.length - liveCount} expired`,
        ];
      }
      const numPerPage = 10;

      const { ui, model } = singleState(
        (current: number): UIElement => [
          pager(
            Math.ceil(selectedAlerts.length / numPerPage),
            current,
            model.statusChanged
          ),
          selectedAlerts
            .slice(current * numPerPage, (current + 1) * numPerPage)
            .map((a) => renderAlert(a, makeHeader, locationColumns)),
        ]
      );
      model.statusChanged(0);
      return [
        selectedAlerts.length == 1
          ? "Found 1 alert."
          : `Found ${selectedAlerts.length} alerts.`,
        br(),
        breakdown(selectedAlerts, filterList),
        total,
        br(),
        ui,
      ];
    },
    filterbar: (filters: AlertFilter<RegExp>[]) =>
      group(filters.map((f) => showFilter(f, filterList))),
  });
  register(state.model);

  return {
    toolbar: groupWithFind(
      () => {
        createLabelValueFilterDialog((label, value) =>
          filterList.add({
            type: "eq",
            value: value,
            label: label,
          })
        );
        return true;
      },

      buttonAccessory(
        [{ type: "icon", icon: "funnel" }, "Add Filter"],
        "Add a filter to limit the alerts displayed.",
        () =>
          dialog((close) => [
            { type: "icon", icon: "bell" },
            { type: "b", contents: "Activity" },
            br(),
            button(
              [{ type: "icon", icon: "volume-up" }, "Firing"],
              "Currently firing alerts.",
              () => {
                close();
                filterList.keepOnly((x) => x.type != "live");
                filterList.add({ type: "live", value: true, label: null });
              }
            ),
            button(
              [{ type: "icon", icon: "volume-mute" }, "Expired"],
              "Not currently firing alerts.",
              () => {
                close();
                filterList.keepOnly((x) => x.type != "live");
                filterList.add({ type: "live", value: false, label: null });
              }
            ),
            br(),
            { type: "icon", icon: "signpost" },
            { type: "b", contents: "Labels" },
            br(),
            createLabelFilter(
              [{ type: "icon", icon: "chat-left-fill" }, "Has Label"],
              "Find actions a labels.",
              close,
              (label) => {
                filterList.add({
                  type: "has",
                  label: label,
                  value: null,
                });
              }
            ),
            createLabelFilter(
              [
                { type: "icon", icon: "chat-left-dots-fill" },
                "Label Name Matches Regular Expression",
              ],
              "Find actions with label names that match a regular expression.",
              close,
              (label) =>
                filterList.add({
                  type: "has-regex",
                  label: new RegExp(label),
                  value: null,
                })
            ),
            br(),
            { type: "icon", icon: "collection-fill" },
            { type: "b", contents: "Values" },
            br(),
            createLabelValueFilter(
              [
                { type: "icon", icon: "chat-left-text-fill" },
                "Value Matches Text",
              ],
              "Find actions with labels that match a particular value.",
              close,
              (label, value) =>
                filterList.add({
                  type: "eq",
                  value: value,
                  label: label,
                })
            ),
            createLabelValueFilter(
              [
                { type: "icon", icon: "chat-left" },
                "Value Does Not Match Text",
              ],
              "Find actions with labels that do not match a particular value.",
              close,
              (label, value) =>
                filterList.add({
                  type: "ne",
                  value: value,
                  label: label,
                })
            ),
            createLabelValueFilter(
              [
                { type: "icon", icon: "chat-left-dots-fill" },
                "Value Matches Regular Expression",
              ],
              "Find actions with a label value that match a regular expression.",
              close,
              (label, value) =>
                filterList.add({
                  type: "regex",
                  value: new RegExp(value),
                  label: label,
                })
            ),
          ])
      ),
      toolbarExtras,
      state.components.filterbar
    ),
    main: state.components.alertlist,
  };
}
function applyFilters<L, A extends Alert<L>>(
  alerts: A[],
  filters: AlertFilter<RegExp>[]
): A[] {
  for (const userFilter of filters) {
    switch (userFilter.type) {
      case "live":
        alerts = alerts.filter((a) => a.live == userFilter.value);
        break;

      case "has":
        alerts = alerts.filter((a) =>
          a.labels.hasOwnProperty(userFilter.label)
        );
        break;
      case "has-regex":
        alerts = alerts.filter((a) =>
          Object.keys(a.labels).some((l) => userFilter.label.test(l))
        );
        break;
      case "eq":
        alerts = alerts.filter(
          (a) => a.labels[userFilter.label] == userFilter.value
        );
        break;
      case "ne":
        alerts = alerts.filter(
          (a) => a.labels[userFilter.label] != userFilter.value
        );
        break;

      case "regex":
        alerts = alerts.filter((a) =>
          userFilter.value.test(a.labels[userFilter.label])
        );
        break;
    }
  }
  return alerts;
}
/**
 * Create a snazzy breakdown of common elements among the alerts provided
 */
function breakdown(
  alerts: Alert<unknown>[],
  list: UpdateableList<AlertFilter<RegExp>>
): UIElement {
  const commonLabels = { ...alerts[0].labels };
  alerts.forEach((a) => {
    for (const [label, value] of Object.entries(a.labels)) {
      if (commonLabels[label] != value) {
        delete commonLabels[label];
      }
    }
  });
  const commonRows = Object.entries(commonLabels).map(([label, value]) =>
    tableRow(
      null,
      { contents: label },
      { contents: "Same for All" },
      { contents: value }
    )
  );

  const uselessLabels = Object.keys(commonLabels);
  if (alerts.length > 10) {
    const breakdown = new Map<
      string,
      { total: number; values: Map<string, number> }
    >();
    for (const a of alerts) {
      for (const [name, value] of Object.entries(a.labels)) {
        if (!uselessLabels.includes(name)) {
          if (!breakdown.has(name)) {
            breakdown.set(name, { total: 0, values: new Map() });
          }
          const counts = breakdown.get(name)!;
          counts.total++;
          counts.values.set(value, (counts.values.get(value) || 0) + 1);
        }
      }
    }

    const bestBreakdown = [...breakdown.entries()]
      .sort((a, b) => b[1].total - a[1].total)
      .filter(
        (x) =>
          x[1].total > 1 &&
          [...x[1].values.values()].some((c) => c > 1 && c > x[1].total * 0.1)
      );
    bestBreakdown.length = Math.min(bestBreakdown.length, 10);
    let { ui, model } = singleState(
      (
        breakdown: {
          label: string;
          total: number;
          values: Map<string, number>;
        } | null
      ): UIElement =>
        breakdown
          ? [
              table(
                [...breakdown.values],
                [breakdown.label, ([value]) => value || "<blank>"],
                [
                  "Value",
                  ([, count]) => percentAndCount(count, breakdown.total),
                ],
                [
                  "",
                  ([value]) => [
                    button(
                      { type: "icon", icon: "chat-left-text-fill" },
                      "Show alerts that match this value.",
                      () =>
                        list.add({
                          label: breakdown.label,
                          value: value,
                          type: "eq",
                        })
                    ),
                    button(
                      { type: "icon", icon: "chat-left-fill" },
                      "Hide alerts that match this value.",
                      () =>
                        list.add({
                          label: breakdown.label,
                          value: value,
                          type: "ne",
                        })
                    ),
                  ],
                ]
              ),
            ]
          : blank()
    );
    const selectors = radioSelector(
      "Show Values ▶",
      "Hide Values ◀",
      model,
      ...bestBreakdown.map(([label, { total, values }]) => ({
        label,
        total,
        values,
      }))
    );
    const breakdownRows = bestBreakdown.map(([label, { total }], index) =>
      tableRow(
        null,
        { contents: label },
        {
          contents:
            total == alerts.length
              ? "Always Present"
              : "Present in " + percentAndCount(total, alerts.length),
        },
        {
          contents: [
            button(
              [{ type: "icon", icon: "chat-left-fill" }, "Has Label"],
              "Show alerts that have this label with any value.",
              () =>
                list.add({
                  label: label,
                  value: null,
                  type: "has",
                })
            ),
            selectors[index],
          ],
        }
      )
    );
    return flexGroup(
      "row",
      {
        contents: tableFromRows(
          [
            [
              tableRow(
                null,
                { contents: "Label", header: true },
                { contents: "Type", header: true },
                { contents: "Value", header: true },
                { contents: blank(), header: true }
              ),
            ],
            commonRows,
            breakdownRows,
          ].flat()
        ),
        width: 1,
      },
      { contents: ui, width: 1 }
    );
  }
  return blank();
}

function createLabelFilter(
  name: DisplayElement,
  tooltip: string,
  close: () => void,
  add: (label: string) => void
): UIElement {
  return button(name, tooltip, () => {
    close();
    dialog((close) => {
      const label = inputText();
      return [
        "Label: ",
        label.ui,
        br(),
        button("Add", "Add alert filter.", () => {
          if (label.value.trim()) {
            close();
            add(label.value.trim());
          }
        }),
      ];
    });
  });
}

function createLabelValueFilter(
  name: DisplayElement,
  tooltip: string,
  close: () => void,
  add: (label: string, value: string) => void
): UIElement {
  return button(name, tooltip, () => {
    close();
    createLabelValueFilterDialog(add);
  });
}
function createLabelValueFilterDialog(
  add: (label: string, value: string) => void
) {
  dialog((close) => {
    const label = inputText();
    const value = inputText();
    return [
      "Label: ",
      label.ui,
      br(),
      "Value: ",
      value.ui,
      br(),
      button("Add", "Add alert filter.", () => {
        if (label.value.trim() && value.value.trim()) {
          close();
          add(label.value.trim(), value.value.trim());
        }
      }),
    ];
  });
}
export function initialiseAlertDashboard(
  initialFilters: AlertFilter<string>[],
  output: HTMLElement
) {
  if (location.hash) {
    fetchJsonWithBusyDialog(
      "getalert",
      location.hash.substring(1),
      (selectedAlert) =>
        setRootDashboard(
          output,
          selectedAlert
            ? renderAlert(
                selectedAlert,
                prometheusAlertHeader,
                prometheusAlertLocation((filename) => filename)
              )
            : "Unknown alert."
        )
    );
  } else {
    const filterState = synchronizerFields(
      historyState(
        "alerts",
        {
          filters: loadFilterRegex(initialFilters),
        },
        ({ filters }) => `Alerts with ${filters.length} filters`
      )
    );
    const alertState = sharedPane(
      "main",
      (alerts: PrometheusAlert[] | null) => {
        if (alerts?.length) {
          const fileNameFormatter = commonPathPrefix(
            alerts.flatMap((a) => a.locations || []).map((l) => l.file)
          );
          const { main, toolbar } = alertNavigator(
            alerts,
            prometheusAlertHeader,
            filterState.filters,
            prometheusAlertLocation(fileNameFormatter)
          );
          return { main: main, toolbar: toolbar };
        } else {
          return {
            main: "No alerts produced by this server.",
            toolbar: blank(),
          };
        }
      },
      "main",
      "toolbar"
    );
    const refresher = refreshable("allalerts", alertState.model, true);
    setRootDashboard(
      output,
      refreshButton(refresher.reload),
      alertState.components.toolbar,
      helpArea("alert"),
      br(),
      alertState.components.main
    );
    refresher.statusChanged(null);
  }
}

/**
 * Prepare all regular expressions in an alert filter for use
 */
export function loadFilterRegex(
  filters: AlertFilter<string>[]
): AlertFilter<RegExp>[] {
  return filters.map((filter) => {
    switch (filter.type) {
      case "has-regex":
        return {
          label: new RegExp(filter.label.substring(1, filter.label.length - 1)),
          value: null,
          type: filter.type,
        };
      case "regex":
        return {
          label: filter.label,
          value: new RegExp(filter.value.substring(1, filter.value.length - 1)),
          type: filter.type,
        };
      default:
        return filter;
    }
  });
}

/**
 * Create column for locations from real olives
 */
export function prometheusAlertLocation(
  fileNameFormatter: FilenameFormatter
): [string, (l: SourceLocation) => UIElement][] {
  return [
    ["File", (l: SourceLocation) => fileNameFormatter(l.file)],
    ["Line", (l: SourceLocation) => l.line?.toString() || "*"],
    ["Column", (l: SourceLocation) => l.column?.toString() || "*"],
    ["Source Hash", (l: SourceLocation) => l.hash || "*"],
    [
      "Olive",
      (l: SourceLocation) =>
        link(
          "olivedash?saved=" +
            encodeURIComponent(
              JSON.stringify({
                file: l.file,
                line: l.line,
                column: l.column,
                hash: l.hash,
              })
            ),
          "View in Dashboard"
        ),
    ],
    [
      "Source",
      (l: SourceLocation) => (l.url ? link(l.url, "View Source") : blank()),
    ],
  ];
}

function percentAndCount(count: number, total: number): string {
  return `${count} (${((count / total) * 100).toFixed(2)}%)`;
}
export function prometheusAlertHeader(a: PrometheusAlert): UIElement {
  return link(a.generatorURL, "Permalink");
}

function renderAlert<L, A extends Alert<L>>(
  a: A,
  makeHeader: (alert: A) => UIElement,
  locationColumns: LocationColumns<L>
): UIElement {
  return tile(
    ["alert", a.live ? "live" : "expired"],
    makeHeader(a),
    table(
      Object.entries(a.labels).sort((a, b) => a[0].localeCompare(b[0])),
      ["Label", (x) => x[0]],
      ["Value", (x) => x[1].split(/\n/).map((t) => text(t))]
    ),
    [
      { name: "Started", getter: (a: Alert<L>) => a.startsAt },
      { name: "Ended", getter: (a: Alert<L>) => a.endsAt },
    ].map(({ name, getter }) => {
      const time = getter(a);
      if (time) {
        const { ago, absolute } = computeDuration(new Date(time).getTime());
        return text(`${name} ${ago}`, absolute);
      } else {
        return blank();
      }
    }),
    objectTable(a.annotations, "Annotations", (x) => x),
    table(a.locations, ...locationColumns)
  );
}
function showFilter(
  userFilter: AlertFilter<RegExp>,
  list: UpdateableList<AlertFilter<RegExp>>
): UIElement {
  let name: DisplayElement;
  switch (userFilter.type) {
    case "live":
      name = userFilter.value
        ? [{ type: "icon", icon: "volume-up" }, "Firing"]
        : [{ type: "icon", icon: "volume-mute" }, "Expired"];
      break;
    case "has":
      name = [{ type: "icon", icon: "tag" }, userFilter.label];
      break;
    case "has-regex":
      name = [{ type: "icon", icon: "tag" }, ` ~ ${userFilter.label}`];
      break;
    case "eq":
      name = `${userFilter.label} = ${userFilter.value || "<blank>"}`;
      break;
    case "ne":
      name = `${userFilter.label} ≠ ${userFilter.value || "<blank>"}`;
      break;
    case "regex":
      name = `${userFilter.label} ~ ${userFilter.value}`;
      break;
    case "sourcelocation":
      return tile(
        ["button"],
        "Source Location",
        table(
          userFilter.locations,
          ["File", (l) => l.file],
          ["Line", (l) => l.line?.toString() || "*"],
          ["Column", (l) => l.column?.toString() || "*"],
          ["Hash", (l) => l.hash || "*"]
        ),
        buttonClose("Remove filter.", () =>
          list.keepOnly((x) => x != userFilter)
        )
      );
    default:
      name = "Unknown";
  }
  return tile(
    ["button"],
    name,
    buttonClose("Remove filter.", () =>
      list.keepOnly(
        (x) =>
          x.type != userFilter.type &&
          (x.type == "sourcelocation"
            ? true
            : x.label != userFilter.label && x.value != userFilter.value)
      )
    )
  );
}
// The filters contain regular expressions, so add a method to serialise them
Object.defineProperty(RegExp.prototype, "toJSON", {
  value: RegExp.prototype.toString,
});

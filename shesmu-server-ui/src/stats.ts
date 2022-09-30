import {
  ClickHandler,
  UIElement,
  blank,
  br,
  button,
  dialog,
  legend,
  makeUrl,
  popupMenu,
  singleState,
  tableFromRows,
  tableRow,
  tabs,
  temporaryState,
  text,
  dropdown,
} from "./html.js";
import {
  FilenameFormatter,
  StatefulModel,
  breakSlashes,
  combineModels,
  computeDuration,
  formatTimeSpan,
  splitModel,
  mergingModel,
} from "./util.js";
import { histogram } from "./histogram.js";
import {
  ActionFilter,
  PropertyType,
  TimeRangeType,
  filtersForPropertySearch,
  nameForBin,
  nameForProperty,
  updateBasicQueryForPropertySearch,
} from "./actionfilters.js";
import { fetchAsPromise, locallyStored, refreshable } from "./io.js";
import { Status, ExportSearchCommand, exportSearchDialog } from "./action.js";
import { helpHotspot } from "./help.js";

interface TableStatRow {
  /** The human-friendly name of the thing being recorded (_e.g._, Total)
   */
  title: string;
  /**
   * The number of actions that are counted
   */
  value: number;
  /**
   * Whether this row is based on actions that have some property
   */
  kind: "property" | null;
  /**
   * The internal name of this property.
   */
  type: PropertyType;
  /**
   * The human friendly value of this property.
   */
  property?: string;
  /**
   * The machine-readable value of this property
   */
  json?: any;
}
/**
 * A kind of statistic that a table of counts
 */
interface StatTable {
  type: "table";
  table: TableStatRow[];
}
/**
 * A cross-table of two different properties
 */
interface StatCrosstab {
  type: "crosstab";
  column: PropertyType;
  row: PropertyType;
  rows: { [name: string]: any };
  columns: { name: PropertyType; value: any }[];
  data: { [row in PropertyType]: { [column in PropertyType]: number } };
}
/**
 * A histogram of values
 */
interface StatHistogram {
  type: "histogram";
  boundaries: number[];
  counts: { [name in TimeRangeType]: number[] };
}
/**
 * A histogram of values broken down by property
 */
interface StatHistogramByProperty {
  type: "histogram-by-property";
  property: PropertyType;
  properties: any[];
  boundaries: number[];
  counts: { [name in TimeRangeType]: { counts: number[]; value: any }[] };
}
/**
 * Some raw text information
 */
interface StatText {
  type: "text";
  value: string;
}
/**
 * A statistic value computed by the server
 */
export type Stat =
  | StatCrosstab
  | StatHistogram
  | StatHistogramByProperty
  | StatTable
  | StatText;
/**
 * A callback to add a property limit to the existing search filter
 */
export type AddPropertySearch = (...limits: PropertySearch[]) => void;
/**
 * A callback to add a range search limit to the existing filter
 */
export type AddRangeSearch = (
  typeName: TimeRangeType,
  start: number,
  end: number,
  ...restrictions: PropertySearch[]
) => void;

export type PropertySearch =
  | { type: "status"; value: Status }
  | { type: "sourcefile"; value: string }
  | { type: "tag"; value: string }
  | { type: "type"; value: string };

const colours = [
  "#d09c2e",
  "#5b7fee",
  "#bacd4c",
  "#503290",
  "#8bc151",
  "#903691",
  "#46ca79",
  "#db64c3",
  "#63bb5b",
  "#af74db",
  "#9fa627",
  "#5a5dc0",
  "#72891f",
  "#578ae2",
  "#c96724",
  "#38b3eb",
  "#c34f32",
  "#34d3ca",
  "#be2e68",
  "#4ec88c",
  "#be438d",
  "#53d1a8",
  "#d54a4a",
  "#319466",
  "#d486d8",
  "#417c25",
  "#4b2f75",
  "#c3b857",
  "#3b5ba0",
  "#e09c4e",
  "#6d95db",
  "#9f741f",
  "#826bb9",
  "#78bb73",
  "#802964",
  "#a8bd69",
  "#b995e2",
  "#346e2e",
  "#d97eb8",
  "#6e6f24",
  "#e36f96",
  "#c29b59",
  "#862644",
  "#da8b57",
  "#d2506f",
  "#8d4e19",
  "#d34b5b",
  "#832520",
  "#d06c72",
  "#ce7058",
];
function renderStat(
  stat: Stat,
  addPropertySearch: AddPropertySearch,
  addRangeSearch: AddRangeSearch,
  filenameFormatter: FilenameFormatter,
  exportSearches: ExportSearchCommand[],
  baseFilters: ActionFilter[]
): UIElement {
  function popupForProperty(...limits: PropertySearch[]): ClickHandler {
    return popupMenu(
      false,
      {
        label: "Drill Down",
        action: () => addPropertySearch(...limits),
      },
      {
        label: "Drill Down in New Tab",
        action: () =>
          fetchAsPromise("printquery", {
            type: "and",
            filters: baseFilters.concat(filtersForPropertySearch(...limits)),
          }).then((result) => {
            window.open(
              makeUrl("actiondash", { filters: result, saved: "All Actions" })
            );
          }),
      },
      {
        label: "Open Search for Only This Selection",
        action: () => {
          const result = updateBasicQueryForPropertySearch(limits, {});
          if (result instanceof Promise) {
            result.then((query) =>
              window.open(
                makeUrl("actiondash", {
                  filters: query,
                  saved: "All Actions",
                })
              )
            );
          } else {
            window.open(
              makeUrl("actiondash", {
                filters: result,
                saved: "All Actions",
              })
            );
          }
        },
      },
      {
        label: "Export Search for Drill Down",
        action: () =>
          exportSearchDialog(
            exportSearches,
            baseFilters.concat(filtersForPropertySearch(...limits))
          ),
      },
      {
        label: "Export Search for Only This Condition",
        action: () =>
          exportSearchDialog(
            exportSearches,
            filtersForPropertySearch(...limits)
          ),
      }
    );
  }
  switch (stat.type) {
    case "text":
      return [stat.value, br()];
    case "table":
      return [
        tableFromRows(
          stat.table.map((row: TableStatRow) => {
            let prettyTitle: string;
            let click: ClickHandler | null = null;
            if (row.kind == null) {
              prettyTitle = row.title;
            } else if (row.kind == "property") {
              prettyTitle = `${row.title} ${row.property}`;
              click = popupForProperty({ type: row.type, value: row.json });
            } else {
              prettyTitle = `Unknown entry for ${row.kind}`;
            }
            return tableRow(
              click,
              { contents: prettyTitle },
              { contents: row.value.toString() }
            );
          })
        ),
        helpHotspot("stats-table"),
        br(),
      ];
    case "crosstab": {
      const rows = [];

      rows.push(
        tableRow(
          null,
          ...[{ contents: blank(), header: true }].concat(
            stat.columns.map((c) => ({
              contents: breakSlashes(c.name),
              header: true,
              cell: c.name,
              click: popupForProperty({ type: stat.column, value: c.value }),
            }))
          )
        )
      );
      const maximum = Math.max(
        1,
        Math.max(
          ...Object.values(stat.data).map((row) =>
            Math.max(...Object.values(row))
          )
        )
      );

      for (let rowKey of Object.keys(stat.data).sort() as PropertyType[]) {
        const rowValue = stat.rows[rowKey];
        rows.push(
          tableRow(
            null,
            ...[
              {
                contents: breakSlashes(rowKey) as UIElement,
                header: true,
                click: popupForProperty({ type: stat.row, value: rowValue }),
              },
            ].concat(
              stat.columns.map((col) => {
                // The matrix might be ragged if doing a tag-tag crosstab
                const value =
                  stat.data.hasOwnProperty(rowKey) &&
                  stat.data[rowKey].hasOwnProperty(col.name)
                    ? stat.data[rowKey][col.name]
                    : 0;
                return {
                  contents: value
                    ? (stat.data[rowKey][col.name].toString() as UIElement)
                    : blank(),
                  header: false,
                  intensity: (value || 0) / maximum,
                  click: popupForProperty(
                    { type: stat.column, value: col.value },
                    { type: stat.row, value: rowValue }
                  ),
                };
              })
            )
          )
        );
      }
      return [tableFromRows(rows), helpHotspot("stats-crosstab"), br()];
    }

    case "histogram": {
      const boundaryLabels = stat.boundaries.map((x) => computeDuration(x));
      const boundaries = stat.boundaries;
      const ui = histogram(
        Math.PI / 4,
        boundaryLabels.map((l) => l.ago),
        Object.entries(stat.counts).map(([bin, counts]) => ({
          label: " " + nameForBin(bin as TimeRangeType),
          counts: counts,
          selected(start: number, end: number): void {
            addRangeSearch(
              bin as TimeRangeType,
              boundaries[start],
              Math.max(boundaries[end], boundaries[start] + 60_000) // Time is rounded to minutes for some situations, so if the window is too small, the start and end times will be the same and nothing matches; this kicks the end range a bit into the future
            );
          },
          selectionDisplay(start: number, end: number): string {
            const sum = counts.reduce(
              (acc, value, index) =>
                index >= start && index < end ? acc + value : acc,
              0
            );
            return (
              sum +
              " actions over " +
              formatTimeSpan(boundaries[end] - boundaries[start]) +
              " (" +
              boundaryLabels[start].ago +
              " / " +
              boundaryLabels[start].absolute +
              " to " +
              boundaryLabels[end].ago +
              " / " +
              boundaryLabels[end].absolute +
              ")"
            );
          },
        }))
      );
      return [ui, helpHotspot("stats-histogram"), br()];
    }
    case "histogram-by-property": {
      const boundaryLabels = stat.boundaries.map((x) => computeDuration(x));
      const boundaries = stat.boundaries;
      const { ui: histoUi, model: histoModel } = singleState(
        (selectedProperties: { value: any; colour: string }[]) =>
          selectedProperties.length == 0
            ? "Please select items from the legend to view."
            : histogram(
                Math.PI / 4,
                boundaryLabels.map((l) => l.ago),
                Object.entries(stat.counts).flatMap(([bin, propertyCounts]) =>
                  selectedProperties.flatMap(({ value, colour }) =>
                    propertyCounts
                      .filter((c) => c.value == value)
                      .map((c) => ({
                        label: " " + nameForBin(bin as TimeRangeType),
                        colour,
                        counts: c.counts,
                        selected(start: number, end: number): void {
                          addRangeSearch(
                            bin as TimeRangeType,
                            boundaries[start],
                            Math.max(
                              boundaries[end],
                              boundaries[start] + 60_000
                            ), // Time is rounded to minutes for some situations, so if the window is too small, the start and end times will be the same and nothing matches; this kicks the end range a bit into the future
                            {
                              type: stat.property,
                              value,
                            }
                          );
                        },
                        selectionDisplay(start: number, end: number): string {
                          const sum = c.counts.reduce(
                            (acc, value, index) =>
                              index >= start && index < end ? acc + value : acc,
                            0
                          );
                          return (
                            sum +
                            " actions over " +
                            formatTimeSpan(
                              boundaries[end] - boundaries[start]
                            ) +
                            " (" +
                            boundaryLabels[start].ago +
                            " / " +
                            boundaryLabels[start].absolute +
                            " to " +
                            boundaryLabels[end].ago +
                            " / " +
                            boundaryLabels[end].absolute +
                            ")"
                          );
                        },
                      }))
                  )
                )
              )
      );
      return [
        { type: "b", contents: ["By ", nameForProperty(stat.property)] },
        " ",
        helpHotspot("stats-histogram-by-property"),
        br(),
        legend(
          histoModel,
          stat.property == "sourcefile"
            ? (property) => filenameFormatter(`${property}`)
            : (property) => breakSlashes(`${property}`),
          stat.properties,
          "#999",
          colours
        ),

        br(),
        histoUi,
        br(),
      ];
    }

    default:
      return "Unknown stat type";
  }
}

/**
 * Produce a stats pane that can be fed filters to rebuild itself
 */
export function actionStats(
  addPropertySeach: AddPropertySearch,
  addRangeSearch: AddRangeSearch,
  filenameFormatter: FilenameFormatter,
  exportSearches: ExportSearchCommand[]
): { ui: UIElement; toolbar: UIElement; model: StatefulModel<ActionFilter[]> } {
  const filters = temporaryState<ActionFilter[]>([]);
  const { model, ui } = singleState((stats: Stat[] | null): UIElement => {
    if (stats?.length) {
      const results = stats.map((stat) =>
        renderStat(
          stat,
          addPropertySeach,
          addRangeSearch,
          filenameFormatter,
          exportSearches,
          filters.get()
        )
      );
      return [
        text("Click any cell or table heading to filter results."),
        results,
      ];
    }
    return "No statistics are available.";
  });
  const waitForStats = locallyStored<boolean>(
    "shesmu_wait_for_slow_stats",
    false
  );
  const [io, wait] = mergingModel(
    refreshable("stats", model, false),
    (filters: ActionFilter[] | null, wait: boolean | null) => ({
      filters: filters || [],
      wait: wait || false,
    }),
    true
  );
  return {
    ui: ui,
    toolbar: dropdown(
      (wait, selected) => {
        if (wait) {
          return [
            { type: "icon", icon: "hourglass-split" },
            selected ? blank() : "Wait for Slow Statistics",
          ];
        } else {
          return [
            { type: "icon", icon: "hourglass" },
            selected ? blank() : "Skip Slow Statistics",
          ];
        }
      },
      (wait) => wait == waitForStats.get(),
      wait,
      {
        synchronizer: waitForStats,
        predicate: (recovered, item) => recovered == item,
        extract: (x) => x,
      },
      false,
      true
    ),

    model: combineModels(filters, io),
  };
}

import {
  UIElement,
  text,
  tableFromRows,
  tableRow,
  blank,
  singleState,
  popup,
  ClickHandler,
  dialog,
  button,
  makeUrl,
} from "./html.js";
import {
  StatefulModel,
  breakSlashes,
  formatTimeSpan,
  computeDuration,
} from "./util.js";
import { histogram } from "./histogram.js";
import {
  nameForBin,
  ActionFilter,
  PropertyType,
  TimeRangeType,
  filtersForPropertySearch,
  BasicQuery,
  updateBasicQueryForPropertySearch,
} from "./actionfilters.js";
import { refreshable } from "./io.js";
import { Status, ExportSearchCommand } from "./action.js";
import { helpHotspot, helpArea } from "./help.js";

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
 * Some raw text information
 */
interface StatText {
  type: "text";
  value: string;
}
/**
 * A statistic value computed by the server
 */
type Stat = StatCrosstab | StatHistogram | StatTable | StatText;
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
  end: number
) => void;

export type PropertySearch =
  | { type: "status"; value: Status }
  | { type: "sourcefile"; value: string }
  | { type: "tag"; value: string }
  | { type: "type"; value: string };

function renderStat(
  stat: Stat,
  addPropertySearch: AddPropertySearch,
  addRangeSearch: AddRangeSearch,
  exportSearches: ExportSearchCommand[]
): { ui: UIElement; reveal: () => void } {
  function popupForProperty(...limits: PropertySearch[]): ClickHandler {
    return popup(
      false,
      {
        label: "Drill Down",
        action: () => addPropertySearch(...limits),
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
        label: "Export Search for Only This Condition",
        action: () =>
          dialog((close) =>
            exportSearches.map(([label, title, command]) =>
              button(label, title, () => {
                close();
                command(filtersForPropertySearch(...limits));
              })
            )
          ),
      }
    );
  }
  switch (stat.type) {
    case "text":
      return { ui: stat.value, reveal: () => {} };
    case "table":
      return {
        ui: [
          tableFromRows(
            stat.table.map(
              (row: TableStatRow): HTMLTableRowElement => {
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
              }
            )
          ),
          helpHotspot("stats-table"),
        ],
        reveal: () => {},
      };
    case "crosstab": {
      const rows: HTMLTableRowElement[] = [];

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
      return {
        ui: [tableFromRows(rows), helpHotspot("stats-crosstab")],
        reveal: () => {},
      };
    }

    case "histogram": {
      const boundaryLabels = stat.boundaries.map((x) => computeDuration(x));
      const boundaries = stat.boundaries;
      const { ui, redraw } = histogram(
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
              +" / " +
              boundaryLabels[start].absolute +
              " to " +
              boundaryLabels[end].ago +
              +" / " +
              boundaryLabels[end].absolute +
              ")"
            );
          },
        }))
      );
      return { ui: [ui, helpHotspot("stats-histogram")], reveal: redraw };
    }

    default:
      return { ui: "Unknown stat type", reveal: () => {} };
  }
}

/**
 * Produce a stats pane that can be fed filters to rebuild itself
 */
export function actionStats(
  addPropertySeach: AddPropertySearch,
  addRangeSearch: AddRangeSearch,
  exportSearches: ExportSearchCommand[]
): { ui: UIElement; model: StatefulModel<ActionFilter[]>; reveal: () => void } {
  let revealers: (() => void)[] = [];
  const { model, ui } = singleState(
    (stats: Stat[] | null): UIElement => {
      if (stats?.length) {
        const results = stats.map((stat) =>
          renderStat(stat, addPropertySeach, addRangeSearch, exportSearches)
        );
        revealers = results.map((r) => r.reveal);
        return [
          text("Click any cell or table heading to filter results."),
          results.map((r) => r.ui),
        ];
      }
      return "No statistics are available.";
    }
  );
  const io = refreshable(
    "stats",
    (filters: ActionFilter[]) => ({
      body: JSON.stringify(filters),
      method: "POST",
    }),
    model,
    false
  );
  return {
    ui: ui,
    model: io,
    reveal: () => {
      for (const reveal of revealers) reveal();
    },
  };
}

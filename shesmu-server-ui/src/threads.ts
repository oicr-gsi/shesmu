import { histogram, HistogramRow } from "./histogram.js";
import {
  br,
  button,
  collapsible,
  intervalCounter,
  mono,
  setRootDashboard,
  sharedPane,
  singleState,
  table,
  tabs,
  tile,
  UIElement,
} from "./html.js";
import { locallyStored, refreshable, saveFile } from "./io.js";
import {
  bufferingModel,
  computeDuration,
  formatTimeSpan,
  mapModel,
} from "./util.js";

export interface ThreadInfo {
  state:
    | "NEW"
    | "RUNNABLE"
    | "BLOCKED"
    | "WAITING"
    | "TIMED_WAITING"
    | "TERMINATED";
  blockedCount: number;
  blockedTime: number;
  waitCount: number;
  waitTime: number;
  priority: number;
  cpuTime: number;
  trace: string[];
}
export interface ThreadResponse {
  threads: { [name: string]: ThreadInfo };
  time: number;
}
function formatDelta(delta: number): string {
  if (delta == 0) {
    return "0";
  }
  return (delta < 0 ? "-" : "+") + formatTimeSpan(delta);
}
export function initialiseThreadDash() {
  const threadDisplay = sharedPane<
    (ThreadResponse | null)[],
    { main: UIElement; toolbar: UIElement }
  >(
    "main",
    (states) => {
      const sortedStates = states
        .filter((s: ThreadResponse | null): s is ThreadResponse => s != null)
        .sort((a, b) => b.time - a.time);
      if (sortedStates.length == 0) {
        return { main: "No information to display.", toolbar: [] };
      }
      const threadInfo: {
        contents: UIElement;
        name: string;
        priority: number;
      }[] = [];
      for (const name of new Set(
        sortedStates.flatMap((s) => Object.keys(s.threads))
      )) {
        const info = sortedStates.filter((s) => s.threads.hasOwnProperty(name));
        const priority =
          info.length > 1
            ? info[0].threads[name].cpuTime - info[1].threads[name].cpuTime
            : 0;
        const deltas: {
          delta: number;
          cpuTime: number;
          blockedTime: number;
          blockedCount: number;
          waitCount: number;
          waitTime: number;
        }[] = [];
        for (let i = 1; i < info.length; i++) {
          deltas.push({
            delta: info[i - 1].time - info[i].time,
            cpuTime:
              info[i - 1].threads[name].cpuTime - info[i].threads[name].cpuTime,
            blockedTime:
              info[i - 1].threads[name].blockedTime -
              info[i].threads[name].blockedTime,
            blockedCount:
              info[i - 1].threads[name].blockedCount -
              info[i].threads[name].blockedCount,
            waitCount:
              info[i - 1].threads[name].waitCount -
              info[i].threads[name].waitCount,
            waitTime:
              info[i - 1].threads[name].waitTime -
              info[i].threads[name].waitTime,
          });
        }

        threadInfo.push({
          contents: [
            { type: "b", contents: "Raw Data" },
            table(
              info,
              ["Clock Time", (i) => new Date(i.time).toString()],
              ["State", (i) => i.threads[name].state],
              [
                "CPU Time",
                (i) => formatTimeSpan(i.threads[name].cpuTime / 1e6),
              ],
              ["Blocked Count", (i) => i.threads[name].blockedCount],
              [
                "Blocked Time",
                (i) => formatTimeSpan(i.threads[name].blockedTime),
              ],
              ["Wait Count", (i) => i.threads[name].waitCount],
              ["Wait Time", (i) => formatTimeSpan(i.threads[name].waitTime)]
            ),
            { type: "b", contents: "Changes" },
            table(
              deltas,
              ["Clock Time", (i) => formatDelta(i.delta)],
              ["Blocked Count", (i) => i.blockedCount],
              ["Blocked Time", (i) => formatDelta(i.blockedTime)],
              ["Wait Count", (i) => i.waitCount],
              ["Wait Time", (i) => formatDelta(i.waitTime)]
            ),
            info.map((info) =>
              collapsible(
                `Trace at ${new Date(info.time)}`,
                info.threads[name].trace.map((t) => [mono(t), br()])
              )
            ),
          ],
          name,
          priority,
        });
      }

      threadInfo.sort((a, b) => b.priority - a.priority);

      const histogramRows: HistogramRow[] = [];
      const processes = new Set(
        sortedStates
          .flatMap((s) => Object.values(s.threads))
          .flatMap((t) => t.trace)
      );
      const processCounts: Map<string, number>[] = [];
      for (let i = 1; i < sortedStates.length; i++) {
        const timeslice = sortedStates[i - 1].time - sortedStates[i].time;
        const processValues = new Map<string, number>(
          Array.from(processes).map((p) => [p, 0])
        );
        for (const [threadName, thread] of Object.entries(
          sortedStates[i].threads
        )) {
          const threadTime =
            ((sortedStates[i - 1].threads[threadName]?.cpuTime || 0) -
              thread.cpuTime) /
            timeslice;
          for (const trace of thread.trace) {
            processValues.set(
              trace,
              (processValues.get(trace) || 0) + threadTime
            );
          }
        }
        processCounts.push(processValues);
      }
      const boundaryLabels = sortedStates.map((i) => computeDuration(i.time));
      for (const process of processes) {
        const counts = processCounts.map((c) => c.get(process) || 0);
        histogramRows.push({
          counts,
          label: process,
          selected: (_start, _end) => {},
          selectionDisplay: (start, end) => {
            const sum = counts.reduce(
              (acc, value, index) =>
                index >= start && index < end ? acc + value : acc,
              0
            );
            return (
              sum.toFixed(2) +
              " CPU megaseconds per second (" +
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
        });
      }

      return {
        toolbar: button(
          [{ type: "icon", icon: "download" }, "Download"],
          "Download the captured data as JSON.",
          () =>
            saveFile(
              JSON.stringify(sortedStates, null, 2),
              "application/json",
              `thread-info-${new Date(
                sortedStates[0].time
              ).toISOString()}-${new Date(
                sortedStates[sortedStates.length - 1].time
              ).toISOString()}.json`
            )
        ),
        main: [
          sortedStates.length > 2
            ? [
                histogram(
                  45,
                  boundaryLabels.map((l) => l.ago),
                  histogramRows
                ),
                "This histogram represents approximate time used by various classes and olives running in the Shesmu server. The time units are total CPU megaseconds per wall clock second. Since the sampling interval is large, time may be incorrectly attached to different actors. Also, parent activities include the time of their children; an olive that calls a plugin will have the time used by the plugin associated with both the olive and the plugin.",
              ]
            : [],
          tabs(...threadInfo),
        ],
      };
    },
    "main",
    "toolbar"
  );
  setRootDashboard(
    "threaddash",
    tile(
      [],
      intervalCounter(
        60_000, // 1 minute
        mapModel(
          refreshable(
            "threads",
            bufferingModel(threadDisplay.model, 60),
            false
          ),
          (_) => null
        ),
        {
          label: [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
          title: "Reload any thread information from the server.",
          synchronizer: locallyStored<boolean>("shesmu_update_threads", true),
        }
      ),
      threadDisplay.components.toolbar
    ),
    threadDisplay.components.main
  );
}

import { UIElement } from "./html.js";

/**
 * A single row in a histogram display
 *
 * It must use the common bucket labels for the chart.
 */
export interface HistogramRow {
  /**
   * The colour for the blocks in the row and the text
   */
  readonly colour?: string;
  /**
   * The number of items in each bucket of the histogram
   */
  readonly counts: number[];
  /**
   * The name of the row.
   */
  readonly label: string;
  /**
   * Create a message to show what is being selected for the range provided.
   *
   * It is guaranteed that start will be less than end.
   */
  selectionDisplay(start: number, end: number): string;
  /**
   * A callback indicating that the user has selected this range of the histogram.
   *
   * It is guaranteed that start will be less than end.
   */
  selected(start: number, end: number): void;
}
type HistogramSelection = { row: number; index: number } | null;
/**
 * Draw a histogram with multiple rows that share a common set of data ranges
 *
 * @param headerAngle the angle to rotate the bucket labels, in radians
 * @param labels the labels for each bucket; this must be one longer than the number of items in each row to include the title of the last bucket
 * @param rows the histogram rows to display
 */
export function histogram(
  headerAngle: number,
  labels: string[],
  rows: HistogramRow[]
): UIElement {
  const div = document.createElement("div");
  div.className = "histogram";
  let selectionStart: HistogramSelection = null;
  div.style.width = "90%";
  const canvas = document.createElement("canvas");
  const ctxt = canvas.getContext("2d")!;
  const rowHeight = 40;
  const fontHeight = 10; // We should be able to compute this from the font metrics, but they don't provide it, so uhh...10pts.
  const columnLabelHeight =
    Math.sin(headerAngle) *
      Math.max(...labels.map((l) => ctxt.measureText(l).width)) +
    2 * fontHeight;
  canvas.height = rows.length * rowHeight + columnLabelHeight;
  div.appendChild(canvas);
  const current = document.createElement("span");
  current.innerText = "\u00A0";
  div.appendChild(current);
  const redraw = () => {
    const cs = getComputedStyle(div);
    const width = parseInt(cs.getPropertyValue("width"), 10);
    canvas.width = width;

    const labelWidth = Math.max(
      ...rows.map((r) => ctxt.measureText(" " + r.label).width)
    );
    const columnWidth = (width - labelWidth) / (labels.length - 1);
    const columnSkip = Math.ceil(
      (2 * fontHeight * Math.cos(headerAngle)) / columnWidth
    );
    if (width < labelWidth) {
      // The browser window is just too small, so give up;
      ctxt.clearRect(0, 0, width, canvas.height);
      ctxt.fillText("Cannot show histogram.", fontHeight, 0);
      return;
    }

    const repaint = (selectionEnd: HistogramSelection) => {
      ctxt.clearRect(0, 0, width, canvas.height);
      ctxt.fillStyle = "#000";
      labels.forEach((label, index) => {
        if (index % columnSkip == 0) {
          // We can only apply rotation about the origin, so move the origin to the point where we want to draw the text, rotate it, draw the text at the origin, then reset the coordinate system.
          ctxt.translate(index * columnWidth, columnLabelHeight);
          ctxt.rotate(-headerAngle);
          ctxt.fillText(label, fontHeight * Math.tan(headerAngle), 0);
          ctxt.setTransform(1, 0, 0, 1, 0, 0);
        }
      });
      rows.forEach((row, rowIndex) => {
        if (row.counts.length != labels.length - 1) {
          throw new Error(
            `Row ${rowIndex} has ${row.counts.length} but expected ${
              labels.length - 1
            }`
          );
        }
        const logMax = Math.log(Math.max(...row.counts));
        for (let countIndex = 0; countIndex < row.counts.length; countIndex++) {
          if (
            selectionStart &&
            selectionEnd &&
            selectionStart.row == rowIndex &&
            selectionEnd.row == rowIndex &&
            countIndex >= Math.min(selectionStart.index, selectionEnd.index) &&
            countIndex <= Math.max(selectionStart.index, selectionEnd.index)
          ) {
            ctxt.fillStyle = "#E0493B";
          } else {
            ctxt.fillStyle = rows[rowIndex].colour || "#06AED5";
          }
          ctxt.globalAlpha = Math.log(row.counts[countIndex] + 1) / logMax;
          ctxt.fillRect(
            countIndex * columnWidth + 1,
            rowIndex * rowHeight + 2 + columnLabelHeight,
            columnWidth - 2,
            rowHeight - 4
          );
        }
        ctxt.fillStyle = "#000";
        ctxt.globalAlpha = 1;
        ctxt.fillText(
          " " + row.label,
          width - labelWidth,
          rowIndex * rowHeight +
            (rowHeight + fontHeight) / 2 +
            columnLabelHeight
        );
      });
    };
    repaint(null);
    const findSelection = (e: MouseEvent): HistogramSelection => {
      if (e.button != 0) return null;
      const bounds = canvas.getBoundingClientRect();
      const x = e.clientX - bounds.left;
      const y = e.clientY - bounds.top - columnLabelHeight;
      if (y > 0 && x > 0 && x < width - labelWidth) {
        return {
          row: Math.max(0, Math.floor(y / rowHeight)),
          index: Math.max(
            0,
            Math.floor((x / (width - labelWidth)) * (labels.length - 1))
          ),
        };
      }
      return null;
    };
    canvas.addEventListener("mousedown", (e) => {
      selectionStart = findSelection(e);
      if (selectionStart) {
        current.innerText = rows[selectionStart.row].selectionDisplay(
          selectionStart.index,
          selectionStart.index + 1
        );
        repaint(selectionStart);
      } else {
        current.innerText = "\u00A0";
        current.title = "";
      }
    });
    const mouseWhileDown = (e: MouseEvent, finished: boolean) => {
      const selectionEnd = findSelection(e);
      repaint(selectionEnd);
      if (
        selectionStart &&
        selectionEnd &&
        selectionStart.row == selectionEnd.row
      ) {
        const startBound = Math.min(selectionStart.index, selectionEnd.index);
        const endBound = Math.max(selectionStart.index, selectionEnd.index) + 1;
        const row = rows[selectionEnd.row];
        current.innerText = row.selectionDisplay(startBound, endBound);
        if (finished) {
          row.selected(startBound, endBound);
        }
      } else {
        current.innerText = "\u00A0";
        current.title = "";
      }
    };
    canvas.addEventListener("mouseup", (e) => {
      mouseWhileDown(e, true);
      selectionStart = null;
    });
    canvas.addEventListener("mousemove", (e) => {
      if (selectionStart) {
        mouseWhileDown(e, false);
      }
    });
  };
  // There's eventually going to be a new API for this, but for now, we plugig into the window's resize event so we can redo the layout when it changes. We also plugin into a DOM render so we can remove our listener when the histogram is refreshed.
  let timeout = window.setTimeout(redraw, 100);
  const resizeCallback = () => {
    clearTimeout(timeout);
    window.setTimeout(redraw, 100);
  };
  window.addEventListener("resize", resizeCallback);
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (let i = 0; i < mutation.removedNodes.length; i++) {
        if (mutation.removedNodes.item(i) == div) {
          observer.disconnect();
          window.removeEventListener("resize", resizeCallback);
        }
      }
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });
  return { element: div, find: null, reveal: redraw, type: "ui" };
}

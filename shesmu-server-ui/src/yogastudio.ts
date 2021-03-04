import { loadFile, refreshable, saveClipboard, saveFile } from "./io.js";
import {
  Tab,
  br,
  button,
  buttonAccessory,
  checkKey,
  group,
  preformatted,
  setRootDashboard,
  singleState,
  table,
  tabsModel,
} from "./html.js";
import {
  FilenameFormatter,
  combineModels,
  commonPathPrefix,
  mapModel,
} from "./util.js";
import {
  WizardStep,
  renderInformation,
  renderWizard,
} from "./guided_meditations.js";
import * as runtime from "./runtime.js";
import {
  compressToEncodedURIComponent,
  decompressFromEncodedURIComponent,
} from "./lz-string.js";
import { themeSelector } from "./simulation.js";
import { ExportSearchCommand } from "./action.js";

export interface MeditationCompilationResponse {
  errors: string[];
  functionBody?: string;
}

export function renderResponse(
  response: MeditationCompilationResponse | null,
  filenameFormatter: FilenameFormatter,
  exportSearches: ExportSearchCommand[]
): Tab[] {
  if (response?.functionBody) {
    const wizard = new Function("$runtime", response?.functionBody)(
      runtime
    ) as WizardStep;
    try {
      const { information, then } = wizard();
      return [
        {
          name: "Meditation",
          contents: [
            information
              .flat(Number.MAX_VALUE)
              .map((i) =>
                renderInformation(i, filenameFormatter, exportSearches)
              ),
            then == null
              ? "Well, that was fast."
              : renderWizard(then, filenameFormatter, exportSearches),
          ],
        },
        {
          name: "JavaScript Code",
          contents: preformatted(response?.functionBody),
        },
      ];
    } catch (e) {
      return [
        {
          name: "Implementation Error",
          contents: [e.toString(), br(), preformatted(response?.functionBody)],
        },
      ];
    }
  } else {
    return [];
  }
}

export function initialiseYogaStudio(
  ace: AceAjax.Ace,
  container: HTMLElement,
  scriptName: string | null,
  scriptBody: string | null,
  decodeBody: boolean,
  fileNames: string[],
  exportSearches: ExportSearchCommand[]
) {
  const filenameFormatter = commonPathPrefix(fileNames);
  let fileName = scriptName || "unknown.medtiation";
  const script = document.createElement("DIV");
  script.className = "editor";
  const editor = ace.edit(script);
  editor.session.setMode("ace/mode/shesmu");
  editor.session.setOption("useWorker", false);
  editor.session.setTabSize(2);
  editor.session.setUseSoftTabs(true);
  editor.setFontSize("14pt");
  editor.setValue(
    (decodeBody && scriptBody
      ? decompressFromEncodedURIComponent(scriptBody)
      : scriptBody) ||
      localStorage.getItem("shesmu_meditation") ||
      "",
    0
  );
  const errorTable = singleState(
    (response: MeditationCompilationResponse | null) => {
      const annotations: AceAjax.Annotation[] = [];
      const ui = table(
        (response?.errors || []).map((err) => {
          const match = err.match(/^(\d+):(\d+): *(.*$)/);
          if (match) {
            const line = parseInt(match[1]);
            const column = parseInt(match[2]);
            const errorText = match[3];
            annotations.push({
              row: line - 1,
              column: column - 1,
              text: errorText,
              type: "error",
            });
            return { line: line, column: column, message: errorText };
          } else {
            return { line: null, column: null, message: err };
          }
        }),
        ["Line", (e) => e.line?.toString() || ""],
        ["Column", (e) => e.column?.toString() || ""],
        ["Error", (e) => e.message]
      );
      editor.getSession().setAnnotations(annotations);
      return ui;
    }
  );

  const tabbedArea = tabsModel(1, {
    name: "Script",
    contents: group(
      { element: script, find: null, reveal: null, type: "ui" },
      errorTable.ui
    ),
  });
  const dashboardState = mapModel(
    tabbedArea.models[0],
    (response: MeditationCompilationResponse | null) => {
      return {
        tabs: renderResponse(response, filenameFormatter, exportSearches),
        activate: response?.errors.length === 0,
      };
    }
  );
  const main = refreshable(
    "compile-meditation",
    combineModels(dashboardState, errorTable.model),
    true
  );
  const simulationModel = mapModel(main, (request: string) => {
    editor.getSession().clearAnnotations();
    return {
      script: request,
    };
  });

  setRootDashboard(
    container,
    group(
      button(
        [{ type: "icon", icon: "signpost-fill" }, "Try Meditation"],
        "Compile meditation and start it",
        () => simulationModel.statusChanged(editor.getValue())
      ),
      buttonAccessory(
        [{ type: "icon", icon: "file-earmark-arrow-up" }, "Upload File"],
        "Upload a file from your computer to edit",
        () =>
          loadFile((name, data) => {
            fileName = name;
            editor.setValue(data, 0);
          })
      ),
      buttonAccessory(
        [{ type: "icon", icon: "file-earmark-arrow-down" }, "Download File"],
        "Save script in editor to your computer",
        () => saveFile(editor.getValue(), "text/plain", fileName)
      ),
      buttonAccessory(
        [{ type: "icon", icon: "share" }, "Share"],
        "Copy this script as a link to the clipboard",
        () =>
          saveClipboard(
            `${window.location.origin}${
              window.location.pathname
            }?share=${compressToEncodedURIComponent(editor.getValue())}`
          )
      ),
      themeSelector(editor)
    ),
    br(),
    tabbedArea.ui
  );
  document.addEventListener(
    "keydown",
    (e) => {
      // Map Ctrl-S or Command-S to download/save
      if (checkKey(e, "s")) {
        e.preventDefault();
        saveFile(editor.getValue(), "text/plain", fileName);
      }
    },
    false
  );
  editor.getSession().on("change", () => {
    localStorage.setItem("shesmu_meditation", editor.getValue());
  });
  main.force(null);
}

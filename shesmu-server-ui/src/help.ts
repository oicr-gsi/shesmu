import {
  UIElement,
  buttonIcon,
  subscribedState,
  addElements,
  br,
  blank,
  hr,
} from "./html.js";
import { locallyStored } from "./io.js";
import { Publisher, pubSubModel } from "./util.js";
export type Area = "action" | "alert" | "olive" | "pauses" | "simulator";
export type HotSpot = "stats-crosstab" | "stats-table" | "stats-histogram";
/**
 * Describes an individual help message that should appear.
 */
interface HelpTip {
  /**
   * A short (one line) description of the feature
   */
  summary: string;
  /**
   * A long description of the feature
   */
  description: string;
  /**
   * The hotspot (_i.e._, UI location) where this help tip should be displayed.
   */
  hotspot: HotSpot | null;
  /**
   * A general category for the kind of information in this tip so that everything in a category can be marked as read at once.
   */
  area: Area;
}
/**
 * All the help for the whole UI. Each outer array represents a release version of Shesmu.
 *
 * Entries in the arrays can be replaced by null to hide this tip if the user upgrades past this version.
 * This only makes sense after the fact. Suppose version 2 has a tip and then version 3 replaces that feature; replacing that tip with null will make it hidden from a user going directly from 1 to 3. It must not be deleted outright, since that will shuffle in indicies and that will display the wrong thing to the user.
 */
const tips: (HelpTip | null)[][] = [
  [
    {
      summary: "Use advanced search for tag×tag filtering",
      description:
        "When using the basic search, multiple tags are treated as “any” matches, so selecting another tag will broaden rather than narrow the search. Switch to the advanced search to allow narrowing when searching tags.",
      hotspot: "stats-crosstab",
      area: "action",
    },
    {
      summary: "Click table rows to filter",
      description: "Click on table rows to drill down into those actions.",
      hotspot: "stats-table",
      area: "action",
    },
    {
      summary: "Click and drag histogram to filter",
      description:
        "Clicking and dragging on a range will add a search filter to display only matching the actions in that range.",
      hotspot: "stats-histogram",
      area: "action",
    },
    {
      summary: "Use the breakdown table to filter",
      description:
        "If there are many actions, a breakdown is provided with common alert attributes to allow fast filtering.",
      hotspot: null,
      area: "alert",
    },
    {
      summary: "Filter olives with file names, description, and tags",
      description:
        "Use Description or Tag in the olive source to anotate olives (and the actions they generate). Those annotations and the file name can be used as search keywords in the olive selection box.",
      hotspot: null,
      area: "olive",
    },
    {
      summary: "Custom actions can be uploaded",
      description:
        "The simulator has access to all the actions on a server, but the Extra Definitions tab allows uploading custom actions, for in-development workflows.",
      hotspot: null,
      area: "simulator",
    },
  ],
];

/**
 * This is the help information stored in the browser to track what the user has seen.
 */
interface HelpState {
  /** This is one above the highest version number the user has been exposted to. (This means it should be the length of the tips list once the system is initialised.)*/
  version: number;
  /**
   * All the tips the user has yet to see. These are a pair of indices into the features list
   */
  tips: [number, number][];
}
let state = locallyStored<HelpState>("shesmu_help", { version: 0, tips: [] });
let model: Publisher<HelpState> = pubSubModel();
model.subscribe({ ...state.model, isAlive: true });
{
  const current = state.last;
  // In case there are any garbage tips, remove them from the user's config. If the server is rolled back, and then forward, it might cause tips to be marked as read, but that's not such a big deal. It's also per server; not global accross all Shesmu instances a user accesses.
  current.tips = current.tips.filter(
    ([version, index]) =>
      version >= 0 &&
      version < tips.length &&
      index >= 0 &&
      index < tips[version].length &&
      tips[version][index] != null
  );
  // Now, pack any unseen tips into the user's config
  for (let i = current.version; i < tips.length; i++) {
    current.tips = current.tips.concat(
      [...tips[i].keys()]
        .filter((index) => tips[i][index] != null)
        .map((index) => [i, index])
    );
  }
  current.version = tips.length;
  model.statusChanged(current);
}

function helpButton(
  readTooltip: string,
  readAllEnabled: boolean,
  predicate: (version: number, index: number, tip: HelpTip) => boolean
): UIElement {
  return subscribedState(state.last, model, (input: HelpState) => {
    const hasNew = input.tips.some(([version, index]) => {
      const tip = tips[version]?.[index];
      return tip && predicate(version, index, tip);
    });
    const popupContainer = document.createElement("div");
    popupContainer.className = "helpcapture";
    const popup = document.createElement("div");
    const markReadOnClose: Map<number, Set<number>> = new Map();
    addElements(
      popup,
      hasNew
        ? buttonIcon("✓ Read", readTooltip, () => {
            document.body.removeChild(popupContainer);
            model.statusChanged({
              version: input.version,
              tips: input.tips.filter(([version, index]) => {
                const tip = tips[version]?.[index];
                return tip != null && !predicate(version, index, tip);
              }),
            });
          })
        : blank(),
      readAllEnabled && hasNew
        ? [
            " | ",
            buttonIcon(
              "🙈 Ignore All Tips",
              "Mark all tips everywhere as read.",
              () => {
                document.body.removeChild(popupContainer);
                model.statusChanged({
                  version: input.version,
                  tips: [],
                });
              }
            ),
          ]
        : blank(),
      br(),
      tips
        .flatMap((tips, version) =>
          tips.map((tip, index) => ({
            version: version,
            index: index,
            tip: tip,
          }))
        )
        .filter(
          ({ index, tip, version }) => tip && predicate(version, index, tip)
        )
        .sort((a, b) => b.version - a.version || a.index - b.index)
        .map(({ index, tip, version }) => {
          if (tip == null) return blank();
          const isNew = input.tips.some(([v, i]) => v == version && i == index);
          const header = document.createElement("p");
          const more = document.createElement("span");
          addElements(header, "💡 ", tip.summary, more);
          more.innerText = " (more)";
          more.style.color = "#aaa";
          header.style.fontWeight = isNew ? "bold" : "normal";
          const body = document.createElement("div");
          body.innerText = tip.description;
          body.style.display = "none";
          header.addEventListener("click", () => {
            body.style.display = "block";
            more.innerText = "";
            if (!markReadOnClose.has(version)) {
              markReadOnClose.set(version, new Set());
            }
            markReadOnClose.get(version)?.add(index);
          });
          return [hr(), header, body];
        })
    );
    popup.addEventListener("click", (e) => e.stopPropagation());
    popupContainer.appendChild(popup);
    popupContainer.addEventListener("click", (e) => {
      e.stopPropagation();
      document.body.removeChild(popupContainer);
      if (hasNew) {
        model.statusChanged({
          version: input.version,
          tips: input.tips.filter(
            ([version, index]) => !markReadOnClose.get(version)?.has(index)
          ),
        });
      }
    });
    return buttonIcon(
      hasNew ? "ⓘ️✨" : "ⓘ️",
      hasNew ? "New tips are available!" : "Previous tips and advice.",
      (e) => {
        e.stopPropagation();
        popup.style.left = `${e.pageX}px`;
        popup.style.top = `${e.pageY}px`;
        document.body.appendChild(popupContainer);
      }
    );
  });
}

/**
 * Create an collection of information for an “area” (generally a page or dashboard)
 */
export function helpArea(area: Area): UIElement {
  return helpButton(
    "Mark all tips for this dashboard as read.",
    false,
    (_version, _index, tip) => tip.area == area
  );
}

/**
 * Create a "hotspot" that can display help information if it is available.
 *
 * This is a widget that exists in part of a UI widget
 * @param hotspot the unique ID of this hotspot that will display related tips.
 */
export function helpHotspot(hotspot: HotSpot): UIElement {
  return helpButton(
    "Mark all tips for this widget as read.",
    false,
    (_version, _index, tip) => tip.hotspot == hotspot
  );
}

/**
 * Create help information for the current version of the server
 */
export function initialiseStatusHelp(): void {
  const root = document.body.children.item(1)!;
  const div = document.createElement("div");
  root.insertBefore(div, root.firstChild);
  addElements(
    div,
    helpButton(
      "Mark all tips from this release as read.",
      true,
      (version, _index, _tip) => version == tips.length - 1
    )
  );
}

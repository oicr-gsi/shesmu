import { saveFile } from "./io.js";
import {
  Publisher,
  SourceLocation,
  StatefulModel,
  combineModels,
  computeDuration,
  filterModel,
  mapModel,
  mergingModel,
  shuffle,
} from "./util.js";
import { Query } from "./actionfilters.js";
import { AlertFilter } from "./alert.js";

/**
 * A function to render an item that can handle click events.
 */
export type ActiveItemRenderer<T> = (item: T, click: ClickHandler) => UIElement;
/**
 * The callback for handling mouse events
 */
export type ClickHandler = (e: MouseEvent) => void;
/**
 * A UI element associated with a DOM node and additinal behaviours
 */
export interface ComplexElement<T extends HTMLElement> {
  type: "ui";
  /**
   * The DOM node
   */
  element: T;
  /**
   * A callback to handle pressing Ctrl-F
   */
  find: FindHandler;
  /**
   * A callback to invoke when the element is displayed (either initially or in case of switching tabs)
   */
  reveal: (() => void) | null;
}
/** UI elements that can be duplicate at will (_i.e_, they are not associated with any DOM nodes) */
export type DisplayElement =
  | DisplayElement[]
  | string
  | number
  | null // Use for a WBR element
  | IconElement
  | FormattedElement
  | LinkElement;

/**
 * A row in a drop down table
 */
export interface DropdownTableRow<T> {
  /** The value that is selected*/
  value: T;
  /** The cells that should be put in the table for this entry */
  label: TableCell[];
}
/**
 * A minitable in a drop-down table
 */
export interface DropdownTableSection<T> {
  /** The value that is selected when this section is selected*/
  value: T;
  /**
   * The title for this table
   */
  label: UIElement;
  /**
   * The entries in the table
   */
  children: DropdownTableRow<T>[];
}
/**
 * A flexible element in a flex box
 */
export interface FlexElement {
  css?: string[];
  /**
   * The UI to show in the flex box
   */
  contents: UIElement;
  /**
   * The relative width of the element
   */
  width: number;
}

export interface InputField<T> {
  ui: UIElement;
  value: T;
  enabled: boolean;
}
/**
 * A function which will intercept Ctrl-F
 * @returns true if it wishes to intercept the browser's default behaviour
 */
export type FindHandler = (() => boolean) | null;
/**
 * Text with simple formatting
 */
export interface FormattedElement {
  type:
    | "b" // bold
    | "i" // italic
    | "p" //paragraph
    | "s" //strike through
    | "tt"; // typewriter/monospace
  contents: DisplayElement;
}

export interface IconElement {
  type: "icon";
  icon: IconName;
}
export type IconName =
  | "alarm-fill"
  | "alarm"
  | "align-bottom"
  | "align-center"
  | "align-end"
  | "align-middle"
  | "align-start"
  | "align-top"
  | "alt"
  | "app-indicator"
  | "app"
  | "archive-fill"
  | "archive"
  | "arrow-90deg-down"
  | "arrow-90deg-left"
  | "arrow-90deg-right"
  | "arrow-90deg-up"
  | "arrow-bar-down"
  | "arrow-bar-left"
  | "arrow-bar-right"
  | "arrow-bar-up"
  | "arrow-clockwise"
  | "arrow-counterclockwise"
  | "arrow-down-circle-fill"
  | "arrow-down-circle"
  | "arrow-down-left-circle-fill"
  | "arrow-down-left-circle"
  | "arrow-down-left-square-fill"
  | "arrow-down-left-square"
  | "arrow-down-left"
  | "arrow-down-right-circle-fill"
  | "arrow-down-right-circle"
  | "arrow-down-right-square-fill"
  | "arrow-down-right-square"
  | "arrow-down-right"
  | "arrow-down-short"
  | "arrow-down-square-fill"
  | "arrow-down-square"
  | "arrow-down"
  | "arrow-down-up"
  | "arrow-left-circle-fill"
  | "arrow-left-circle"
  | "arrow-left-right"
  | "arrow-left-short"
  | "arrow-left-square-fill"
  | "arrow-left-square"
  | "arrow-left"
  | "arrow-repeat"
  | "arrow-return-left"
  | "arrow-return-right"
  | "arrow-right-circle-fill"
  | "arrow-right-circle"
  | "arrow-right-short"
  | "arrow-right-square-fill"
  | "arrow-right-square"
  | "arrow-right"
  | "arrows-angle-contract"
  | "arrows-angle-expand"
  | "arrows-collapse"
  | "arrows-expand"
  | "arrows-fullscreen"
  | "arrows-move"
  | "arrow-up-circle-fill"
  | "arrow-up-circle"
  | "arrow-up-left-circle-fill"
  | "arrow-up-left-circle"
  | "arrow-up-left-square-fill"
  | "arrow-up-left-square"
  | "arrow-up-left"
  | "arrow-up-right-circle-fill"
  | "arrow-up-right-circle"
  | "arrow-up-right-square-fill"
  | "arrow-up-right-square"
  | "arrow-up-right"
  | "arrow-up-short"
  | "arrow-up-square-fill"
  | "arrow-up-square"
  | "arrow-up"
  | "aspect-ratio-fill"
  | "aspect-ratio"
  | "asterisk"
  | "at"
  | "award-fill"
  | "award"
  | "backspace-fill"
  | "backspace-reverse-fill"
  | "backspace-reverse"
  | "backspace"
  | "back"
  | "badge-3d-fill"
  | "badge-3d"
  | "badge-4k-fill"
  | "badge-4k"
  | "badge-8k-fill"
  | "badge-8k"
  | "badge-ad-fill"
  | "badge-ad"
  | "badge-ar-fill"
  | "badge-ar"
  | "badge-cc-fill"
  | "badge-cc"
  | "badge-hd-fill"
  | "badge-hd"
  | "badge-tm-fill"
  | "badge-tm"
  | "badge-vo-fill"
  | "badge-vo"
  | "badge-vr-fill"
  | "badge-vr"
  | "badge-wc-fill"
  | "badge-wc"
  | "bag-check-fill"
  | "bag-check"
  | "bag-dash-fill"
  | "bag-dash"
  | "bag-fill"
  | "bag-plus-fill"
  | "bag-plus"
  | "bag"
  | "bag-x-fill"
  | "bag-x"
  | "bar-chart-fill"
  | "bar-chart-line-fill"
  | "bar-chart-line"
  | "bar-chart-steps"
  | "bar-chart"
  | "basket2-fill"
  | "basket2"
  | "basket3-fill"
  | "basket3"
  | "basket-fill"
  | "basket"
  | "battery-charging"
  | "battery-full"
  | "battery-half"
  | "battery"
  | "bell-fill"
  | "bell"
  | "bezier2"
  | "bezier"
  | "bicycle"
  | "binoculars-fill"
  | "binoculars"
  | "blockquote-left"
  | "blockquote-right"
  | "book-fill"
  | "book-half"
  | "bookmark-check-fill"
  | "bookmark-check"
  | "bookmark-dash-fill"
  | "bookmark-dash"
  | "bookmark-fill"
  | "bookmark-heart-fill"
  | "bookmark-heart"
  | "bookmark-plus-fill"
  | "bookmark-plus"
  | "bookmarks-fill"
  | "bookmarks"
  | "bookmark-star-fill"
  | "bookmark-star"
  | "bookmark"
  | "bookmark-x-fill"
  | "bookmark-x"
  | "bookshelf"
  | "book"
  | "bootstrap-fill"
  | "bootstrap-icons"
  | "bootstrap-reboot"
  | "bootstrap"
  | "border-all"
  | "border-bottom"
  | "border-center"
  | "border-inner"
  | "border-left"
  | "border-middle"
  | "border-outer"
  | "border-right"
  | "border-style"
  | "border"
  | "border-top"
  | "border-width"
  | "bounding-box-circles"
  | "bounding-box"
  | "box-arrow-down-left"
  | "box-arrow-down-right"
  | "box-arrow-down"
  | "box-arrow-in-down-left"
  | "box-arrow-in-down-right"
  | "box-arrow-in-down"
  | "box-arrow-in-left"
  | "box-arrow-in-right"
  | "box-arrow-in-up-left"
  | "box-arrow-in-up-right"
  | "box-arrow-in-up"
  | "box-arrow-left"
  | "box-arrow-right"
  | "box-arrow-up-left"
  | "box-arrow-up-right"
  | "box-arrow-up"
  | "box-seam"
  | "box"
  | "braces"
  | "bricks"
  | "briefcase-fill"
  | "briefcase"
  | "brightness-alt-high-fill"
  | "brightness-alt-high"
  | "brightness-alt-low-fill"
  | "brightness-alt-low"
  | "brightness-high-fill"
  | "brightness-high"
  | "brightness-low-fill"
  | "brightness-low"
  | "broadcast-pin"
  | "broadcast"
  | "brush-fill"
  | "brush"
  | "bucket-fill"
  | "bucket"
  | "bug-fill"
  | "bug"
  | "building"
  | "bullseye"
  | "calculator-fill"
  | "calculator"
  | "calendar2-check-fill"
  | "calendar2-check"
  | "calendar2-date-fill"
  | "calendar2-date"
  | "calendar2-day-fill"
  | "calendar2-day"
  | "calendar2-event-fill"
  | "calendar2-event"
  | "calendar2-fill"
  | "calendar2-minus-fill"
  | "calendar2-minus"
  | "calendar2-month-fill"
  | "calendar2-month"
  | "calendar2-plus-fill"
  | "calendar2-plus"
  | "calendar2-range-fill"
  | "calendar2-range"
  | "calendar2"
  | "calendar2-week-fill"
  | "calendar2-week"
  | "calendar2-x-fill"
  | "calendar2-x"
  | "calendar3-event-fill"
  | "calendar3-event"
  | "calendar3-fill"
  | "calendar3-range-fill"
  | "calendar3-range"
  | "calendar3"
  | "calendar3-week-fill"
  | "calendar3-week"
  | "calendar4-event"
  | "calendar4-range"
  | "calendar4"
  | "calendar4-week"
  | "calendar-check-fill"
  | "calendar-check"
  | "calendar-date-fill"
  | "calendar-date"
  | "calendar-day-fill"
  | "calendar-day"
  | "calendar-event-fill"
  | "calendar-event"
  | "calendar-fill"
  | "calendar-minus-fill"
  | "calendar-minus"
  | "calendar-month-fill"
  | "calendar-month"
  | "calendar-plus-fill"
  | "calendar-plus"
  | "calendar-range-fill"
  | "calendar-range"
  | "calendar"
  | "calendar-week-fill"
  | "calendar-week"
  | "calendar-x-fill"
  | "calendar-x"
  | "camera2"
  | "camera-fill"
  | "camera-reels-fill"
  | "camera-reels"
  | "camera"
  | "camera-video-fill"
  | "camera-video-off-fill"
  | "camera-video-off"
  | "camera-video"
  | "capslock-fill"
  | "capslock"
  | "card-checklist"
  | "card-heading"
  | "card-image"
  | "card-list"
  | "card-text"
  | "caret-down-fill"
  | "caret-down-square-fill"
  | "caret-down-square"
  | "caret-down"
  | "caret-left-fill"
  | "caret-left-square-fill"
  | "caret-left-square"
  | "caret-left"
  | "caret-right-fill"
  | "caret-right-square-fill"
  | "caret-right-square"
  | "caret-right"
  | "caret-up-fill"
  | "caret-up-square-fill"
  | "caret-up-square"
  | "caret-up"
  | "cart2"
  | "cart3"
  | "cart4"
  | "cart-check-fill"
  | "cart-check"
  | "cart-dash-fill"
  | "cart-dash"
  | "cart-fill"
  | "cart-plus-fill"
  | "cart-plus"
  | "cart"
  | "cart-x-fill"
  | "cart-x"
  | "cash-stack"
  | "cash"
  | "cast"
  | "chat-dots-fill"
  | "chat-dots"
  | "chat-fill"
  | "chat-left-dots-fill"
  | "chat-left-dots"
  | "chat-left-fill"
  | "chat-left-quote-fill"
  | "chat-left-quote"
  | "chat-left"
  | "chat-left-text-fill"
  | "chat-left-text"
  | "chat-quote-fill"
  | "chat-quote"
  | "chat-right-dots-fill"
  | "chat-right-dots"
  | "chat-right-fill"
  | "chat-right-quote-fill"
  | "chat-right-quote"
  | "chat-right"
  | "chat-right-text-fill"
  | "chat-right-text"
  | "chat-square-dots-fill"
  | "chat-square-dots"
  | "chat-square-fill"
  | "chat-square-quote-fill"
  | "chat-square-quote"
  | "chat-square"
  | "chat-square-text-fill"
  | "chat-square-text"
  | "chat"
  | "chat-text-fill"
  | "chat-text"
  | "check2-all"
  | "check2-circle"
  | "check2-square"
  | "check2"
  | "check-all"
  | "check-circle-fill"
  | "check-circle"
  | "check-square-fill"
  | "check-square"
  | "check"
  | "chevron-bar-contract"
  | "chevron-bar-down"
  | "chevron-bar-expand"
  | "chevron-bar-left"
  | "chevron-bar-right"
  | "chevron-bar-up"
  | "chevron-compact-down"
  | "chevron-compact-left"
  | "chevron-compact-right"
  | "chevron-compact-up"
  | "chevron-contract"
  | "chevron-double-down"
  | "chevron-double-left"
  | "chevron-double-right"
  | "chevron-double-up"
  | "chevron-down"
  | "chevron-expand"
  | "chevron-left"
  | "chevron-right"
  | "chevron-up"
  | "circle-fill"
  | "circle-half"
  | "circle-square"
  | "circle"
  | "clipboard-check"
  | "clipboard-data"
  | "clipboard-minus"
  | "clipboard-plus"
  | "clipboard"
  | "clipboard-x"
  | "clock-fill"
  | "clock-history"
  | "clock"
  | "cloud-arrow-down-fill"
  | "cloud-arrow-down"
  | "cloud-arrow-up-fill"
  | "cloud-arrow-up"
  | "cloud-check-fill"
  | "cloud-check"
  | "cloud-download-fill"
  | "cloud-download"
  | "cloud-drizzle-fill"
  | "cloud-drizzle"
  | "cloud-fill"
  | "cloud-fog2-fill"
  | "cloud-fog2"
  | "cloud-fog-fill"
  | "cloud-fog"
  | "cloud-hail-fill"
  | "cloud-hail"
  | "cloud-haze-1"
  | "cloud-haze2-fill"
  | "cloud-haze-fill"
  | "cloud-haze"
  | "cloud-lightning-fill"
  | "cloud-lightning-rain-fill"
  | "cloud-lightning-rain"
  | "cloud-lightning"
  | "cloud-minus-fill"
  | "cloud-minus"
  | "cloud-moon-fill"
  | "cloud-moon"
  | "cloud-plus-fill"
  | "cloud-plus"
  | "cloud-rain-fill"
  | "cloud-rain-heavy-fill"
  | "cloud-rain-heavy"
  | "cloud-rain"
  | "clouds-fill"
  | "cloud-slash-fill"
  | "cloud-slash"
  | "cloud-sleet-fill"
  | "cloud-sleet"
  | "cloud-snow-fill"
  | "cloud-snow"
  | "clouds"
  | "cloud-sun-fill"
  | "cloud-sun"
  | "cloud"
  | "cloud-upload-fill"
  | "cloud-upload"
  | "cloudy-fill"
  | "cloudy"
  | "code-slash"
  | "code-square"
  | "code"
  | "collection-fill"
  | "collection-play-fill"
  | "collection-play"
  | "collection"
  | "columns-gap"
  | "columns"
  | "command"
  | "compass-fill"
  | "compass"
  | "cone-striped"
  | "cone"
  | "controller"
  | "cpu-fill"
  | "cpu"
  | "credit-card-2-back-fill"
  | "credit-card-2-back"
  | "credit-card-2-front-fill"
  | "credit-card-2-front"
  | "credit-card-fill"
  | "credit-card"
  | "crop"
  | "cup-fill"
  | "cup-straw"
  | "cup"
  | "cursor-fill"
  | "cursor"
  | "cursor-text"
  | "dash-circle-dotted"
  | "dash-circle-fill"
  | "dash-circle"
  | "dash-square-dotted"
  | "dash-square-fill"
  | "dash-square"
  | "dash"
  | "diagram-2-fill"
  | "diagram-2"
  | "diagram-3-fill"
  | "diagram-3"
  | "diamond-fill"
  | "diamond-half"
  | "diamond"
  | "dice-1-fill"
  | "dice-1"
  | "dice-2-fill"
  | "dice-2"
  | "dice-3-fill"
  | "dice-3"
  | "dice-4-fill"
  | "dice-4"
  | "dice-5-fill"
  | "dice-5"
  | "dice-6-fill"
  | "dice-6"
  | "disc-fill"
  | "discord"
  | "disc"
  | "display-fill"
  | "display"
  | "distribute-horizontal"
  | "distribute-vertical"
  | "door-closed-fill"
  | "door-closed"
  | "door-open-fill"
  | "door-open"
  | "dot"
  | "download"
  | "droplet-fill"
  | "droplet-half"
  | "droplet"
  | "earbuds"
  | "easel-fill"
  | "easel"
  | "egg-fill"
  | "egg-fried"
  | "egg"
  | "eject-fill"
  | "eject"
  | "emoji-angry-fill"
  | "emoji-angry"
  | "emoji-dizzy-fill"
  | "emoji-dizzy"
  | "emoji-expressionless-fill"
  | "emoji-expressionless"
  | "emoji-frown-fill"
  | "emoji-frown"
  | "emoji-heart-eyes-fill"
  | "emoji-heart-eyes"
  | "emoji-laughing-fill"
  | "emoji-laughing"
  | "emoji-neutral-fill"
  | "emoji-neutral"
  | "emoji-smile-fill"
  | "emoji-smile"
  | "emoji-smile-upside-down-fill"
  | "emoji-smile-upside-down"
  | "emoji-sunglasses-fill"
  | "emoji-sunglasses"
  | "emoji-wink-fill"
  | "emoji-wink"
  | "envelope-fill"
  | "envelope-open-fill"
  | "envelope-open"
  | "envelope"
  | "eraser-fill"
  | "eraser"
  | "exclamation-circle-fill"
  | "exclamation-circle"
  | "exclamation-diamond-fill"
  | "exclamation-diamond"
  | "exclamation-octagon-fill"
  | "exclamation-octagon"
  | "exclamation-square-fill"
  | "exclamation-square"
  | "exclamation"
  | "exclamation-triangle-fill"
  | "exclamation-triangle"
  | "exclude"
  | "eyedropper"
  | "eye-fill"
  | "eyeglasses"
  | "eye-slash-fill"
  | "eye-slash"
  | "eye"
  | "facebook"
  | "file-arrow-down-fill"
  | "file-arrow-down"
  | "file-arrow-up-fill"
  | "file-arrow-up"
  | "file-bar-graph-fill"
  | "file-bar-graph"
  | "file-binary-fill"
  | "file-binary"
  | "file-break-fill"
  | "file-break"
  | "file-check-fill"
  | "file-check"
  | "file-code-fill"
  | "file-code"
  | "file-diff-fill"
  | "file-diff"
  | "file-earmark-arrow-down-fill"
  | "file-earmark-arrow-down"
  | "file-earmark-arrow-up-fill"
  | "file-earmark-arrow-up"
  | "file-earmark-bar-graph-fill"
  | "file-earmark-bar-graph"
  | "file-earmark-binary-fill"
  | "file-earmark-binary"
  | "file-earmark-break-fill"
  | "file-earmark-break"
  | "file-earmark-check-fill"
  | "file-earmark-check"
  | "file-earmark-code-fill"
  | "file-earmark-code"
  | "file-earmark-diff-fill"
  | "file-earmark-diff"
  | "file-earmark-easel-fill"
  | "file-earmark-easel"
  | "file-earmark-excel-fill"
  | "file-earmark-excel"
  | "file-earmark-fill"
  | "file-earmark-font-fill"
  | "file-earmark-font"
  | "file-earmark-image-fill"
  | "file-earmark-image"
  | "file-earmark-lock2-fill"
  | "file-earmark-lock2"
  | "file-earmark-lock-fill"
  | "file-earmark-lock"
  | "file-earmark-medical-fill"
  | "file-earmark-medical"
  | "file-earmark-minus-fill"
  | "file-earmark-minus"
  | "file-earmark-music-fill"
  | "file-earmark-music"
  | "file-earmark-person-fill"
  | "file-earmark-person"
  | "file-earmark-play-fill"
  | "file-earmark-play"
  | "file-earmark-plus-fill"
  | "file-earmark-plus"
  | "file-earmark-post-fill"
  | "file-earmark-post"
  | "file-earmark-ppt-fill"
  | "file-earmark-ppt"
  | "file-earmark-richtext-fill"
  | "file-earmark-richtext"
  | "file-earmark-ruled-fill"
  | "file-earmark-ruled"
  | "file-earmark-slides-fill"
  | "file-earmark-slides"
  | "file-earmark-spreadsheet-fill"
  | "file-earmark-spreadsheet"
  | "file-earmark"
  | "file-earmark-text-fill"
  | "file-earmark-text"
  | "file-earmark-word-fill"
  | "file-earmark-word"
  | "file-earmark-x-fill"
  | "file-earmark-x"
  | "file-earmark-zip-fill"
  | "file-earmark-zip"
  | "file-easel-fill"
  | "file-easel"
  | "file-excel-fill"
  | "file-excel"
  | "file-fill"
  | "file-font-fill"
  | "file-font"
  | "file-image-fill"
  | "file-image"
  | "file-lock2-fill"
  | "file-lock2"
  | "file-lock-fill"
  | "file-lock"
  | "file-medical-fill"
  | "file-medical"
  | "file-minus-fill"
  | "file-minus"
  | "file-music-fill"
  | "file-music"
  | "file-person-fill"
  | "file-person"
  | "file-play-fill"
  | "file-play"
  | "file-plus-fill"
  | "file-plus"
  | "file-post-fill"
  | "file-post"
  | "file-ppt-fill"
  | "file-ppt"
  | "file-richtext-fill"
  | "file-richtext"
  | "file-ruled-fill"
  | "file-ruled"
  | "files-alt"
  | "file-slides-fill"
  | "file-slides"
  | "file-spreadsheet-fill"
  | "file-spreadsheet"
  | "files"
  | "file"
  | "file-text-fill"
  | "file-text"
  | "file-word-fill"
  | "file-word"
  | "file-x-fill"
  | "file-x"
  | "file-zip-fill"
  | "file-zip"
  | "film"
  | "filter-circle-fill"
  | "filter-circle"
  | "filter-left"
  | "filter-right"
  | "filter-square-fill"
  | "filter-square"
  | "filter"
  | "flag-fill"
  | "flag"
  | "flower1"
  | "flower2"
  | "flower3"
  | "folder2-open"
  | "folder2"
  | "folder-check"
  | "folder-fill"
  | "folder-minus"
  | "folder-plus"
  | "folder"
  | "folder-symlink-fill"
  | "folder-symlink"
  | "folder-x"
  | "fonts"
  | "forward-fill"
  | "forward"
  | "front"
  | "fullscreen-exit"
  | "fullscreen"
  | "funnel-fill"
  | "funnel"
  | "gear-fill"
  | "gear"
  | "gear-wide-connected"
  | "gear-wide"
  | "gem"
  | "geo-alt-fill"
  | "geo-alt"
  | "geo-fill"
  | "geo"
  | "gift-fill"
  | "gift"
  | "github"
  | "globe2"
  | "globe"
  | "google"
  | "graph-down"
  | "graph-up"
  | "grid-1x2-fill"
  | "grid-1x2"
  | "grid-3x2-gap-fill"
  | "grid-3x2-gap"
  | "grid-3x2"
  | "grid-3x3-gap-fill"
  | "grid-3x3-gap"
  | "grid-3x3"
  | "grid-fill"
  | "grid"
  | "grip-horizontal"
  | "grip-vertical"
  | "hammer"
  | "handbag-fill"
  | "handbag"
  | "hand-index-fill"
  | "hand-index"
  | "hand-index-thumb-fill"
  | "hand-index-thumb"
  | "hand-thumbs-down-fill"
  | "hand-thumbs-down"
  | "hand-thumbs-up-fill"
  | "hand-thumbs-up"
  | "hash"
  | "hdd-fill"
  | "hdd-network-fill"
  | "hdd-network"
  | "hdd-rack-fill"
  | "hdd-rack"
  | "hdd-stack-fill"
  | "hdd-stack"
  | "hdd"
  | "headphones"
  | "headset"
  | "heart-fill"
  | "heart-half"
  | "heart"
  | "heptagon-fill"
  | "heptagon-half"
  | "heptagon"
  | "hexagon-fill"
  | "hexagon-half"
  | "hexagon"
  | "hourglass-bottom"
  | "hourglass-split"
  | "hourglass"
  | "hourglass-top"
  | "house-door-fill"
  | "house-door"
  | "house-fill"
  | "house"
  | "hr"
  | "hurricane"
  | "image-alt"
  | "image-fill"
  | "images"
  | "image"
  | "inboxes-fill"
  | "inboxes"
  | "inbox-fill"
  | "inbox"
  | "info-circle-fill"
  | "info-circle"
  | "info-square-fill"
  | "info-square"
  | "info"
  | "input-cursor"
  | "input-cursor-text"
  | "instagram"
  | "intersect"
  | "journal-album"
  | "journal-arrow-down"
  | "journal-arrow-up"
  | "journal-bookmark-fill"
  | "journal-bookmark"
  | "journal-check"
  | "journal-code"
  | "journal-medical"
  | "journal-minus"
  | "journal-plus"
  | "journal-richtext"
  | "journals"
  | "journal"
  | "journal-text"
  | "journal-x"
  | "joystick"
  | "justify-left"
  | "justify-right"
  | "justify"
  | "kanban-fill"
  | "kanban"
  | "keyboard-fill"
  | "keyboard"
  | "key-fill"
  | "key"
  | "ladder"
  | "lamp-fill"
  | "lamp"
  | "laptop-fill"
  | "laptop"
  | "layer-backward"
  | "layer-forward"
  | "layers-fill"
  | "layers-half"
  | "layers"
  | "layout-sidebar-inset-reverse"
  | "layout-sidebar-inset"
  | "layout-sidebar-reverse"
  | "layout-sidebar"
  | "layout-split"
  | "layout-text-sidebar-reverse"
  | "layout-text-sidebar"
  | "layout-text-window-reverse"
  | "layout-text-window"
  | "layout-three-columns"
  | "layout-wtf"
  | "life-preserver"
  | "lightbulb-fill"
  | "lightbulb-off-fill"
  | "lightbulb-off"
  | "lightbulb"
  | "lightning-charge-fill"
  | "lightning-charge"
  | "lightning-fill"
  | "lightning"
  | "link-45deg"
  | "linkedin"
  | "link"
  | "list-check"
  | "list-nested"
  | "list-ol"
  | "list-stars"
  | "list"
  | "list-task"
  | "list-ul"
  | "lock-fill"
  | "lock"
  | "mailbox2"
  | "mailbox"
  | "map-fill"
  | "map"
  | "markdown-fill"
  | "markdown"
  | "mask"
  | "megaphone-fill"
  | "megaphone"
  | "menu-app-fill"
  | "menu-app"
  | "menu-button-fill"
  | "menu-button"
  | "menu-button-wide-fill"
  | "menu-button-wide"
  | "menu-down"
  | "menu-up"
  | "mic-fill"
  | "mic-mute-fill"
  | "mic-mute"
  | "mic"
  | "minecart-loaded"
  | "minecart"
  | "moisture"
  | "moon-fill"
  | "moon-stars-fill"
  | "moon-stars"
  | "moon"
  | "mouse2-fill"
  | "mouse2"
  | "mouse3-fill"
  | "mouse3"
  | "mouse-fill"
  | "mouse"
  | "music-note-beamed"
  | "music-note-list"
  | "music-note"
  | "music-player-fill"
  | "music-player"
  | "newspaper"
  | "node-minus-fill"
  | "node-minus"
  | "node-plus-fill"
  | "node-plus"
  | "nut-fill"
  | "nut"
  | "octagon-fill"
  | "octagon-half"
  | "octagon"
  | "option"
  | "outlet"
  | "paint-bucket"
  | "palette2"
  | "palette-fill"
  | "palette"
  | "paperclip"
  | "paragraph"
  | "patch-check-fill"
  | "patch-check"
  | "patch-exclamation-fill"
  | "patch-exclamation"
  | "patch-minus-fill"
  | "patch-minus"
  | "patch-plus-fill"
  | "patch-plus"
  | "patch-question-fill"
  | "patch-question"
  | "pause-btn-fill"
  | "pause-btn"
  | "pause-circle-fill"
  | "pause-circle"
  | "pause-fill"
  | "pause"
  | "peace-fill"
  | "peace"
  | "pencil-fill"
  | "pencil-square"
  | "pencil"
  | "pen-fill"
  | "pen"
  | "pentagon-fill"
  | "pentagon-half"
  | "pentagon"
  | "people-fill"
  | "people"
  | "percent"
  | "person-badge-fill"
  | "person-badge"
  | "person-bounding-box"
  | "person-check-fill"
  | "person-check"
  | "person-circle"
  | "person-dash-fill"
  | "person-dash"
  | "person-fill"
  | "person-lines-fill"
  | "person-plus-fill"
  | "person-plus"
  | "person-square"
  | "person"
  | "person-x-fill"
  | "person-x"
  | "phone-fill"
  | "phone-landscape-fill"
  | "phone-landscape"
  | "phone"
  | "phone-vibrate-fill"
  | "phone-vibrate"
  | "pie-chart-fill"
  | "pie-chart"
  | "pin-angle-fill"
  | "pin-angle"
  | "pin-fill"
  | "pin"
  | "pip-fill"
  | "pip"
  | "play-btn-fill"
  | "play-btn"
  | "play-circle-fill"
  | "play-circle"
  | "play-fill"
  | "play"
  | "plug-fill"
  | "plug"
  | "plus-circle-dotted"
  | "plus-circle-fill"
  | "plus-circle"
  | "plus-square-dotted"
  | "plus-square-fill"
  | "plus-square"
  | "plus"
  | "power"
  | "printer-fill"
  | "printer"
  | "puzzle-fill"
  | "puzzle"
  | "question-circle-fill"
  | "question-circle"
  | "question-diamond-fill"
  | "question-diamond"
  | "question-octagon-fill"
  | "question-octagon"
  | "question-square-fill"
  | "question-square"
  | "question"
  | "rainbow"
  | "receipt-cutoff"
  | "receipt"
  | "reception-0"
  | "reception-1"
  | "reception-2"
  | "reception-3"
  | "reception-4"
  | "record2-fill"
  | "record2"
  | "record-btn-fill"
  | "record-btn"
  | "record-circle-fill"
  | "record-circle"
  | "record-fill"
  | "record"
  | "reply-all-fill"
  | "reply-all"
  | "reply-fill"
  | "reply"
  | "rss-fill"
  | "rss"
  | "rulers"
  | "save2-fill"
  | "save2"
  | "save-fill"
  | "save"
  | "scissors"
  | "screwdriver"
  | "search"
  | "segmented-nav"
  | "server"
  | "share-fill"
  | "share"
  | "shield-check"
  | "shield-exclamation"
  | "shield-fill-check"
  | "shield-fill-exclamation"
  | "shield-fill-minus"
  | "shield-fill-plus"
  | "shield-fill"
  | "shield-fill-x"
  | "shield-lock-fill"
  | "shield-lock"
  | "shield-minus"
  | "shield-plus"
  | "shield-shaded"
  | "shield-slash-fill"
  | "shield-slash"
  | "shield"
  | "shield-x"
  | "shift-fill"
  | "shift"
  | "shop"
  | "shop-window"
  | "shuffle"
  | "signpost-2-fill"
  | "signpost-2"
  | "signpost-fill"
  | "signpost-split-fill"
  | "signpost-split"
  | "signpost"
  | "sim-fill"
  | "sim"
  | "skip-backward-btn-fill"
  | "skip-backward-btn"
  | "skip-backward-circle-fill"
  | "skip-backward-circle"
  | "skip-backward-fill"
  | "skip-backward"
  | "skip-end-btn-fill"
  | "skip-end-btn"
  | "skip-end-circle-fill"
  | "skip-end-circle"
  | "skip-end-fill"
  | "skip-end"
  | "skip-forward-btn-fill"
  | "skip-forward-btn"
  | "skip-forward-circle-fill"
  | "skip-forward-circle"
  | "skip-forward-fill"
  | "skip-forward"
  | "skip-start-btn-fill"
  | "skip-start-btn"
  | "skip-start-circle-fill"
  | "skip-start-circle"
  | "skip-start-fill"
  | "skip-start"
  | "slack"
  | "slash-circle-fill"
  | "slash-circle"
  | "slash-square-fill"
  | "slash-square"
  | "slash"
  | "sliders"
  | "smartwatch"
  | "snow2"
  | "snow3"
  | "snow"
  | "sort-alpha-down-alt"
  | "sort-alpha-down"
  | "sort-alpha-up-alt"
  | "sort-alpha-up"
  | "sort-down-alt"
  | "sort-down"
  | "sort-numeric-down-alt"
  | "sort-numeric-down"
  | "sort-numeric-up-alt"
  | "sort-numeric-up"
  | "sort-up-alt"
  | "sort-up"
  | "soundwave"
  | "speaker-fill"
  | "speaker"
  | "speedometer2"
  | "speedometer"
  | "spellcheck"
  | "square-fill"
  | "square-half"
  | "square"
  | "stack"
  | "star-fill"
  | "star-half"
  | "stars"
  | "star"
  | "stickies-fill"
  | "stickies"
  | "sticky-fill"
  | "sticky"
  | "stop-btn-fill"
  | "stop-btn"
  | "stop-circle-fill"
  | "stop-circle"
  | "stop-fill"
  | "stoplights-fill"
  | "stoplights"
  | "stop"
  | "stopwatch-fill"
  | "stopwatch"
  | "subtract"
  | "suit-club-fill"
  | "suit-club"
  | "suit-diamond-fill"
  | "suit-diamond"
  | "suit-heart-fill"
  | "suit-heart"
  | "suit-spade-fill"
  | "suit-spade"
  | "sun-fill"
  | "sunglasses"
  | "sunrise-fill"
  | "sunrise"
  | "sunset-fill"
  | "sunset"
  | "sun"
  | "symmetry-horizontal"
  | "symmetry-vertical"
  | "table"
  | "tablet-fill"
  | "tablet-landscape-fill"
  | "tablet-landscape"
  | "tablet"
  | "tag-fill"
  | "tags-fill"
  | "tags"
  | "tag"
  | "telegram"
  | "telephone-fill"
  | "telephone-forward-fill"
  | "telephone-forward"
  | "telephone-inbound-fill"
  | "telephone-inbound"
  | "telephone-minus-fill"
  | "telephone-minus"
  | "telephone-outbound-fill"
  | "telephone-outbound"
  | "telephone-plus-fill"
  | "telephone-plus"
  | "telephone"
  | "telephone-x-fill"
  | "telephone-x"
  | "terminal-fill"
  | "terminal"
  | "textarea-resize"
  | "textarea"
  | "textarea-t"
  | "text-center"
  | "text-indent-left"
  | "text-indent-right"
  | "text-left"
  | "text-paragraph"
  | "text-right"
  | "thermometer-half"
  | "thermometer-high"
  | "thermometer-low"
  | "thermometer-snow"
  | "thermometer-sun"
  | "thermometer"
  | "three-dots"
  | "three-dots-vertical"
  | "toggle2-off"
  | "toggle2-on"
  | "toggle-off"
  | "toggle-on"
  | "toggles2"
  | "toggles"
  | "tools"
  | "tornado"
  | "trash2-fill"
  | "trash2"
  | "trash-fill"
  | "trash"
  | "tree-fill"
  | "tree"
  | "triangle-fill"
  | "triangle-half"
  | "triangle"
  | "trophy-fill"
  | "trophy"
  | "tropical-storm"
  | "truck-flatbed"
  | "truck"
  | "tsunami"
  | "tv-fill"
  | "tv"
  | "twitch"
  | "twitter"
  | "type-bold"
  | "type-h1"
  | "type-h2"
  | "type-h3"
  | "type-italic"
  | "type-strikethrough"
  | "type"
  | "type-underline"
  | "ui-checks-grid"
  | "ui-checks"
  | "ui-radios-grid"
  | "ui-radios"
  | "umbrella-fill"
  | "umbrella"
  | "union"
  | "unlock-fill"
  | "unlock"
  | "upc-scan"
  | "upc"
  | "upload"
  | "vector-pen"
  | "view-list"
  | "view-stacked"
  | "vinyl-fill"
  | "vinyl"
  | "voicemail"
  | "volume-down-fill"
  | "volume-down"
  | "volume-mute-fill"
  | "volume-mute"
  | "volume-off-fill"
  | "volume-off"
  | "volume-up-fill"
  | "volume-up"
  | "vr"
  | "wallet2"
  | "wallet-fill"
  | "wallet"
  | "watch"
  | "water"
  | "whatsapp"
  | "wifi-1"
  | "wifi-2"
  | "wifi-off"
  | "wifi"
  | "window-dock"
  | "window-sidebar"
  | "window"
  | "wind"
  | "wrench"
  | "x-circle-fill"
  | "x-circle"
  | "x-diamond-fill"
  | "x-diamond"
  | "x-octagon-fill"
  | "x-octagon"
  | "x-square-fill"
  | "x-square"
  | "x"
  | "youtube"
  | "zoom-in"
  | "zoom-out";
/**
 * A hyperlink
 */
export interface LinkElement {
  type: "a";
  /** The URL to link to */
  url: string;
  /** The display text to use */
  contents: LinkContents;
  /** The tooltop for the link */
  title: string;
}

export type LinkContents =
  | LinkContents[]
  | string
  | number
  | null
  | FormattedElement
  | IconElement;

/**
 * A mapping type for UI elements that have a multi-pane display
 *
 * TypeScript allows mapping an input object to a transformed output type. The caller will provide an object of functions to generate different panes will receive an output object of those panes with the same keys. This mapper defines that operation to the type system.
 */
export type NamedComponents<T> = {
  [P in keyof T]: UIElement;
};

export type SearchBarState = "ok" | "bad" | "busy" | "dirty";

/**
 * The request parameters for Shesmu UI pages
 */
export interface ShesmuLinks {
  actiondash: {
    filters: Query;
    saved: string;
  };
  alerts: {
    filters: AlertFilter<RegExp>[];
  };
  olivedash: {
    alert: AlertFilter<RegExp>[];
    saved: SourceLocation | null;
    filters: Query;
  };
}
/**
 * A callback that is capable of listening to updates in the state synchronizer.
 * @param state the new state
 * @param internal the event was generated by an internal status change rather than an external signal
 */
export type StateListener<T> = ((state: T, internal: boolean) => void) | null;
/**
 * An interface to keep two different system synchonrized
 */
export interface StateSynchronizer<T> extends StatefulModel<T> {
  /**
   * Read the current state.
   */
  get(): T;
  /**
   * Set a listener to listen for state updates
   *
   * Only one listener is selected at a time
   */
  listen(listener: StateListener<T>): void;
}
/**
 * An accessor for synchronizing the fields in an object independently.
 */
type SynchronizedFields<T> = {
  [P in keyof T]: StateSynchronizer<T[P]>;
};
/**
 * The type of a single tab in a multi-tabbed display
 */
export interface Tab {
  /** The header title for the tab */
  name: UIElement;
  /** The contents the body of the tab */
  contents: UIElement;
  /** If true, this tab will be selected by default*/
  selected?: boolean;
}
/**
 * Replacement tabs for a {@link tabsModel}.
 */
export interface TabUpdate {
  /**
   * The tabs to insert into the selector
   */
  tabs: Tab[];
  /**
   * If true, the tab will immediately switch to the first of the new tabs. If false, the user's current view will be maintained.
   */
  activate: boolean;
}
/**
 * The contents of a table cell
 */
export interface TableCell {
  /**
   * A callback if the cell is clicked
   */
  click?: ClickHandler;
  /**
   * The elements that should be in the cell.
   */
  contents: UIElement;
  /**
   * If true, the cell is a header; if false or absent, it is a data cell.
   */
  header?: boolean;
  /**
   * The background colour intensity between 0 and 1
   *
   * This is used by some stats tables to highlight cells based on their numeric counts as a fraction of the total. Unconventionally, 0 is white (no intensity) and 1 is a theme-appropriate blue.
   */
  intensity?: number;
  /**
   * The number of columns to span
   */
  span?: number;
  /**
   * A tooltip for the cell
   */
  title?: string;
}
/**
 * A description of a segment from the root to a leaf in a tree display
 */
export interface TreePath {
  /**
   * The "real" value associated with this segement. It will be used for comparisons against other leaves, but not displayed to the user.
   */
  value: string;
  /**
   * The way this segment should be shown to the user
   */
  display: DisplayElement;
  /**
   * Remove this segment if only has one leaf when true
   */
  elide: boolean;
}

/**
 * A bit of data that can be placed in a GUI element.
 */
export type UIElement =
  | UIElement[]
  | string
  | number
  | null
  | FormattedElement
  | IconElement
  | LinkElement
  | ComplexElement<HTMLElement>;

/**
 * A list that can be updated
 */
export interface UpdateableList<T> {
  add(item: T): void;
  keepOnly(predicate: (item: T) => boolean): void;
  replace(items: T[]): void;
}

/**
 * Add all GUI elements to an existing HTML element
 */
function addElements(
  target: HTMLElement,
  ...elements: UIElement[]
): { find: FindHandler; reveal: (() => void) | null } {
  const reveals: (() => void)[] = [];
  let find: FindHandler = null;
  function add(result: UIElement, last: boolean) {
    if (Array.isArray(result)) {
      result.forEach((result, index, arr) =>
        add(result, last && index == arr.length - 1)
      );
    } else if (result === null) {
      target.appendChild(document.createElement("wbr"));
    } else if (typeof result == "string") {
      target.appendChild(document.createTextNode(result));
    } else if (typeof result == "number") {
      target.appendChild(document.createTextNode(result.toString()));
    } else {
      switch (result.type) {
        case "a":
          {
            const element = document.createElement("a");
            addElements(element, result.contents, " 🔗");
            element.target = "_blank";
            element.href = result.url;
            element.title = result.title;
            target.appendChild(element);
          }
          break;
        case "b":
        case "i":
        case "p":
          {
            const element = createUiFromTag(result.type, result.contents);
            // Safe to discard find and reveal since only display elements should be present
            target.appendChild(element.element);
          }
          break;
        case "tt":
          {
            const element = createUiFromTag("span", result.contents);
            // Safe to discard find and reveal since only display elements should be present
            target.appendChild(element.element);
            element.element.style.fontFamily = "monospace";
          }
          break;

        case "s":
          {
            const element = createUiFromTag("span", result.contents);
            // Safe to discard find and reveal since only display elements should be present
            target.appendChild(element.element);
            element.element.style.textDecoration = "line-through";
          }
          break;
        case "ui":
          {
            target.appendChild(result.element);
            if (result.reveal) {
              reveals.push(result.reveal);
            }
            if (result.find) {
              if (find) {
                const oldFind = find;
                const newFind = result.find;
                find = () => oldFind() || newFind();
              } else {
                find = result.find;
              }
            }
          }
          break;
        case "icon":
          {
            const svg = document.createElementNS(
              "http://www.w3.org/2000/svg",
              "svg"
            );
            svg.classList.add("icon");
            svg.setAttribute("width", "1.2em");
            svg.setAttribute("height", "1.2em");
            svg.setAttribute("fill", "currentColor");
            const use = document.createElementNS(
              "http://www.w3.org/2000/svg",
              "use"
            );
            use.setAttribute("href", `bootstrap-icons.svg#${result.icon}`);
            svg.appendChild(use);
            target.appendChild(svg);
            if (last) {
              // Icons are padded on the right side for better layout, but the last icon should have this padding turned off.
              svg.style.marginRight = "0px";
            }
          }
          break;
      }
    }
  }
  elements.forEach((result, index, arr) =>
    add(result, index == arr.length - 1)
  );
  return {
    find: find,
    reveal: reveals.length ? () => reveals.forEach((r) => r()) : null,
  };
}
export function createUiFromTag<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  ...elements: UIElement[]
): ComplexElement<HTMLElementTagNameMap[K]> {
  const target = document.createElement(tag);
  const { reveal, find } = addElements(target, ...elements);
  return { element: target, find: find, reveal: reveal, type: "ui" };
}

/**
 * Add a blank UI element
 *
 * This isn't very useful, but simplifies some code paths in the action tile building.
 */
export function blank(): DisplayElement {
  return [];
}
/**
 * Create a line break
 */
export function br(): UIElement {
  return createUiFromTag("br");
}

/**
 * Create a modal dialog with a throbber in the middle.
 *
 * It returns a function to close this dialog
 */
export function busyDialog(): () => void {
  const modal = createUiFromTag("div", throbber());
  modal.element.className = "modal";
  document.body.appendChild(modal.element);
  if (modal.reveal) {
    modal.reveal();
  }
  return () => {
    if (modal.element.isConnected) {
      document.body.removeChild(modal.element);
    }
  };
}
/**
 * Create a self-closing popup notification (aka butter bar)
 * @param delay the number of milliseconds the bar should be displayed for
 * @param contents what to display in the bar
 */
export function butter(delay: number, ...contents: UIElement[]): void {
  const bar = createUiFromTag("div", contents);
  bar.element.className = "butter";
  document.body.appendChild(bar.element);
  let timeout = 0;
  const startClose = (delay: number) => {
    if (timeout) {
      window.clearTimeout(timeout);
    }

    timeout = window.setTimeout(() => {
      bar.element.style.opacity = "0";
      timeout = window.setTimeout(
        () => document.body.removeChild(bar.element),
        300
      );
    }, Math.max(300, delay - 300));
  };
  startClose(delay);
  bar.element.addEventListener("click", () => {
    document.body.removeChild(bar.element);
    if (timeout) {
      window.clearTimeout(timeout);
    }
  });
  bar.element.addEventListener("mouseenter", () => {
    if (timeout) {
      window.clearTimeout(timeout);
      timeout = 0;
      bar.element.style.opacity = "1";
    }
  });
  bar.element.addEventListener("mouseleave", () => {
    startClose(1000);
  });
  if (bar.reveal) {
    bar.reveal();
  }
}

/**
 * Create a normal button
 */
export function button(
  label: DisplayElement,
  title: string,
  callback: ClickHandler
) {
  return buttonCustom(label, title, ["regular"], callback);
}

/**
 * Create a button for a less important feature
 *
 * In the Shesmu UI, some features (such as exporting searches) as considered less important, and get a softer colour than the normal buttons.
 */
export function buttonAccessory(
  label: DisplayElement,
  title: string,
  callback: ClickHandler
) {
  return buttonCustom(label, title, ["accessory"], callback);
}

/**
 * Create a button with a custom UI design
 */
export function buttonCustom(
  label: DisplayElement,
  title: string,
  className: string[],
  callback: ClickHandler
) {
  const button = createUiFromTag("span", label);
  button.element.classList.add("button");
  for (const name of className) {
    button.element.classList.add(name);
  }
  button.element.title = title;
  button.element.addEventListener("click", (e) => {
    e.stopPropagation();
    callback(e);
  });
  return button;
}

export function buttonDisabled(label: DisplayElement, title: string) {
  const button = createUiFromTag("span", label);
  button.element.classList.add("button");
  button.element.classList.add("disabled");
  button.element.title = title;
  button.element.addEventListener("click", (e) => e.stopPropagation());
  return button;
}

/**
 * Create a close button
 */
export function buttonClose(title: string, callback: ClickHandler): UIElement {
  return buttonIcon("✖", title, callback);
}
/**
 * Create a button for a dangerous feature
 */
export function buttonDanger(
  label: DisplayElement,
  title: string,
  callback: ClickHandler
) {
  return buttonCustom(label, title, ["danger"], callback);
}
/**
 * Create an edit button
 */
export function buttonEdit(title: string, callback: ClickHandler): UIElement {
  return buttonIcon("✎", title, callback);
}
/**
 * Create a button that has no “button” styling, used for features where naked icons are helpful
 */
export function buttonIcon(
  icon: DisplayElement,
  title: string,
  callback: ClickHandler
): UIElement {
  const button = createUiFromTag("span", icon);
  button.element.className = "iconbutton";
  button.element.title = title;
  button.element.addEventListener("click", (e) => {
    e.stopPropagation();
    callback(e);
  });
  return button;
}

/**
 * Check if a control/command key is pressed
 * @param e the event
 * @param key the letter to check for
 */
export function checkKey(e: KeyboardEvent, key: string): boolean {
  return (
    (window.navigator.platform.match("Mac") ? e.metaKey : e.ctrlKey) &&
    e.key === key
  );
}

/**
 * Display a panel that requires the user to enter a random substitution code.
 */
export function checkRandomPermutation(
  count: number,
  callback: () => void
): UIElement {
  const code: number[] = new Array(
    Math.max(3, Math.min(26, Math.ceil(Math.log2(count))))
  );
  for (let i = 0; i < code.length; i++) {
    code[i] = i;
  }
  shuffle(code);
  return [
    "Put the blocks in alphabetical order:",
    br(),
    dragOrder(
      {
        statusChanged: (input: number[]) => {
          if (input.every((item, index) => item == index)) {
            callback();
          }
        },
        statusFailed: (message, retry) => {},
        statusWaiting: () => {},
        reload: () => {},
      },
      (i) => String.fromCodePoint("A".codePointAt(0)! + i),
      ...code
    ),
  ];
}

/**
 * Display a panel that requires the user to enter a random sequence to affect the number of items provided. The sequence length is proportional to the number of items.
 */
export function checkRandomSequence(
  count: number,
  callback: () => void
): UIElement {
  const sequence = new Array(Math.max(1, Math.ceil(Math.log2(count)))).fill(0);
  for (let i = 0; i < sequence.length; i++) {
    if (i == 0) {
      sequence[i] = Math.floor(Math.random() * 9);
    } else {
      // Prevent duplicates
      const result = Math.floor(Math.random() * 8);
      if (result >= sequence[i - 1]) {
        sequence[i] = result + 1;
      } else {
        sequence[i] = result;
      }
    }
  }
  let index = 0;
  let { ui, model } = singleState((index: number) => [
    `Press ${sequence[index] + 1}`,
    br(),
    progress(index / sequence.length),
  ]);
  model.statusChanged(0);
  const indexButton = (value: number) =>
    button((value + 1).toString(), "", () => {
      if (index < sequence.length && value == sequence[index]) {
        index++;
        if (index < sequence.length) {
          model.statusChanged(index);
        } else {
          callback();
        }
      } else {
        index = 0;
        model.statusChanged(index);
      }
    });
  return tile(
    [],
    ui,
    br(),
    indexButton(0),
    indexButton(1),
    indexButton(2),
    br(),
    indexButton(3),
    indexButton(4),
    indexButton(5),
    br(),
    indexButton(6),
    indexButton(7),
    indexButton(8),
    br()
  );
}

/**
 * Remove all child nodes from an element
 */
function clearChildren(container: HTMLElement) {
  while (container.hasChildNodes()) {
    container.removeChild(container.lastChild!);
  }
}

/**
 * A collapsible section
 * @param title the name of the section
 * @param inner the contents of the section
 */
export function collapsible(
  title: DisplayElement,
  ...inner: UIElement[]
): UIElement {
  const result = collapsibleWithDefault(title, false, ...inner);
  return result ? result.ui : blank();
}
/**
 * A collapsible section
 * @param title the name of the section
 * @param inner the contents of the section
 */
export function collapsibleWithDefault(
  title: DisplayElement,
  openAtStart: boolean,
  ...inner: UIElement[]
): { ui: UIElement; model: StatefulModel<boolean> } | null {
  const contents = createUiFromTag("div", ...inner);
  if (!contents.element.hasChildNodes()) {
    return null;
  }
  const showHide = createUiFromTag("p", title);
  showHide.element.className = openAtStart
    ? "collapse expanded"
    : "collapse collapsed";
  showHide.element.addEventListener("click", (e) => {
    e.stopPropagation();
    const visible = showHide.element.classList.contains("collapsed");

    showHide.element.className = visible
      ? "collapse expanded"
      : "collapse collapsed";
  });
  return {
    ui: [showHide, contents],
    model: {
      reload: () => {},
      statusChanged: (input) => {
        showHide.element.className = input
          ? "collapse expanded"
          : "collapse collapsed";
      },
      statusFailed: (_message, _error) => {},
      statusWaiting: () => {},
    },
  };
}
const months: string[] = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/**
 * Create a date picker that can be disabled
 */
export function dateEditor(initial: number | null): {
  ui: UIElement;
  getter: () => number | null;
} {
  const enabled = inputCheckbox("Unbounded", !initial);
  const selected = typeof initial == "number" ? new Date(initial) : new Date();
  const year = inputNumber(selected.getFullYear(), 1970, null);
  const initialMonth: number = selected.getMonth();
  const monthModel = temporaryState(initialMonth);
  const month = dropdown(
    (m: number) => months[m],
    (m) => m == initialMonth,
    monthModel,
    null,
    ...months.keys()
  );
  const day = inputNumber(selected.getDate(), 1, 31);
  const hour = inputNumber(selected.getHours(), 0, 23);
  const minute = inputNumber(selected.getMinutes(), 0, 59);

  return {
    ui: [
      enabled.ui,
      br(),
      year.ui,
      month,
      day.ui,
      br(),
      hour.ui,
      ":",
      minute.ui,
    ],
    getter: () =>
      enabled.value
        ? null
        : new Date(
            year.value,
            monthModel.get(),
            day.value,
            hour.value,
            minute.value,
            0,
            0
          ).getTime(),
  };
}

/**
 * Show a dialog box with the provided contents
 *
 *  @param contents a callback to generate the contents of the dialog; it is given a function to close the dialog
 * @param afterClose an optional callback that will be invoked when the dialog is closed for any reason
 */

export function dialog(
  contents: (close: () => void) => UIElement,
  afterClose?: () => void
): void {
  const modal = document.createElement("div");
  modal.className = "modal dialog";

  const dialog = document.createElement("div");
  modal.appendChild(dialog);

  const closeButton = document.createElement("div");
  closeButton.innerText = "✖";

  dialog.appendChild(closeButton);

  const close = () => {
    if (modal.isConnected) {
      document.body.removeChild(modal);
    }
    if (afterClose) {
      afterClose();
    }
  };
  const inner = createUiFromTag("div", contents(close));
  dialog.appendChild(inner.element);

  document.body.appendChild(modal);
  modal.addEventListener("click", (e) => {
    if (e.target == modal) {
      if (modal.isConnected) {
        document.body.removeChild(modal);
      }
      e.stopPropagation();
      if (afterClose) {
        afterClose();
      }
    }
  });
  closeButton.addEventListener("click", close);
  inner.element.addEventListener("click", (e) => e.stopPropagation());
  if (inner.reveal) {
    inner.reveal();
  }
}
/**
 * Create a set of draggable blocks that can be reordered
 * @param model the output model to update with the current state
 * @param labelMaker a callback to determine what to display in the blocks
 * @param items the values to rearrange
 */
export function dragOrder<T>(
  model: StatefulModel<T[]>,
  labelMaker: (input: T) => DisplayElement,
  ...items: T[]
): UIElement {
  const container = singleState((input: T[]) => {
    let displays = input.map((item, index) => {
      const element = createUiFromTag("div", labelMaker(item));
      element.element.draggable = true;
      element.element.addEventListener("dragstart", (e) => {
        e.dataTransfer?.setData("moveposition", index.toString());
      });
      element.element.addEventListener("dragover", (e) => {
        e.stopPropagation();
        e.preventDefault();
        const onLeft =
          (e.pageX - parentalSum(element.element, (x) => x.offsetLeft)) /
            element.element.clientWidth <
          0.5;
        if (onLeft) {
          element.element.style.borderLeftColor = "var(--widget-highlight)";
          if (index == items.length - 1) {
            element.element.style.borderRightColor = "white";
          } else {
            displays[index + 1].element.style.borderLeftColor = "white";
          }
        } else {
          element.element.style.borderLeftColor = "white";
          if (index == items.length - 1) {
            element.element.style.borderRightColor = "var(--widget-highlight)";
          } else {
            displays[index + 1].element.style.borderLeftColor =
              "var(--widget-highlight)";
          }
        }
      });
      element.element.addEventListener("dragleave", (e) => {
        element.element.style.borderLeftColor = "white";
        element.element.style.borderRightColor = "white";
        if (index < items.length - 1) {
          displays[index + 1].element.style.borderLeftColor = "white";
        }
      });
      element.element.addEventListener("drop", (e) => {
        e.stopPropagation();
        let position = e.dataTransfer?.getData("moveposition");
        if (position) {
          const positionIndex = parseInt(position);
          if (positionIndex == index) {
            return;
          }
          const onLeft =
            (e.pageX - parentalSum(element.element, (x) => x.offsetLeft)) /
              element.element.clientWidth <
            0.5;
          if (positionIndex < index) {
            if (positionIndex == 0) {
              output.statusChanged(
                input
                  .slice(1, onLeft ? index : index + 1)
                  .concat([input[0]])
                  .concat(input.slice(onLeft ? index : index + 1))
              );
            } else {
              output.statusChanged(
                input
                  .slice(0, positionIndex)
                  .concat(
                    input.slice(positionIndex + 1, onLeft ? index : index + 1)
                  )
                  .concat([input[positionIndex]])
                  .concat(input.slice(onLeft ? index : index + 1))
              );
            }
          } else {
            output.statusChanged(
              input
                .slice(0, onLeft ? index : index + 1)
                .concat([input[positionIndex]])
                .concat(input.slice(onLeft ? index : index + 1, positionIndex))
                .concat(input.slice(positionIndex + 1))
            );
          }
        }
      });

      return element;
    });
    const list = createUiFromTag("div", displays);
    list.element.classList.add("dragorder");
    return list;
  });
  container.model.statusChanged(items);
  const output = combineModels(model, container.model);

  return container.ui;
}
/**
 * Create a drop down list
 *
 * This acts like a passive user input that gets read on demand.
 * @param labelMaker a function to produce a label the user will see for the label
 * @param model a model to manage this dropdown's state
 * @returns the UI element to change
 */
export function dropdown<T, S>(
  labelMaker: (input: T, selected: boolean) => DisplayElement,
  initial: ((item: T) => boolean) | null,
  model: StatefulModel<T>,
  synchronizer: {
    synchronizer: StateSynchronizer<S>;
    predicate: (recovered: S, item: T) => boolean;
    extract: (item: T) => S;
  } | null,
  ...items: T[]
): UIElement {
  const activeElement = pane("blank");
  const synchronizerCallbacks: ((state: S) => void)[] = [];
  const selectionModel = combineModels(
    model,
    mapModel(activeElement.model, (s) => labelMaker(s, true))
  );
  const container = createUiFromTag("span", activeElement.ui, " ▼");
  container.element.className = "dropdown";
  container.element.addEventListener(
    "click",
    popupMenu(
      true,
      ...items.map((item) => ({
        label: labelMaker(item, false),
        action: () => {
          selectionModel.statusChanged(item);
          if (synchronizer) {
            synchronizer.synchronizer.statusChanged(synchronizer.extract(item));
          }
        },
      }))
    )
  );
  const initialIndex =
    initial === null ? 0 : Math.max(items.findIndex(initial), 0);
  items.forEach((item, index) => {
    if (index == initialIndex) {
      selectionModel.statusChanged(item);
      if (synchronizer) {
        synchronizer.synchronizer.statusChanged(synchronizer.extract(item));
      }
    }
    synchronizerCallbacks.push((state) => {
      if (synchronizer?.predicate(state, item)) {
        selectionModel.statusChanged(item);
      }
    });
  });
  if (synchronizer) {
    synchronizer.synchronizer.listen((value) =>
      synchronizerCallbacks.forEach((callback) => callback(value))
    );
  }
  return container;
}
/**
 * Create a drop down table that can be filtered
 *
 * This acts like a passive user input that gets read on demand.
 * @param model a model to manage this dropdown's state
 * @param activeLabelMaker a function to produce a label the user will see for the label
 * @param searchPredicate a function that determines if the current user search keywords match an item
 * @returns the UI element to change
 */
export function dropdownTable<T, S>(
  model: StatefulModel<T | null>,
  synchronizer: {
    synchronzier: StateSynchronizer<S>;
    predicate: (recovered: S, item: T | null) => boolean;
    extract: (item: T | null) => S;
  } | null,
  activeLabelMaker: (input: T | null) => UIElement,
  searchPredicate: (input: T | null, keywords: string[]) => boolean,
  ...items: DropdownTableSection<T | null>[]
): UIElement {
  const activeElement = pane("blank");
  const synchronizerCallbacks: ((state: S) => void)[] = [];
  const selectionModel = combineModels(
    model,
    mapModel(activeElement.model, activeLabelMaker)
  );
  const container = createUiFromTag("span", activeElement.ui, " ▼");
  container.element.className = "dropdown";
  container.element.addEventListener(
    "click",
    popup(
      "tablemenu",
      true,
      (close) => {
        const searchFilters: ((keywords: string[]) => void)[] = [];
        return items.length == 0
          ? "No items."
          : [
              "Filter: ",
              inputSearch((input) => {
                const keywords = input
                  .toLowerCase()
                  .split(/\W+/)
                  .filter((s) => s);
                for (const searchFilter of searchFilters) {
                  searchFilter(keywords);
                }
              }),
              br(),
              items.map(({ value, label, children }) => {
                const groupLabel = createUiFromTag("p", label);
                groupLabel.element.addEventListener("click", (e) => {
                  e.stopPropagation();
                  selectionModel.statusChanged(value);
                  if (synchronizer) {
                    synchronizer.synchronzier.statusChanged(
                      synchronizer.extract(value)
                    );
                  }
                  close();
                });

                const block = createUiFromTag(
                  "div",
                  groupLabel,
                  children.length
                    ? tableFromRows(
                        children.map((child) => {
                          const row = tableRow(() => {
                            selectionModel.statusChanged(child.value);
                            if (synchronizer) {
                              synchronizer.synchronzier.statusChanged(
                                synchronizer.extract(child.value)
                              );
                            }
                            close();
                          }, ...child.label);
                          searchFilters.push((keywords) => {
                            row.element.style.display = searchPredicate(
                              child.value,
                              keywords
                            )
                              ? "block"
                              : "none";
                          });
                          synchronizerCallbacks.push((state) => {
                            if (synchronizer?.predicate(state, child.value)) {
                              selectionModel.statusChanged(child.value);
                            }
                          });
                          return row;
                        })
                      )
                    : blank()
                );
                searchFilters.push((keywords) => {
                  block.element.style.display =
                    searchPredicate(value, keywords) ||
                    children.some((child) =>
                      searchPredicate(child.value, keywords)
                    )
                      ? "block"
                      : "none";
                });
                synchronizerCallbacks.push((state) => {
                  if (synchronizer?.predicate(state, value)) {
                    selectionModel.statusChanged(value);
                  }
                });
                return block;
              }),
            ];
      },
      () => {}
    )
  );
  if (synchronizer) {
    const matches = items
      .flatMap((item) => [
        item.value,
        ...item.children.map((child) => child.value),
      ])
      .filter(
        (v) =>
          v != null &&
          synchronizer.predicate(synchronizer.synchronzier.get(), v)
      );
    if (matches.length) {
      selectionModel.statusChanged(matches[0]);
    } else {
      selectionModel.statusChanged(null);
    }
  } else {
    selectionModel.statusChanged(null);
  }
  if (synchronizer) {
    synchronizer.synchronzier.listen((value) =>
      synchronizerCallbacks.forEach((callback) => callback(value))
    );
  }
  return container;
}

/**
 * Create a group of elements with flexbox layout
 */
export function flexGroup(
  direction: "row" | "column",
  ...blocks: FlexElement[]
): UIElement {
  const element = createUiFromTag(
    "div",
    ...blocks.map(({ css, contents, width }) => {
      const x = createUiFromTag("div", contents);
      x.element.style.flex = width.toString();
      if (css) {
        for (const c of css) {
          x.element.classList.add(c);
        }
      }
      return x;
    })
  );
  element.element.style.display = "flex";
  element.element.style.flexDirection = direction;
  return element;
}
/**
 * Create a group of elements
 */
export function group(...contents: UIElement[]): UIElement {
  return createUiFromTag("span", ...contents);
}
/**
 * Create a group of elements
 */
export function groupWithFind(
  handler: FindHandler,
  ...contents: UIElement[]
): UIElement {
  const element = createUiFromTag("span", ...contents);
  if (element.find && handler) {
    const oldFind = element.find;
    element.find = () => handler() || oldFind();
  } else if (handler) {
    element.find = handler;
  }
  return element;
}
/**
 * Display items in section header
 */
export function header(title: string): UIElement {
  return createUiFromTag("h2", title);
}
/**
 * Create a section of UI that can be hidden or revealed
 * @param ui the UI elements to enclose
 * @returns a new UI element and a model that, when set to true, will show the inner UI elements
 */
export function hidden(...ui: UIElement[]): {
  ui: UIElement;
  model: StatefulModel<boolean>;
} {
  const container = createUiFromTag("span", ...ui);
  container.element.style.display = "none";
  return {
    ui: container,
    model: {
      reload: () => {},
      statusChanged: (input) => {
        container.element.style.display = input ? "inline" : "none";
      },
      statusFailed: (_message, _error) => {},
      statusWaiting: () => {},
    },
  };
}
/**
 * Create a section of UI that is connected to a model that cannot be null and is hidden when it is
 * @param input the UI element and model
 */
export function hiddenOnNull<T>(
  model: StatefulModel<T>,
  ...ui: UIElement[]
): { ui: UIElement; model: StatefulModel<T | null> } {
  const inner = hidden(...ui);
  return {
    ui: inner.ui,
    model: combineModels(
      filterModel(model, "Not available."),
      mapModel(inner.model, (input) => input !== null)
    ),
  };
}
/**
 * Create a way to synchronize the browser history with an object
 * @param url the URL to link back to
 * @param initial the state to use on start up
 * @param title a function to produce a title; this isn't displayed anywhere, but might be in the future according to Mozilla
 */
export function historyState<R extends keyof ShesmuLinks>(
  url: R,
  initial: ShesmuLinks[R],
  title: (input: ShesmuLinks[R]) => string
): StateSynchronizer<ShesmuLinks[R]> {
  let listener: StateListener<ShesmuLinks[R]> = null;
  let current = initial;
  window.addEventListener("popstate", (e) => {
    if (e.state) {
      current = e.state as ShesmuLinks[R];
      if (listener) {
        listener(current, false);
      }
    }
  });

  return {
    reload: () => {},
    statusChanged: (input: ShesmuLinks[R]) => {
      if (input != current) {
        current = input;
        window.history.pushState(
          { ...input },
          title(input),
          makeUrl(url, input)
        );
        if (listener) {
          listener(current, true);
        }
      }
    },
    statusFailed: (message, _retry) => console.log(message),
    statusWaiting: () => {},
    get(): ShesmuLinks[R] {
      return current;
    },
    listen: (newListener: StateListener<ShesmuLinks[R]>) => {
      listener = newListener;
      if (listener) {
        listener(current, false);
      }
    },
  };
}

/**
 * Create a line break
 */
export function hr(): UIElement {
  return createUiFromTag("hr");
}
/**
 * Create an indented block
 * @param children the elements to indent
 */
export function indented(...children: UIElement[]): UIElement {
  const ui = createUiFromTag("div", ...children);
  ui.element.className = "indent";
  return ui;
}

/**
 * Create an image
 */
export function img(src: string, className?: string): UIElement {
  const image = createUiFromTag("img");
  image.element.src = src;
  if (className) {
    image.element.className = className;
  }
  return image;
}
/**
 * Create a checkbox
 * @param label the text associated with the checkbox
 * @param initial the default state of the check box
 * @returns the UI element and a function to check the current check status
 */
export function inputCheckbox(
  label: string,
  initial: boolean
): InputField<boolean> {
  const labelElement = document.createElement("label");
  const checkbox = document.createElement("input");
  checkbox.type = "checkbox";
  checkbox.checked = initial;
  labelElement.appendChild(checkbox);
  labelElement.appendChild(document.createTextNode(label));
  return {
    ui: { element: labelElement, reveal: null, find: null, type: "ui" },
    get value() {
      return checkbox.checked;
    },
    set value(v) {
      checkbox.checked = v;
    },
    get enabled() {
      return !checkbox.disabled;
    },
    set enabled(v) {
      checkbox.disabled = !v;
    },
  };
}
/**
 * Create a search input box.
 */
export function inputSearch(updateHandler: (input: string) => void): UIElement {
  const input = createUiFromTag("input");
  input.element.type = "search";
  input.element.addEventListener("input", () => {
    updateHandler(input.element.value.trim());
  });

  return input;
}
export function inputSearchBar(
  title: string,
  initial: string,
  model: StatefulModel<string>
): [InputField<string>, StatefulModel<SearchBarState>] {
  const input = createUiFromTag("input");
  let lastState: SearchBarState = "dirty";
  let lastValue = initial;
  input.element.type = "text";
  input.element.value = initial;
  input.element.style.width = "100%";
  input.element.addEventListener("keyup", (e) => {
    if (input.element.value == lastValue) {
      input.element.className = lastState;
    } else {
      input.element.className = "dirty";
    }
    if (e.key == "Enter") {
      lastValue = input.element.value;
      lastState = "dirty";
      model.statusChanged(input.element.value);
    }
  });

  return [
    {
      ui: flexGroup(
        "row",
        { css: ["rigid"], contents: title, width: 0 },
        { contents: input, width: 5 },
        {
          css: ["rigid"],
          contents: { type: "icon", icon: "arrow-return-left" },
          width: 0,
        }
      ),
      get value() {
        return input.element.value;
      },
      set value(v) {
        input.element.value = v;
      },
      get enabled() {
        return !input.element.disabled;
      },
      set enabled(v) {
        input.element.disabled = !v;
      },
    },
    {
      reload: () => {},
      statusChanged: (state) => {
        input.element.className = state;
        lastState = state;
      },
      statusFailed: (_message, _error) => (input.element.className = "bad"),
      statusWaiting: () => (input.element.className = "busy"),
    },
  ];
}
export function inputNumber(
  value: number,
  min: number,
  max: number | null
): InputField<number> {
  const input = createUiFromTag("input");
  input.element.type = "number";
  input.element.min = min.toString();
  if (max) {
    input.element.max = max.toString();
  }
  input.element.value = value.toString();
  return {
    ui: input,
    get value() {
      return input.element.valueAsNumber;
    },
    set value(v) {
      input.element.value = v.toString();
    },
    get enabled() {
      return !input.element.disabled;
    },
    set enabled(v) {
      input.element.disabled = !v;
    },
  };
}

/**
 * Create a text input box.
 */
export function inputText(initial?: string): InputField<string> {
  const input = createUiFromTag("input");
  input.element.type = "text";
  if (initial) {
    input.element.value = initial;
  }
  return {
    ui: input,
    get value() {
      return input.element.value;
    },
    set value(v) {
      input.element.value = v;
    },
    get enabled() {
      return !input.element.disabled;
    },
    set enabled(v) {
      input.element.disabled = !v;
    },
  };
}
/**
 * Create a big text input box.
 */
export function inputTextArea(initial?: string): InputField<string> {
  const input = createUiFromTag("textarea");
  if (initial) {
    input.element.value = initial;
  }
  return {
    ui: input,
    get value() {
      return input.element.value;
    },
    set value(v) {
      input.element.value = v;
    },
    get enabled() {
      return !input.element.disabled;
    },
    set enabled(v) {
      input.element.disabled = !v;
    },
  };
}

/**
 * A UI element that tiggers a regular update
 *
 * It keeps a counter and updates the counter every time it is triggered. If
 * the UI element is removed, the model will no longer be updated.
 * @param period the length of time between updates in milliseconds
 * @param model the model to update with the current count
 * @param refreshLabel if provided, this will create a refresh button for manually triggering an update; if a synchronizer is provided the button can also be used to enable or disable the trigger
 */
export function intervalCounter(
  period: number,
  model: StatefulModel<number>,
  refreshLabel: {
    label: DisplayElement;
    title: string;
    synchronizer: StateSynchronizer<boolean> | null;
  } | null
): UIElement {
  const ui = createUiFromTag("span");
  let counter = 0;
  let handle: number | null = null;
  const update = () => {
    clearChildren(ui.element);
    addElements(ui.element, throbberSmall());
    window.setTimeout(() => clearChildren(ui.element), 500);
    model.statusChanged(counter++);
    if (handle !== null) {
      window.clearInterval(handle);
      handle = window.setInterval(() => {
        if (ui.element.isConnected) {
          update();
        } else {
          setup(false);
        }
      }, period);
    }
  };
  const setup = (enabled: boolean) => {
    if (handle === null && enabled) {
      handle = window.setInterval(() => {
        if (ui.element.isConnected) {
          update();
        } else {
          setup(false);
        }
      }, period);
    } else if (handle !== null && !enabled) {
      window.clearInterval(handle);
      clearChildren(ui.element);
      handle = null;
    }
  };
  let first = true;
  ui.reveal = () => {
    if (first) {
      update();
      first = false;
    }
  };
  if (refreshLabel) {
    if (refreshLabel.synchronizer) {
      const synchronizer = refreshLabel.synchronizer;
      const { model, ui: settingsUi } = singleState((enabled) =>
        buttonAccessory(
          [refreshLabel.label, " ▼"],
          refreshLabel.title,
          popupMenu(
            true,
            enabled
              ? {
                  label: [
                    { type: "icon", icon: "x-circle" },
                    "Disable Auto Refresh",
                  ],
                  action: () => synchronizer.statusChanged(false),
                }
              : {
                  label: [
                    { type: "icon", icon: "check-circle" },
                    "Enable Auto Refresh",
                  ],
                  action: () => synchronizer.statusChanged(true),
                },

            {
              label: [{ type: "icon", icon: "arrow-repeat" }, "Refresh Now"],
              action: update,
            }
          )
        )
      );
      refreshLabel.synchronizer.listen((enabled) => {
        model.statusChanged(enabled);
        setup(enabled);
      });
      return [settingsUi, ui];
    } else {
      setup(true);
      return [
        buttonAccessory(refreshLabel.label, refreshLabel.title, update),
        ui,
      ];
    }
  } else {
    setup(true);
    return ui;
  }
}
/**
 * Display some italic text.
 */
export function italic(text: string): DisplayElement {
  return { type: "i", contents: text };
}

/**
 * Show an an action with a `parameters` object as a table of key-value pairs with the values being formatted as human-friendly JSON.
 */
export function jsonParameters(action: { parameters: object }): UIElement {
  return objectTable(action.parameters, "Parameters", (x: any) =>
    preformatted(JSON.stringify(x, null, 2))
  );
}

/**
 * Create a clickable legend
 *
 * Each element can be individually activated by clicking the legend.
 */
export function legend<T>(
  model: StatefulModel<{ value: T; colour: string }[]>,
  renderer: (input: T) => DisplayElement,
  elements: T[],
  disabledColour: string,
  colours: string[]
): UIElement {
  let selected: Set<number> = new Set();
  let container = createUiFromTag(
    "div",
    ...elements.map((value, index) => {
      const colour = colours[index % colours.length];
      const tile = createUiFromTag("span", renderer(value));
      tile.element.style.color = disabledColour;
      tile.element.addEventListener("click", (e) => {
        e.stopPropagation();
        if (selected.has(index)) {
          selected.delete(index);
          tile.element.style.color = "#aaa";
        } else {
          selected.add(index);
          tile.element.style.color = colour;
        }
        model.statusChanged(
          Array.from(selected).map((index) => ({
            value: elements[index],
            colour: colours[index % colours.length],
          }))
        );
      });
      return tile;
    })
  );
  container.element.classList.add("legend");
  return container;
}

/**
 * Create a hyperlink
 * @param url the target of the hyperlink
 * @param contents the label for the link
 * @param title an optional tooltip
 */
export function link(
  url: string,
  contents: string | number,
  title?: string
): DisplayElement {
  return { type: "a", url, contents: contents.toString(), title: title || "" };
}
/**
 * Create a URL with query parameters
 * @param url the base URL
 * @param parameters the parameters to supply; they will be JSON-encoded
 */
export function makeUrl<R extends keyof ShesmuLinks>(
  url: R,
  parameters: ShesmuLinks[R]
): string {
  return (
    url +
    "?" +
    Object.entries(parameters)
      .map(
        ([key, value]) =>
          key +
          "=" +
          btoa(JSON.stringify(value))
            .replace(/=/g, "")
            .replace(/\+/g, "-")
            .replace(/\//g, "_")
      )
      .join("&")
  );
}
/**
 * Display some monospaced text.
 */
export function mono(text: string): DisplayElement {
  return { type: "tt", contents: text };
}
/**
 * This create multiple panels with some shared state that can be updated
 * @param primary the main panel that should display error notification
 * @param formatters a collection of callback that will be called to update the contents of the panes when the state changes
 * @param silentOnChange if a panel name is listed here, it will be blank when updating/showing an error. This is useful for toolbars
 */
export function multipaneState<
  T,
  F extends { [name: string]: (input: T) => UIElement }
>(
  primary: keyof F | null,
  formatters: F,
  ...silentOnChange: (keyof F)[]
): { model: StatefulModel<T>; components: NamedComponents<F> } {
  const models: StatefulModel<T>[] = [];
  const panes = Object.fromEntries(
    Object.entries(formatters).map(([name, formatter], index) => {
      const { ui, model } = pane(
        silentOnChange.includes(name)
          ? "blank"
          : (primary == null ? index == 0 : name == primary)
          ? "large"
          : "small"
      );
      models.push(mapModel(model, (input) => formatter(input)));
      return [name, ui];
    })
  ) as NamedComponents<F>;

  return {
    model: combineModels(...models),
    components: panes,
  };
}

/**
 * Display an object as a table of key-value pairs with a custom value display format
 * @param object the object to display
 * @param title the label for the table
 * @param valueFormatter a function to display the values in the table
 */
export function objectTable<T>(
  object: { [propertyName: string]: T },
  title: string,
  valueFormatter: (value: T) => UIElement
) {
  return collapsible(
    title,
    table(
      Object.entries(object).sort((a: [string, T], b: [string, T]) =>
        a[0].localeCompare(b[0])
      ),
      ["Name", (x: [string, T]) => x[0]],
      ["Value", (x: [string, T]) => valueFormatter(x[1])]
    )
  );
}

/**
 * Create a selection of numbered buttons for a pager
 */
export function pager(
  numButtons: number,
  current: number,
  drawPager: (index: number) => void
): UIElement {
  if (numButtons < 2) {
    return blank();
  }
  const blocks: FlexElement[] = [];
  let rendering = true;
  blocks.push({
    contents:
      current > 0
        ? buttonAccessory("< Previous", "", () => drawPager(current - 1))
        : buttonDisabled("< Previous", ""),
    width: 2,
  });
  let scoringScheme: (n: number) => boolean;
  // To make the layout consistent, we need to have every permutation show 15 units, where numbers are 1 and an ellipsis is 2. Remember the number is zero-based but the buttons are 1-based.
  if (numButtons < 15) {
    // If the total is less than 13, just show everything
    scoringScheme = (_n) => true;
  } else if (current < 9) {
    // Should display as 1 2 3 4 5 6 7 8 9 10 ... N-2 N-1 N
    scoringScheme = (n) => n < 10 || n >= numButtons - 3;
  } else if (current > numButtons - 9) {
    // Should display as 1 2 3 ... N-9 N-8 N-7 N-6 N-5 N-4 N-3 N-2 N-1 N
    scoringScheme = (n) => n < 3 || n >= numButtons - 9;
  } else {
    // Should display as 1 2 3 ... C-2 C-1 C C+1 C+2 ... N-2 N-1 N
    scoringScheme = (n) =>
      n < 3 || (n >= current - 2 && n <= current + 2) || n >= numButtons - 3;
  }

  const maxDigits = Math.ceil(Math.log10(numButtons + 2));

  for (let i = 0; i < numButtons; i++) {
    if (scoringScheme(i)) {
      const index = i;
      rendering = true;
      // Left pad the label with figure space
      const label = `${" ".repeat(
        Math.max(0, maxDigits - Math.ceil(Math.log10(index + 2)))
      )}${index + 1}`;
      blocks.push({
        contents:
          index == current
            ? buttonDisabled(label, "")
            : buttonAccessory(label, "", () => drawPager(index)),
        width: 1,
      });
    } else if (rendering) {
      blocks.push({ contents: "...", width: 2 });
      rendering = false;
    }
  }
  blocks.push({
    contents:
      current < numButtons - 1
        ? buttonAccessory("Next >", "", () => drawPager(current + 1))
        : buttonDisabled("Next >", ""),
    width: 2,
  });
  return flexGroup("row", ...blocks);
}
/**
 * Create a paginated list of downloadable data
 * @param filename the name to use for downloading
 * @param data the total set of data to use
 * @param render a function to render a subset of the data that is provided
 * @param predicate a function to determine if an entry matches keywords the user has entered in the filter box
 */
export function paginatedList<T>(
  filename: string,
  data: T[],
  render: (items: T[]) => UIElement,
  predicate: (item: T, keywords: string[]) => boolean
): UIElement {
  let condition = (x: T) => true;
  const { ui, model } = pane("blank");
  const showData = () => {
    const selectedData = data.filter(condition);
    const numPerPage = 10;
    const numButtons = Math.ceil(selectedData.length / numPerPage);
    const drawPager = (current: number) => {
      model.statusChanged([
        pager(numButtons, current, drawPager),
        render(
          selectedData.slice(current * numPerPage, (current + 1) * numPerPage)
        ),
      ]);
    };
    drawPager(0);
  };
  showData();
  return group(
    group(
      button(
        [{ type: "icon", icon: "download" }, "Download"],
        "Download data as a file.",
        () => {
          saveFile(JSON.stringify(data, null, 2), "application/json", filename);
        }
      ),
      button(
        [{ type: "icon", icon: "download" }, "Download Selected"],
        "Download filtered data as a file.",
        () => {
          saveFile(
            JSON.stringify(data.filter(condition), null, 2),
            "application/json",
            filename
          );
        }
      ),
      " Filter: ",
      inputSearch((search) => {
        const keywords = search.toLowerCase().split(/\W+/);
        if (keywords.length) {
          condition = (x) => predicate(x, keywords);
        } else {
          condition = (x) => true;
        }
        showData();
      })
    ),
    ui
  );
}
/**
 * Create a mutable section of UI.
 */
export function pane(mode: "blank" | "small" | "medium" | "large"): {
  ui: ComplexElement<HTMLElement>;
  model: StatefulModel<UIElement>;
} {
  const element = document.createElement("span");
  let find: FindHandler = null;
  let reveal: (() => void) | null = null;
  const update = (input: UIElement) => {
    clearChildren(element);
    const result = addElements(element, input);
    find = result.find;
    reveal = result.reveal;
    if (reveal) {
      reveal();
    }
  };
  return {
    ui: {
      element: element,
      find: () => (find ? find() : false),
      reveal: () => {
        if (reveal) {
          reveal();
        }
      },
      type: "ui",
    },
    model: {
      reload: () => {},
      statusChanged: update,
      statusWaiting: () => {
        switch (mode) {
          case "blank":
            update(blank());
            break;
          case "small":
          case "medium":
            update(throbberSmall());
            break;
          case "large":
            update(throbber());
            break;
        }
      },
      statusFailed: (message: string, retry: (() => void) | null) => {
        switch (mode) {
          case "blank":
            update(blank());
            break;
          case "small":
            update(img("dead.svg", "deadolive"));
            break;
          case "medium":
          case "large":
            update([
              img("dead.svg", "deadolive"),
              text(message),
              retry
                ? button("Retry", "Attempt operation again.", retry)
                : blank(),
            ]);
            break;
        }
      },
    },
  };
}
/**
 * Display items in a paragraph node
 */
export function paragraph(...contents: DisplayElement[]): DisplayElement {
  return { type: "p", contents: contents };
}

function parentalSum(
  child: HTMLElement,
  extract: (input: HTMLElement) => number
): number {
  let sum = 0;
  for (
    let current: HTMLElement | null = child;
    current != null;
    current = current.offsetParent as HTMLElement
  ) {
    sum += extract(current);
  }
  return sum;
}

/**
 * Create a click handler that will show a pop up menu
 * @param underOwner if true, the pop up menu will appear under the widget that it is connected to; if false, the menu will appear at the position where the user clicked.
 * @param items the items to be shown in the menu
 */
export function popup(
  className: string,
  underOwner: boolean,
  populate: (close: () => void) => UIElement,
  afterClose: () => void
): ClickHandler {
  return (e) => {
    e.stopPropagation();
    const modal = document.createElement("div");
    modal.className = "capture";
    document.body.appendChild(modal);
    const inner = createUiFromTag(
      "div",
      populate(() => {
        if (modal.isConnected) {
          document.body.removeChild(modal);
        }
        if (inner.element.isConnected) {
          document.body.removeChild(inner.element);
        }
        afterClose();
      })
    );
    inner.element.className = className;
    document.body.appendChild(inner.element);
    const { x, y } =
      underOwner && e.currentTarget instanceof HTMLElement
        ? {
            x: parentalSum(e.currentTarget, (x) => x.offsetLeft),
            y:
              parentalSum(e.currentTarget, (x) => x.offsetTop) +
              e.currentTarget.offsetHeight,
          }
        : { x: e.pageX, y: e.pageY };
    // The simplest thing to do would be put the menu at the point of click
    // or under the appropriate other item. However, if the menu would
    // extend past the edge of the page, triggering new scroll bars, which
    // is annoying and may change the page layout, now putting the menu is
    // slightly the wrong place. Therefore, this code tries to shift the
    // menu to always appear inside the page. There are two coordinate
    // systems `offset`, relative to the page, and `client` relative to the
    // window. When the page is longer than the window, a menu calculation
    // using `client` will stick the menu up in the top chunk of the page
    // (whatever the first scrollable unit is), which is not helpful. If the
    // `offset` is used, then when the page is mostly empty, it doesn't
    // occupy the full `clientHeight`, and show the menu gets pushed up off
    // the top of the screen. In a way, this was the case we were trying to
    // avoid earlier (expanding the length of the page). However, in this
    // case, we know we can extend the height of the page safely because it
    // is less than the height of the window. So, the correct calculation is
    // determine where to place the menu relative to the longer of the page
    // (`offset`) or window (`client`).
    inner.element.style.left = `${Math.min(
      x,
      Math.max(document.body.offsetWidth, document.body.clientWidth) -
        inner.element.offsetWidth -
        10
    )}px`;
    inner.element.style.top = `${Math.min(
      y,
      Math.max(document.body.offsetHeight, document.body.clientHeight) -
        inner.element.offsetHeight -
        10
    )}px`;

    inner.element.addEventListener("click", (e) => e.stopPropagation());
    modal.addEventListener("click", (e) => {
      e.stopPropagation();
      if (modal.isConnected) {
        document.body.removeChild(modal);
      }
      if (inner.element.isConnected) {
        document.body.removeChild(inner.element);
      }
      afterClose();
    });
    if (inner.reveal) {
      inner.reveal();
    }
  };
}
/**
 * Create a click handler that will show a pop up menu
 * @param underOwner if true, the pop up menu will appear under the widget that it is connected to; if false, the menu will appear at the position where the user clicked.
 * @param items the items to be shown in the menu
 */
export function popupMenu(
  underOwner: boolean,
  ...items: { label: DisplayElement; action: () => void }[]
): ClickHandler {
  return popup(
    "menu",
    underOwner,
    (close) =>
      items.map(({ label, action }) =>
        buttonIcon(label, "", () => {
          close();
          action();
        })
      ),
    () => {}
  );
}

/**
 * Display preformatted text.
 */
export function preformatted(text: string): UIElement {
  const pre = createUiFromTag("pre", text);
  pre.element.style.overflowX = "auto";
  return pre;
}
/**
 * Create a standard refresh button
 */
export function refreshButton(callback: () => void): UIElement {
  return button(
    [{ type: "icon", icon: "arrow-repeat" }, "Refresh"],
    "Update current view with new data from server.",
    callback
  );
}

/**
 * Set up the global state for a dashboard
 *
 * This should only be called once per page execution
 * @param container the existing HTML node that will host the dashboard
 * @param contents the widgets that make up the dashboard
 */
export function setRootDashboard(
  container: HTMLElement | string,
  ...elements: UIElement[]
) {
  let findOverride: FindHandler = null;
  window.addEventListener("keydown", (e) => {
    if (
      findOverride &&
      (e.key == "F3" || e.key == "Find" || checkKey(e, "f")) &&
      findOverride()
    ) {
      e.preventDefault();
    }
  });

  let root = createUiFromTag("div", ...elements);
  (typeof container == "string"
    ? document.getElementById(container)!
    : container
  ).appendChild(root.element);
  findOverride = root.find;
  if (root.reveal) {
    root.reveal();
  }
}
/** Create a dialog of buttons that can be multi-selected and filtered
 * @param items the items to display
 * @param setItems a callback for the selected output
 * @param render generate a label and tooltop for each item
 * @param predicated a function to determine if an item matches the search filter
 * @param breakLines whether each button should appear on a separated lines
 */
export function pickFromSet<T>(
  items: readonly T[],
  setItems: (results: T[]) => void,
  render: (item: T) => { label: DisplayElement; title: string },
  predicate: (item: T, keywords: string[]) => boolean,
  breakLines: boolean,
  ...extra: DisplayElement[]
) {
  pickFromSetCustom(
    items,
    setItems,
    (item, click) => {
      const { label, title } = render(item);
      return button(label, title, click);
    },
    predicate,
    breakLines,
    ...extra
  );
}
/** Create a dialog of buttons that can be multi-selected and filtered
 * @param items the items to display
 * @param setItems a callback for the selected output
 * @param render generate a button for each item
 * @param predicated a function to determine if an item matches the search filter
 * @param breakLines whether each button should appear on a separated lines
 */
export function pickFromSetCustom<T>(
  items: readonly T[],
  setItems: (results: T[]) => void,
  render: ActiveItemRenderer<T>,
  predicate: (item: T, keywords: string[]) => boolean,
  breakLines: boolean,
  ...extra: DisplayElement[]
) {
  dialog((close) => {
    let selected: T[] = [];
    const { ui, model } = singleState<T[]>((items) =>
      items.length
        ? items.map((item) => {
            return [
              render(item, (e) => {
                selected.push(item);
                if (!e.ctrlKey) {
                  setItems(selected);
                  e.stopPropagation();
                  close();
                } else {
                  selectedModel.statusChanged(selected);
                }
              }),
              breakLines ? br() : blank(),
            ];
          })
        : "No matches."
    );
    const { ui: selectedUi, model: selectedModel } = singleState<T[]>((items) =>
      items.length
        ? [
            "Going to add:",
            br(),
            items.map((item) => {
              return [
                render(item, (e) => {
                  selected = selected.filter((i) => i != item);
                  selectedModel.statusChanged(selected);
                }),
                breakLines ? br() : blank(),
              ];
            }),
            br(),
            buttonAccessory(
              [{ type: "icon", icon: "plus-circle-fill" }, "Add Selected"],
              "Add selected items.",
              () => {
                setItems(selected);
                close();
              }
            ),
            buttonAccessory(
              [{ type: "icon", icon: "backspace" }, "Clear Selected"],
              "Clear selected items.",
              () => {
                selected = [];
                selectedModel.statusChanged([]);
                close();
              }
            ),
          ]
        : blank()
    );

    const showItems = (p: (input: T) => boolean) =>
      model.statusChanged(
        items.filter((item) => selected.indexOf(item) == -1).filter(p)
      );

    showItems((x) => true);

    return [
      createUiFromTag(
        "p",
        "Filter: ",
        inputSearch((search) => {
          const keywords = search
            .trim()
            .toLowerCase()
            .split(/\W+/)
            .filter((x) => x);
          if (keywords.length) {
            showItems((x) => predicate(x, keywords));
          } else {
            showItems((x) => true);
          }
        })
      ),
      extra,
      br(),
      ui,
      paragraph("Control-click to select multiple."),
      selectedUi,
    ];
  });
}

/**
 * Create a progress bar
 * @param fraction the completion between 0 and 1.
 */
export function progress(fraction: number): UIElement {
  const inner = createUiFromTag("div");
  inner.element.style.width = `${Math.min(100, Math.max(0, fraction * 100))}%`;
  const outer = createUiFromTag("div", inner);
  outer.element.className = "progress";
  return outer;
}
/**
 * Create a set of radio buttons that can be turned off
 * @param inactiveLabel the label when a button is not active
 * @param activeLabel the label when when one of the button is active
 * @param model the model to update with the selected button or null if none are active
 * @param values the values
 * @returns the button corresponding to each value
 */
export function radioSelector<T>(
  inactiveLabel: DisplayElement,
  activeLabel: DisplayElement,
  model: StatefulModel<T | null>,
  ...values: T[]
): UIElement[] {
  let active: ComplexElement<HTMLSpanElement> | null = null;
  return values.map((value) => {
    const button = createUiFromTag("span", inactiveLabel);
    button.element.classList.add("button");
    button.element.classList.add("accessory");
    button.element.addEventListener("click", (e) => {
      e.stopPropagation();
      if (active == button) {
        active = null;
        button.element.classList.remove("active");
        clearChildren(button.element);
        addElements(button.element, inactiveLabel);
        model.statusChanged(null);
      } else {
        if (active != null) {
          active.element.classList.remove("active");
          clearChildren(active.element);
          addElements(active.element, inactiveLabel);
        }
        button.element.classList.add("active");
        clearChildren(button.element);
        addElements(button.element, activeLabel);
        active = button;
        model.statusChanged(value);
      }
    });
    return button;
  });
}

/**
 * Compare two arbitrary JSON objects to create a table that explains their differences
 */
export function recursiveDifferences(
  leftName: string,
  left: any,
  rightName: string,
  right: any
): UIElement {
  const rows = recursiveDifferencesHelper("Root", left, right);
  if (rows.length == 0) {
    return blank();
  } else {
    rows.unshift(
      tableRow(
        null,
        { contents: "Location" },
        { contents: leftName },
        { contents: rightName }
      )
    );
    return tableFromRows(rows);
  }
}
function recursiveDifferencesHelper(
  path: string,
  left: any,
  right: any
): ComplexElement<HTMLTableRowElement>[] {
  if (typeof left == typeof right) {
    switch (typeof left) {
      case "string":
      case "boolean":
      case "number":
        if (left == right) {
          return [];
        }
        break;

      case "object":
        if (left == null && right == null) {
          return [];
        } else if (left == null || right == null) {
          break;
        } else if (Array.isArray(left) && Array.isArray(right)) {
          let rows: ComplexElement<HTMLTableRowElement>[] = [];
          let i;
          for (i = 0; i < Math.min(left.length, right.length); i++) {
            rows = rows.concat(
              recursiveDifferencesHelper(`${path}[${i}]`, left[i], right[i])
            );
          }
          for (; i < left.length; i++) {
            rows.push(
              tableRow(
                null,
                { contents: `${path}[${i}]` },
                { contents: preformatted(JSON.stringify(left[i], null, 2)) },
                { contents: "Missing" }
              )
            );
          }
          for (; i < right.length; i++) {
            rows.push(
              tableRow(
                null,
                { contents: `${path}[${i}]` },
                { contents: "Missing" },
                { contents: preformatted(JSON.stringify(right[i], null, 2)) }
              )
            );
          }
          return rows;
        } else if (!Array.isArray(left) && !Array.isArray(right)) {
          let rows: ComplexElement<HTMLTableRowElement>[] = [];
          let keys = new Set(Object.keys(left).concat(Object.keys(right)));
          for (const key of keys) {
            if (left.hasOwnProperty(key) && right.hasOwnProperty(key)) {
              rows = rows.concat(
                recursiveDifferencesHelper(
                  `${path}.${key}`,
                  left[key],
                  right[key]
                )
              );
            } else if (left.hasOwnProperty(key)) {
              rows.push(
                tableRow(
                  null,
                  { contents: `${path}.${key}` },
                  {
                    contents: preformatted(JSON.stringify(left[key], null, 2)),
                  },
                  { contents: "Missing" }
                )
              );
            } else {
              rows.push(
                tableRow(
                  null,
                  { contents: `${path}.${key}` },
                  { contents: "Missing" },
                  {
                    contents: preformatted(JSON.stringify(right[key], null, 2)),
                  }
                )
              );
            }
          }
          return rows;
        }
        break;
      default:
        return [
          tableRow(
            null,
            { contents: path },
            { contents: `Cannot compare ${typeof left}` },
            { contents: "N/A" }
          ),
        ];
    }
  }
  return [
    tableRow(
      null,
      { contents: path },
      { contents: preformatted(JSON.stringify(left, null, 2)) },
      { contents: preformatted(JSON.stringify(right, null, 2)) }
    ),
  ];
}

/**
 * Make text that makes whitespace visible
 */
export function revealWhitespace(text: string): string {
  return String(text).replace("\n", "⏎").replace("\t", "⇨").replace(/\s/, "␣");
}

/**
 * This create multiple panels with a single shared state that can be updated and this regenerates all the panels
 * @param primary the main panel that should display error notification
 * @param formatters a collection of callback that will be called to update the contents of the panes when the state changes
 * @param keys all the keys provided by the formatter
 */
export function sharedPane<T, F extends { [name: string]: UIElement }>(
  primary: keyof F | null,
  formatter: (input: T) => F,
  ...keys: (keyof F)[]
): { model: StatefulModel<T>; components: NamedComponents<F> } {
  const models: StatefulModel<F>[] = [];
  const panes = Object.fromEntries(
    keys.map((name, index) => {
      const { ui, model } = pane(
        (primary === null ? index == 0 : name == primary) ? "large" : "small"
      );
      models.push(mapModel(model, (input) => input[name]));
      return [name, ui];
    })
  ) as NamedComponents<F>;

  return {
    model: mapModel(combineModels(...models), formatter),
    components: panes,
  };
}
/**
 * Creating a floating side panel
 * @param side the floating panel
 * @param body the main content
 */
export function sidepanel(side: UIElement, body: UIElement): UIElement {
  const sidePanel = createUiFromTag(
    "div",
    createUiFromTag("div", side),
    createUiFromTag("div", body)
  );
  sidePanel.element.className = "sidepanel";
  return sidePanel;
}
/**
 * This create single panels with some shared state that can be updated
 * @param formatter a callback that will be called to update the contents of the pane when the state changes
 */
export function singleState<T>(
  formatter: (input: T) => UIElement,
  hideErrors?: boolean
): { model: StatefulModel<T>; ui: UIElement } {
  const { ui, model } = pane(hideErrors ? "blank" : "medium");
  return {
    model: mapModel(model, formatter),
    ui: ui,
  };
}

/**
 * Create a little read circle with a number in it
 * @param title a callback to generate a tooltip for the current count
 */
export function spotCounter(title: (input: number) => string): {
  ui: UIElement;
  model: StatefulModel<number>;
} {
  const element = createUiFromTag("span");
  element.element.className = "spot";
  return {
    ui: element,
    model: {
      reload: () => {},
      statusChanged: (input) => {
        element.element.className = input ? "spot" : "nospot";
        element.element.innerText = input.toString();
        element.element.title = title(input);
      },
      statusFailed: (message, _retry) => {
        element.element.className = "spot";
        element.element.innerText = "?";
        element.element.title = message;
      },
      statusWaiting: () => {
        element.element.className = "spot";
        element.element.innerText = "";
        element.element.title = "Count is being refreshed";
      },
    },
  };
}

/**
 * Create a list that can be updated and push that into a model
 */
export function statefulList<T>(model: StatefulModel<T[]>): UpdateableList<T> {
  let list: T[] = [];
  return {
    add(item: T): void {
      list = list.concat([item]);
      model.statusChanged(list);
    },
    keepOnly(predicate: (item: T) => boolean): void {
      list = list.filter(predicate);
      model.statusChanged(list);
    },
    replace(items: T[]): void {
      list = [...items];
      model.statusChanged(list);
    },
  };
}

/**
 * Create a stateful list connected to a state synchronizer.
 */
export function statefulListBind<T>(synchronizer: StateSynchronizer<T[]>): {
  list: UpdateableList<T>;
  register: (model: StatefulModel<T[]>) => void;
} {
  let model: StatefulModel<T[]> | null = null;
  let list: T[] = [...synchronizer.get()];
  synchronizer.listen((x, internal) => {
    if (x != list && !internal) {
      list = x;
      if (model) {
        model.statusChanged(list);
      }
    }
  });
  return {
    list: {
      add(item: T): void {
        list = list.concat([item]);
        if (model) {
          model.statusChanged(list);
        }
        synchronizer.statusChanged(list);
      },
      keepOnly(predicate: (item: T) => boolean): void {
        list = list.filter(predicate);
        if (model) {
          model.statusChanged(list);
        }
        synchronizer.statusChanged(list);
      },
      replace(items: T[]): void {
        list = [...items];
        if (model) {
          model.statusChanged(list);
        }
        synchronizer.statusChanged(list);
      },
    },
    register: (newModel) => {
      model = newModel;
      newModel.statusChanged(list);
    },
  };
}

/**
 * Display text with an optional like through it
 */
export function strikeout(
  strike: boolean,
  contents: string | number
): DisplayElement {
  const text = `${contents}`.replace(/\n/g, "⏎");
  return strike ? { type: "s", contents: text } : text;
}
/**
 * Allow synchronising the fields in an object separately
 */
export function synchronizerFields<
  T extends { [name: string]: any },
  K extends keyof T
>(parent: StateSynchronizer<T>): SynchronizedFields<T> {
  let listeners: NonNullable<StateListener<T>>[] = [];
  parent.listen((x, internal) => {
    for (const listener of listeners) {
      listener(x, internal);
    }
  });
  return Object.fromEntries(
    Object.keys(parent.get()).map((key) => {
      let currentListener: StateListener<T[K]> = null;
      listeners.push((input: T, internal: boolean) => {
        if (currentListener) {
          currentListener(input[key], internal);
        }
      });
      return [
        key,
        {
          reload: () => {},
          statusChanged(state: T[K]): void {
            parent.statusChanged({ ...parent.get(), [key]: state });
          },
          statusWaiting(): void {},
          statusFailed(message: string, _retry: () => void): void {
            console.log(message);
          },
          get(): T[K] {
            return parent.get()[key];
          },
          listen(listener: StateListener<T[K]>): void {
            currentListener = listener;
            if (currentListener) {
              currentListener(parent.get()[key], false);
            }
          },
        } as StateSynchronizer<T[K]>,
      ];
    })
  ) as unknown as SynchronizedFields<T>;
}

/**
 * Create a stateful panel connected to an existing publisher
 */
export function subscribedState<T>(
  initial: T,
  publisher: Publisher<T>,
  formatter: (input: T) => UIElement
): UIElement {
  const { ui, model } = pane("blank");
  model.statusChanged(formatter(initial));
  publisher.subscribe({
    ...mapModel(model, formatter),
    get isAlive() {
      return ui.element.isConnected;
    },
  });
  return ui;
}

export function svgFromStr(data: string): UIElement {
  const svg = new window.DOMParser().parseFromString(data, "image/svg+xml");
  return [
    buttonAccessory(
      { type: "icon", icon: "download" },
      "Download diagram.",
      () => saveFile(data, "image/svg+xml", "diagram.svg")
    ),
    br(),
    {
      element: document.adoptNode(svg.documentElement),
      reveal: null,
      find: null,
      type: "ui",
    },
  ];
}
/**
 * Display a table from the supplied items.
 * @param rows the items to use for each row
 * @param  headers a list fo columns, each with a title and a function to render that column for each row
 */
export function table<T>(
  rows: T[],
  ...headers: [UIElement, (value: T) => UIElement][]
): UIElement {
  if (rows.length == 0) return [];
  return createUiFromTag(
    "table",
    createUiFromTag(
      "tr",
      headers.map(([name, _func]) => createUiFromTag("th", name))
    ),
    rows.map((row) =>
      createUiFromTag(
        "tr",
        headers.map(([_name, func]) => createUiFromTag("td", func(row)))
      )
    )
  );
}
/**
 * Create a table from a collection of rows
 */
export function tableFromRows(
  rows: ComplexElement<HTMLTableRowElement>[]
): UIElement {
  if (rows.length == 0) return [];
  return createUiFromTag("table", ...rows);
}
/**
 * Create a single row to put in a table
 * @param click an optional click handler
 * @param cells the cells to put in this row
 */
export function tableRow(
  click: ClickHandler | null,
  ...cells: TableCell[]
): ComplexElement<HTMLTableRowElement> {
  const row = createUiFromTag(
    "tr",
    cells.map(({ contents, span, header, click, intensity, title }) => {
      const cell = createUiFromTag(header ? "th" : "td", contents);
      if (span) {
        cell.element.colSpan = span;
      }
      if (click) {
        cell.element.style.cursor = "pointer";
        cell.element.addEventListener("click", (e) => {
          e.stopPropagation();
          click(e);
        });
      }
      if (intensity != null) {
        cell.element.style.backgroundColor = `hsl(191, 95%, ${Math.ceil(
          97 - Math.max(0, Math.min(1, intensity)) * 20
        )}%)`;
      }
      if (title) {
        cell.element.title = title;
      }

      return cell;
    })
  );
  if (click) {
    row.element.style.cursor = "pointer";
    row.element.addEventListener("click", click);
  }
  return row;
}
/**
 * Create a tabbed area with multiple panes
 *
 * @param tabs each tab to display; each tabe has a name, contents, and an optional Ctrl-F handler. One tab may be marked as selected to be active by default
 */
export function tabs(...tabs: Tab[]): UIElement {
  return tabsModel(0, ...tabs).ui;
}

/**
 * Create a tabbed area which can be updated
 *
 * @param groups the number of tab groups to create; each group can contain multiple tabs, but they must be updated as a unit
 * @param tabs a set of tabs to display at the beginning (these cannot be updated)
 */
export function tabsModel(
  groups: number,
  ...tabs: Tab[]
): { ui: ComplexElement<HTMLElement>; models: StatefulModel<TabUpdate>[] } {
  type Group = {
    panes: ComplexElement<HTMLDivElement>[];
    buttons: ComplexElement<HTMLSpanElement>[];
  };
  let findHandler: FindHandler = null;
  let current: [number, number] = [0, 0];
  const tabGroups: Group[] = [];
  const generate = (group: number, tabs: Tab[]): Group => ({
    panes: tabs.map(({ contents }) => createUiFromTag("div", contents)),
    buttons: tabs.map(({ name }, index) => {
      const button = createUiFromTag("span", name);
      button.element.addEventListener("click", () => {
        current = [group, index];
        tabGroups.forEach(({ panes, buttons }, groupIndex) => {
          panes.forEach((pane, i) => {
            pane.element.style.display =
              groupIndex == group && i == index ? "block" : "none";
          });
          buttons.forEach((button, i) => {
            button.element.className =
              groupIndex == group && i == index ? "tab selected" : "tab";
          });
          if (groupIndex == group) {
            findHandler = panes[index].find;
            const reveal = panes[index].reveal;
            if (reveal) {
              reveal();
            }
          }
        });
      });
      return button;
    }),
  });
  const container = document.createElement("div");
  const paneHolder = document.createElement("div");
  const topBar = document.createElement("div");
  topBar.classList.add("tabs");
  container.appendChild(topBar);
  const buttonBar = document.createElement("div");

  let lastCanGoLeft = false;
  let lastCanGoRight = false;
  let leftInterval: number | null = null;
  const leftButton = createUiFromTag("span", {
    type: "icon",
    icon: "caret-left",
  });
  leftButton.element.className = "iconbutton";
  let rightInterval: number | null = null;
  const rightButton = createUiFromTag("span", {
    type: "icon",
    icon: "caret-right",
  });
  rightButton.element.className = "iconbutton";
  function updateIcons() {
    const canScroll = buttonBar.scrollWidth > buttonBar.clientWidth;
    const canGoLeft = canScroll && buttonBar.scrollLeft > 0;
    const canGoRight =
      canScroll &&
      buttonBar.scrollLeft < buttonBar.scrollWidth - buttonBar.clientWidth;
    if (canGoLeft != lastCanGoLeft) {
      clearChildren(leftButton.element);
      addElements(leftButton.element, {
        type: "icon",
        icon: canGoLeft ? "caret-left-fill" : "caret-left",
      });
      lastCanGoLeft = canGoLeft;
    }
    if (canGoRight != lastCanGoRight) {
      clearChildren(rightButton.element);
      addElements(rightButton.element, {
        type: "icon",
        icon: canGoRight ? "caret-right-fill" : "caret-right",
      });
      lastCanGoRight = canGoRight;
    }
    return { canGoLeft, canGoRight };
  }

  leftButton.element.addEventListener("mousedown", (e) => {
    e.stopPropagation();
    if (leftInterval != null) {
      window.clearInterval(leftInterval);
    }
    if (updateIcons().canGoLeft) {
      function move() {
        buttonBar.scrollLeft = Math.max(buttonBar.scrollLeft - 50, 0);
        if (!updateIcons().canGoLeft && leftInterval != null) {
          window.clearInterval(leftInterval);
          leftInterval = null;
        }
      }
      leftInterval = window.setInterval(move, 100);
      move();
    }
  });
  leftButton.element.addEventListener("mouseup", (e) => {
    e.stopPropagation();
    if (leftInterval != null) {
      window.clearInterval(leftInterval);
    }
  });

  rightButton.element.addEventListener("mousedown", (e) => {
    e.stopPropagation();
    if (rightInterval != null) {
      window.clearInterval(rightInterval);
    }
    if (updateIcons().canGoRight) {
      function move() {
        buttonBar.scrollLeft = Math.min(
          buttonBar.scrollLeft + 50,
          buttonBar.scrollWidth - buttonBar.clientWidth
        );
        if (!updateIcons().canGoRight && rightInterval != null) {
          window.clearInterval(rightInterval);
          rightInterval = null;
        }
      }
      rightInterval = window.setInterval(move, 100);
      move();
    }
  });
  rightButton.element.addEventListener("mouseup", (e) => {
    e.stopPropagation();
    if (rightInterval != null) {
      window.clearInterval(rightInterval);
    }
  });

  new ResizeObserver(() => updateIcons()).observe(buttonBar);

  topBar.appendChild(leftButton.element);
  topBar.appendChild(buttonBar);
  topBar.appendChild(rightButton.element);

  container.appendChild(paneHolder);

  const update = (group: number, activate: boolean) => {
    clearChildren(buttonBar);
    clearChildren(paneHolder);
    tabGroups
      .flatMap((tg) => tg.buttons)
      .forEach((button) => buttonBar.appendChild(button.element));
    tabGroups
      .flatMap((tg) => tg.panes)
      .forEach((pane) => paneHolder.appendChild(pane.element));
    if (current[0] == group || activate) {
      if (tabGroups[group].panes.length) {
        current = [group, 0];
      } else {
        const viableGroups = tabGroups
          .map((g, i) => ({ index: i, len: g.panes.length }))
          .filter((c) => c.len)
          .sort(
            (a, b) => Math.abs(a.index - group) - Math.abs(b.index - group)
          );
        if (viableGroups.length) {
          current = [
            viableGroups[0].index,
            viableGroups[0].index < group ? viableGroups[0].len - 1 : 0,
          ];
        }
      }
    }

    const [targetGroup, targetIndex] = current;
    tabGroups.forEach(({ panes, buttons }, groupIndex) => {
      panes.forEach((pane, i) => {
        pane.element.style.display =
          groupIndex == targetGroup && i == targetIndex ? "block" : "none";
      });
      buttons.forEach((button, i) => {
        button.element.className =
          groupIndex == targetGroup && i == targetIndex
            ? "tab selected"
            : "tab";
      });
      if (groupIndex == targetGroup && targetIndex < panes.length) {
        findHandler = panes[targetIndex].find;
        const reveal = panes[targetIndex].reveal;
        if (reveal) {
          reveal();
        }
      }
    });
  };

  tabGroups.push(generate(0, tabs));
  const models: StatefulModel<TabUpdate>[] = [];
  for (let i = 0; i < groups; i++) {
    const group = i + 1;
    tabGroups.push({ panes: [], buttons: [] });
    models.push({
      reload: () => {},
      statusChanged: ({ tabs, activate }) => {
        tabGroups[group] = generate(group, tabs);
        update(group, activate);
      },
      statusFailed: (message, retry) => {
        tabGroups[group] = {
          panes: [createUiFromTag("div", img("dead.svg", "deadolive"))],
          buttons: [
            createUiFromTag(
              "span",
              text(message),
              retry
                ? button("Retry", "Try the operation again.", retry)
                : blank()
            ),
          ],
        };
        update(group, false);
      },
      statusWaiting: () => {
        tabGroups[group] = {
          panes: [createUiFromTag("div", throbber())],
          buttons: [createUiFromTag("span", throbberSmall())],
        };
        update(group, false);
      },
    });
  }

  update(0, true);
  return {
    ui: {
      element: container,
      find: () => {
        if (findHandler) {
          return findHandler();
        } else {
          return false;
        }
      },
      reveal: null,
      type: "ui",
    },
    models: models,
  };
}

/**
 * Create a UI element with a bunch of fake buttons for tags/types
 */
export function tagList(title: string, entries: string[]): UIElement {
  if (entries.length) {
    return tile(
      ["filterlist"],
      title,
      entries.map((entry) => [textWithTitle(entry, ""), " "])
    );
  } else {
    return blank();
  }
}

/**
 * Display a throbber to indicate that the user should be patient.
 */
export function throbber(): UIElement {
  const throbber = document.createElement("object");
  throbber.data = "press.svg";
  throbber.type = "image/svg+xml";
  throbber.className = "throbber";
  throbber.style.visibility = "hidden";
  window.setTimeout(() => (throbber.style.visibility = "visible"), 500);
  return { element: throbber, reveal: null, find: null, type: "ui" };
}
/**
 * Display a throbber to indicate that the user should be patient that's limited to the line height.
 */
export function throbberSmall(): UIElement {
  const throbber = document.createElement("span");
  throbber.className = "throbber";
  throbber.appendChild(document.createElement("span"));
  throbber.appendChild(document.createElement("span"));
  throbber.appendChild(document.createElement("span"));
  return { element: throbber, reveal: null, find: null, type: "ui" };
}

/**
 * Create a state synchronizer that just caches the value in a local variable.
 */
export function temporaryState<T>(initial: T): StateSynchronizer<T> {
  let current: T = initial;
  let listener: StateListener<T> = null;
  return {
    reload: () => {},
    statusChanged: (input: T) => {
      current = input;
      if (listener) {
        listener(current, true);
      }
    },
    statusFailed: (message, _retry) => console.log(message),
    statusWaiting: () => {},
    get(): T {
      return current;
    },
    listen: (newListener: StateListener<T>) => {
      listener = newListener;
      if (listener) {
        listener(current, false);
      }
    },
  };
}

/**
 * Display text as a paragraph
 */
export function text(contents: string | number, title?: string): UIElement {
  const element = document.createElement("p");
  element.innerText = `${contents}`.replace(/\n/g, "⏎");
  element.title = title || "";
  return { element: element, reveal: null, find: null, type: "ui" };
}
/**
 * Display text as a paragraph
 */
export function textWithTitle(
  contents: DisplayElement,
  title: string
): UIElement {
  const element = createUiFromTag("span", contents);
  element.element.title = title;
  return element;
}
/**
 * Create a block with specific CSS styling
 */
export function tile(classes: string[], ...contents: UIElement[]): UIElement {
  const element = createUiFromTag("div", ...contents);
  for (const c of classes) {
    element.element.classList.add(c);
  }
  return element;
}
/**
 * Display an absolute UNIX epoch time in a nice way.
 */
export function timespan(time: number | undefined | null): UIElement {
  if (!time) return "N/A";
  const { ago, absolute } = computeDuration(time * 1000);
  return text(`${absolute} (${ago})`);
}
/**
 * Create a nested tree navigator
 * @param model the model to update when an item is selected
 * @param render a callback to render an entry
 * @returns a UI element displaying the tree, a model for that data and a model for the path selection callback
 */
export function tree<T>(
  model: StatefulModel<T>,
  render: (input: T) => DisplayElement
): {
  ui: UIElement;
  buttons: UIElement;
  data: StatefulModel<T[]>;
  grouping: StatefulModel<(input: T) => TreePath[]>;
} {
  const { ui, model: paneModel } = pane("small");
  let expand: StatefulModel<boolean> = combineModels();
  let active: ComplexElement<HTMLParagraphElement> | null = null;
  const [dataModel, grouping] = mergingModel(
    paneModel,
    (input: T[] | null, pathFunction: ((input: T) => TreePath[]) | null) => {
      const paths = (input || []).map((i) => {
        const current = createUiFromTag("p", render(i));
        current.element.classList.add("tree");
        current.element.addEventListener("click", (e) => {
          e.stopPropagation();
          if (active) {
            active.element.classList.remove("selected");
          }
          active = current;
          current.element.classList.add("selected");
          model.statusChanged(i);
        });

        return {
          path: pathFunction == null ? [] : pathFunction(i),
          ui: current,
        };
      });
      const allChildren: StatefulModel<boolean>[] = [];
      const ui = treeSplitPaths(paths, 0, allChildren);
      expand = combineModels(...allChildren);
      return ui;
    },
    true
  );
  return {
    ui,
    buttons: [
      buttonAccessory(
        { type: "icon", icon: "arrows-expand" },
        "Expand all branches",
        () => expand.statusChanged(true)
      ),
      buttonAccessory(
        { type: "icon", icon: "arrows-collapse" },
        "Collapse all branches",
        () => expand.statusChanged(false)
      ),
    ],
    data: dataModel,
    grouping: grouping,
  };
}
function treeSplitPaths(
  entries: { path: TreePath[]; ui: UIElement }[],
  index: number,
  expand: StatefulModel<boolean>[]
): UIElement {
  const childCounts: { [s: string]: number } = {};
  for (const path of entries
    .filter((e) => e.path.length > index + 1)
    .map((e) => e.path[index])) {
    if (childCounts.hasOwnProperty(path.value)) {
      childCounts[path.value]++;
    } else {
      // If it's the root level, we don't want to collapse any singletons, so inflate the count.
      childCounts[path.value] = path.elide ? 1 : 2;
    }
  }
  const leaves = entries
    .filter(
      (e) => e.path.length == index + 1 || childCounts[e.path[index].value] == 1
    )
    .map((e) => e.ui);
  const children = Object.entries(childCounts)
    .filter((e) => e[1] > 1)
    .map((e) => e[0])
    .sort()
    .flatMap((value) => {
      const childEntries = entries.filter((e) => e.path[index].value == value);
      const childUi = collapsibleWithDefault(
        childEntries[0].path[index].display,
        childEntries.length < 5,
        treeSplitPaths(childEntries, index + 1, expand)
      );
      if (childUi) {
        expand.push(childUi.model);
        return [childUi.ui];
      } else {
        return [];
      }
    });
  return indented(children.concat(leaves));
}
/**
 * Unordered list of items
 */
export function unorderedList(...contents: DisplayElement[]): UIElement {
  const element = createUiFromTag(
    "ul",
    contents.map((item) => createUiFromTag("li", item))
  );
  return element;
}
// Preload images
new Image().src = "press.svg";
new Image().src = "dead.svg";

import { ActionFilter, ParseQueryResponse } from "./actionfilters.js";
import {
  UIElement,
  blank,
  busyDialog,
  button,
  dialog,
  pager,
  singleState,
  text,
  svgFromStr,
  StateSynchronizer,
  StateListener,
} from "./html.js";
import {
  MutableStore,
  SplitStatefulModel,
  StatefulModel,
  combineModels,
  mapModel,
  mapSplitModel,
  mapTupleModel,
  promiseModel,
  promiseTupleModel,
  splitModel,
} from "./util.js";
import { PauseRequest, Pauses } from "./pause.js";
import { PrometheusAlert, AlertFilter } from "./alert.js";
import { TypeResponse, ValueResponse } from "./definitions.js";
import { SimulationRequest, SimulationResponse } from "./simulation.js";
import { Stat } from "./stats.js";

/**
 * The update information provided by interrogating the server
 */
export interface MutableServerInfo<I, R> {
  /**
   * The original data used to request information from the server
   */
  input: I;
  /**
   * The response from the server.
   */
  response: R;
  /**
   * A callback to attempt to mutate data on the server.
   */
  setter: (value: R) => void;
}

/**
 * These are the request types for all the endpoints where JSON requests can be made to the server
 *
 * Null entires are GET requests; all others are treated as POST requests
 */
interface ShesmuRequestType {
  allalerts: null;
  command: { command: string; filters: ActionFilter[] };
  constant: string;
  function: { name: string; args: any[] };
  getalert: string;
  parsequery: string;
  pausefile: PauseRequest;
  pauseolive: PauseRequest;
  pauses: null;
  printquery: ActionFilter;
  purge: ActionFilter[];
  queryalerts: AlertFilter<RegExp>;
  simulate: SimulationRequest;
  stats: ActionFilter[];
  tags: ActionFilter[];
  type: { value: string; format: string };
}
/**
 * These are the response types for all the endpoints where JSON requests can be made to the server
 */
interface ShesmuResponseType {
  allalerts: PrometheusAlert[];
  command: number;
  constant: ValueResponse;
  function: ValueResponse;
  getalert: PrometheusAlert | null;
  parsequery: ParseQueryResponse;
  pausefile: boolean;
  pauseolive: boolean;
  pauses: Pauses;
  printquery: string;
  purge: number;
  queryalerts: PrometheusAlert[];
  simulate: SimulationResponse;
  stats: Stat[];
  tags: string[];
  type: TypeResponse;
}

/**
 * Perform a JSON fetch operation which will display a modal dialog while working
 */
export function fetchJsonWithBusyDialog<
  R extends keyof ShesmuRequestType & keyof ShesmuResponseType
>(
  url: R,
  parameters: ShesmuRequestType[R],
  callback: (result: ShesmuResponseType[R]) => void
): void {
  const closeBusy = busyDialog();

  fetchAsPromise(url, parameters)
    .then(callback)
    .catch((error) => {
      closeBusy();
      if (error) {
        dialog((close) => [
          text(error.message),
          button(
            [{ type: "icon", icon: "arrow-clockwise" }, "Retry"],
            "Attempt operation again.",
            () => {
              close();
              fetchJsonWithBusyDialog(url, parameters, callback);
            }
          ),
        ]);
      }
    })
    .finally(closeBusy);
}

export function fetchAsPromise<
  R extends keyof ShesmuRequestType & keyof ShesmuResponseType
>(url: R, parameters: ShesmuRequestType[R]): Promise<ShesmuResponseType[R]> {
  return fetch(
    url,
    parameters === null
      ? { method: "GET" }
      : { method: "POST", body: JSON.stringify(parameters) }
  )
    .then((response) => {
      if (response.ok) {
        return Promise.resolve(response);
      } else if (response.status == 503) {
        return Promise.reject(new Error("Shesmu is currently overloaded."));
      } else {
        return Promise.reject(
          new Error(`Failed to load: ${response.status} ${response.statusText}`)
        );
      }
    })
    .then((response) => response.json())
    .then((response) => response as ShesmuResponseType[R]);
}

/**
 * Create a model that acccepts an array and can store it for later iteration
 */
export function iterableModel<T>(): StatefulModel<T[]> & Iterable<T> {
  let state: T[] = [];
  return {
    reload: () => {},
    statusChanged: (input) => (state = [...input]),
    statusFailed: (message, _retry) => console.log(message),
    statusWaiting: () => {},
    [Symbol.iterator]: () => state[Symbol.iterator](),
  };
}

/**
 * Load a file off of local disk.
 * @param callback the callback to be invoked when the file is read
 */
export function loadFile(callback: (name: string, data: string) => void): void {
  const input = document.createElement("INPUT") as HTMLInputElement;
  input.type = "file";

  input.onchange = () => {
    const file = input.files && input.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (rev) => {
        if (rev.target && rev.target.result) {
          callback(file.name, rev.target.result.toString());
        }
      };
      reader.readAsText(file, "UTF-8");
    }
  };

  input.click();
}

/**
 * Create a storage location backed by the browser's local storage in JSON form
 * @param key the key name to use in the local storage database
 * @param empty the default value to use if none is found or the value is corrupt
 */
export function locallyStored<T>(key: string, empty: T): StateSynchronizer<T> {
  let current = empty;
  return locallyStoredCustom(
    key,
    empty,
    (value) => {
      try {
        return JSON.parse(value) as T;
      } catch (e) {
        return null;
      }
    },
    (input) => JSON.stringify(input)
  );
}
/**
 * Create a storage location backed by the browser's local storage as raw text
 * @param key the key name to use in the local storage database
 * @param empty the default value to use if none is found or the value is corrupt
 */
export function locallyStoredString(
  key: string,
  empty: string
): StateSynchronizer<string> {
  return locallyStoredCustom(
    key,
    empty,
    (x) => x,
    (x) => x
  );
}
export function locallyStoredCustom<T>(
  key: string,
  empty: T,
  parse: (input: string) => T | null,
  store: (input: T) => string
): StateSynchronizer<T> {
  let currentListener: StateListener<T> = null;
  window.addEventListener("storage", (e) => {
    if (e.key == key && e.oldValue != e.newValue && currentListener) {
      currentListener(
        e.newValue == null ? empty : parse(e.newValue) || empty,
        false
      );
    }
  });
  return {
    reload: () => {},
    statusChanged: (input: T) => {
      localStorage.setItem(key, store(input));
      if (currentListener) {
        currentListener(input, true);
      }
    },
    statusWaiting: () => {},
    statusFailed: (message: string) => console.log(message),
    get: () => {
      const value = localStorage.getItem(key);
      return value == null ? empty : parse(value) || empty;
    },
    listen: (listener) => {
      currentListener = listener;
      if (listener) {
        const value = localStorage.getItem(key);
        listener(value == null ? empty : parse(value) || empty, false);
      }
    },
  };
}
/**
 * Create a storage location backed by the browser's local storage in JSON form that works like a key-value map
 * @param storageKey the key name to use in the local storage database
 */
export function mutableLocalStore<T>(
  storageKey: string
): MutableStore<string, T> {
  let original: { [name: string]: T } = {};
  try {
    const value = localStorage.getItem(storageKey);
    if (value) {
      original = JSON.parse(value);
    }
  } catch (e) {
    // Ignore
  }
  return {
    clear(): void {
      original = {};
    },
    delete(key: string): void {
      delete original[key];
    },
    get(key: string): T | undefined {
      return original[key];
    },
    [Symbol.iterator](): IterableIterator<[string, T]> {
      return Object.entries(original)[Symbol.iterator]();
    },
    set(key: string, value: T): void {
      original[key] = value;
      localStorage.setItem(storageKey, JSON.stringify(original));
    },
  };
}
/**
 * Create a refreshable view that provides pagination
 * @param pageLength the number of items on each page
 * @param input the URL or request to access
 * @param initial the initial request
 * @param makeRequest a function to create a request for the current page
 * @param computePage get the resulting page information from the response
 * @param model the model to update with the results; it also includes the original request for tracking purposes
 */
export function paginatedRefreshable<I, O>(
  pageLength: number,
  input: RequestInfo,
  makeRequest: (request: I, offset: number, pageLength: number) => RequestInit,
  computePage: (
    request: I | null,
    output: O
  ) => { offset: number; total: number } | null,
  model: StatefulModel<[I, O]>
): { model: SplitStatefulModel<I, [I, O]>; ui: UIElement } {
  let current: I | null = null;
  const { ui, model: pagerModel } = singleState((state: [I, O]) => {
    const result = computePage(state[0], state[1]);
    if (result) {
      return pager(
        Math.ceil(result.total / pageLength),
        Math.floor(result.offset / pageLength),
        (index: number) => {
          if (current != null) {
            outputModel.statusChanged([current, index]);
          }
        }
      );
    } else {
      return blank();
    }
  }, true);
  const outputModel = splitModel(combineModels(pagerModel, model), (output) =>
    mapModel(
      requestTupleModel(
        input,
        mapTupleModel(
          promiseTupleModel(output),
          (promise: Promise<Response | null>) =>
            promise
              .then((response) =>
                response ? response.json() : Promise.resolve(null)
              )
              .then((data: any) => data as O)
        )
      ),
      ([request, page]: [I, number]) =>
        [request, makeRequest(request, page, 25)] as [I, RequestInit]
    )
  );
  return {
    ui: ui,
    model: mapSplitModel(outputModel, (input: I) => {
      current = input;
      return [input, 0] as [I, number];
    }),
  };
}
/**
 * Create a collection of GUI elements backed by a server callback
 *
 * @param input the request to make
 * @param formatters a collection of functions to display the output for a widget; the output will have a matching UI element and they will be updated simultaneously
 * @param modal display a modal dialog to lock out the whole UI
 * @returns an object with a callback to force update of the GUI (the GUI elements are also given this), a prepared refresh button, and all the GUI elements requested
 */
export function refreshable<
  R extends keyof ShesmuRequestType & keyof ShesmuResponseType
>(
  input: R,
  model: StatefulModel<ShesmuResponseType[R] | null>,
  modal: boolean
): SplitStatefulModel<ShesmuRequestType[R], ShesmuResponseType[R] | null> {
  return splitModel(model, (output) =>
    mapModel(
      requestModel(
        input,
        mapModel(promiseModel(output), (promise: Promise<Response>) =>
          promise
            .then((response) => response.json())
            .then((data: any) => data as ShesmuResponseType[R])
        ),
        modal
      ),
      (input) => {
        return input === null
          ? { method: "GET" }
          : { method: "POST", body: JSON.stringify(input) };
      }
    )
  );
}
/**
 * Create a collection of GUI elements backed by a server callback
 *
 * @param input the request to make
 * @param initial the GUI can immediate make a request with the state provided or it can display prefetched data provided
 * @param makeRequest a function to create an HTTP request from the state provided
 * @param formatters a collection of functions to display the output for a widget; the output will have a matching UI element and they will be updated simultaneously
 * @returns an object with a callback to force update of the GUI (the GUI elements are also given this), a prepared refresh button, and all the GUI elements requested
 */
export function refreshableSvg<I>(
  input: RequestInfo,
  makeRequest: (request: I) => RequestInit,
  model: StatefulModel<UIElement>
): SplitStatefulModel<I, UIElement> {
  return splitModel(model, (output) =>
    mapModel(
      requestModel(
        input,
        mapModel(promiseModel(output), (promise: Promise<Response>) =>
          promise.then((response) => response.text()).then(svgFromStr)
        ),
        false
      ),
      makeRequest
    )
  );
}
/**
 * Create a collection of GUI elements backed by a server callback
 *
 * @param input the request to make
 * @param model the model that will deal with the response
 * @param modal display a modal dialog to lock out the whole UI
 */
export function requestModel(
  input: RequestInfo,
  model: StatefulModel<Promise<Response>>,
  modal: boolean
): StatefulModel<RequestInit> {
  return mapModel(model, (request: RequestInit) => {
    const finished = modal ? busyDialog() : () => {};
    return fetch(input, request).then((response) => {
      finished();
      if (response.ok) {
        return Promise.resolve(response);
      } else if (response.status == 503) {
        return Promise.reject(new Error("Shesmu is currently overloaded."));
      } else {
        return Promise.reject(
          new Error(`Failed to load: ${response.status} ${response.statusText}`)
        );
      }
    });
  });
}

/**
 * Create a collection of GUI elements backed by a server callback
 *
 * @param input the request to make
 * @param model the model that will deal with the response
 */
export function requestTupleModel<T>(
  input: RequestInfo,
  model: StatefulModel<[T, Promise<Response | null>]>
): StatefulModel<[T, RequestInit | null]> {
  return mapTupleModel(model, (request: RequestInit | null) =>
    request === null
      ? Promise.resolve(null)
      : fetch(input, request).then((response) => {
          if (response.ok) {
            return Promise.resolve(response);
          } else if (response.status == 503) {
            return Promise.reject(new Error("Shesmu is currently overloaded."));
          } else {
            return Promise.reject(
              new Error(
                `Failed to load: ${response.status} ${response.statusText}`
              )
            );
          }
        })
  );
}

/**
 * Copy data into the clipboard
 */
export function saveClipboard(data: string): void {
  const closeBusy = busyDialog();
  const buffer = document.createElement("TEXTAREA") as HTMLTextAreaElement;
  buffer.value = data;
  buffer.style.display = "inline";
  document.body.appendChild(buffer);
  buffer.select();
  document.execCommand("Copy");
  buffer.style.display = "none";
  window.setTimeout(() => {
    closeBusy();
    document.body.removeChild(buffer);
  }, 300);
}
/**
 * Copy JSON data into the clipboard
 */
export function saveClipboardJson(data: any): void {
  saveClipboard(JSON.stringify(data, null, 2));
}
/**
 * Save data as a local file
 */
export function saveFile(
  data: string,
  mimetype: string,
  fileName: string
): void {
  const blob = new Blob([data], { type: mimetype });

  const a = document.createElement("a");
  a.download = fileName;
  a.href = URL.createObjectURL(blob);
  a.dataset.downloadurl = ["text/plain", a.download, a.href].join(":");
  a.style.display = "none";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(a.href), 1500);
}
/**
 * Create a model which is trying to updated based on state from the server.
 *
 * This assumes one endpoint can both fetch and change the state. It also makes it possible to bypass the entire process if the endpoint is not appropriate from some inputs.
 * @param input the request to query server information
 * @param model the target of the server's response; it may be null if the input was null
 * @param makeRequest create an HTTP request or null to bypass the server state
 */
export function serverStateModel<
  I,
  R extends keyof ShesmuRequestType & keyof ShesmuResponseType
>(
  input: R,
  model: StatefulModel<MutableServerInfo<I, ShesmuResponseType[R]> | null>,
  makeRequest: (
    request: I,
    value: ShesmuResponseType[R] | null
  ) => ShesmuRequestType[R] | null
): StatefulModel<I | null> {
  const split: StatefulModel<
    [I, ShesmuResponseType[R] | null] | null
  > = splitModel(model, (output) =>
    mapModel(
      requestTupleModel(
        input,
        mapTupleModel(
          promiseTupleModel(
            mapModel(
              output,
              (info: [I | null, ShesmuResponseType[R] | null] | null) => {
                const input = info?.[0];
                const response = info?.[1];
                if (
                  input === null ||
                  input === undefined ||
                  response === null ||
                  response === undefined
                ) {
                  return null;
                } else {
                  return {
                    input: input,
                    response: response,
                    setter: (value) => split.statusChanged([input, value]),
                  };
                }
              }
            )
          ),
          (promise: Promise<Response | null>) =>
            promise
              .then((response) =>
                response ? response.json() : Promise.resolve(null)
              )
              .then((data: any) => data as ShesmuResponseType[R])
        )
      ),
      (
        info: [I, ShesmuResponseType[R] | null] | null
      ): [I | null, RequestInit | null] => {
        if (info) {
          const serverRequest = makeRequest(info[0], info[1]);
          if (serverRequest === null) {
            return [info[0], null];
          } else {
            return [
              info[0],
              { method: "POST", body: JSON.stringify(serverRequest) },
            ];
          }
        } else {
          return [null, null];
        }
      }
    )
  );
  return mapModel(split, (input: I | null) =>
    input ? ([input, null] as [I, ShesmuResponseType[R] | null]) : null
  );
}

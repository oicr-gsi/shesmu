/**
 * A function which converts file names to something with linebreaks
 */
export type FilenameFormatter = (path: string) => Node[] | string;

/**
 * An interface for dealing with an updatable data store. It is a subset of the standard Map type
 */
export interface MutableStore<K, V> {
  [Symbol.iterator](): IterableIterator<[K, V]>;
  clear(): void;
  delete(key: K): void;
  get(key: K): V | undefined;
  set(key: K, value: V): void;
}
/**
 * The objects that provide olive location information
 */
export interface SourceLocation {
  file: string;
  line: number | null;
  column: number | null;
  hash: string | null;
  url?: string;
}

/**
 * An interface for something that can render itself using a preferred type or a secondary type
 *
 * This can be useful when the update is a slow operation
 */
export interface SplitStatefulModel<I, O> extends StatefulModel<I> {
  force: (state: O) => void;
}

/**
 * An interface for something that can render itself using new input
 */
export interface StatefulModel<T> {
  /**
   * Refresh the data, if possible.
   */
  reload: () => void;
  /**
   * This is invoked when the UI wishes to update the state of the model with new data.
   */
  statusChanged: (input: T) => void;
  /**
   * The UI is waiting on data and the model should render feedback that it is being updated.
   */
  statusWaiting: () => void;
  /**
   * The UI failed attempting to update the state. The model should inform the user. If possible the UI may provide a callback to try the operation again.
   */
  statusFailed: (message: string, retry: (() => void) | null) => void;
}
/**
 * A regular expression that matches valid Shesmu identifiers.
 */
export const validIdentifier = /[a-z][a-zA-Z0-9_]*/;
/**
 * Join items with a soft line-break at every delimiter.
 */
export function softJoin(delimiter: string, text: string[]): Node[] {
  return text.flatMap((chunk: string, index: number) =>
    index == 0
      ? [document.createTextNode(chunk)]
      : [
          document.createTextNode(delimiter),
          document.createElement("WBR"),
          document.createTextNode(chunk),
        ]
  );
}

/**
 * Break text containing slashes into text with soft breaks to wrap long paths.
 */
export function breakSlashes(text: string): Node[] {
  return softJoin("/", text.split(/\//g));
}
/**
 * Create a new model by combining existing models
 */
export function combineModels<T>(
  ...models: StatefulModel<T>[]
): StatefulModel<T> {
  return {
    reload: () => {
      for (const model of models) {
        model.reload();
      }
    },
    statusChanged: (input: T) => {
      for (const model of models) {
        model.statusChanged(input);
      }
    },
    statusFailed: (message, retry) => {
      for (const model of models) {
        model.statusFailed(message, retry);
      }
    },
    statusWaiting: () => {
      for (const model of models) {
        model.statusWaiting();
      }
    },
  };
}
/**
 * Find the longest common prefix and produce a function to strip that prefix
 */
export function commonPathPrefix(paths: string[]): FilenameFormatter {
  if (!paths.length) {
    return (x: any) => x;
  }

  const commonPrefix = paths[0].split("/");
  commonPrefix.pop();
  for (let i = 1; i < paths.length; i++) {
    const parts = paths[i].split("/");
    parts.pop();
    let x = 0;
    while (
      x < parts.length &&
      x < commonPrefix.length &&
      parts[x] == commonPrefix[x]
    )
      x++;
    commonPrefix.length = x;
  }
  return (x: string) => softJoin("/", x.split("/").splice(commonPrefix.length));
}
/**
 * Make a lightweight copy of a location for sending back to the server
 */
export function copyLocation(location: SourceLocation): SourceLocation {
  return {
    file: location.file,
    line: location.line,
    column: location.column,
    hash: location.hash,
  };
}
/**
 * Show a duration as a human-friendly approximation
 * @param duration the duration in milliseconds
 */
export function formatTimeSpan(duration: number): string {
  let diff = Math.abs(Math.ceil(duration / 1000));
  let result = "";
  let chunkcount = 0;
  for (let { span, name } of [
    { span: 31557600, name: "y" },
    { span: 86400, name: "d" },
    { span: 3600, name: "h" },
    { span: 60, name: "m" },
    { span: 1, name: "s" },
  ]) {
    const chunk = Math.floor(diff / span);
    if (chunk > 0 || chunkcount > 0) {
      result = `${result}${chunk}${name} `;
      diff = diff % span;
      if (++chunkcount > 2) {
        break;
      }
    }
  }
  return result;
}

/**
 * Compute the duration between an aboslute timestamp and the current time.
 */
export function computeDuration(
  timeInMs: number
): { ago: string; absolute: string } {
  const span = new Date().getTime() - timeInMs;
  let ago = formatTimeSpan(span);
  if (ago) {
    ago = ago + (span < 0 ? "from now" : "ago");
  } else {
    ago = "now";
  }
  return { ago: ago, absolute: new Date(timeInMs).toISOString() };
}
/**
 * Produce a model that generates an error on null input
 */
export function errorModel<T, R>(
  model: StatefulModel<R>,
  transformer: (
    input: T
  ) => { type: "error"; message: string } | { type: "ok"; value: R }
): StatefulModel<T> {
  return {
    reload: model.reload,
    statusChanged: (input: T) => {
      const result = transformer(input);
      if (result.type == "error") {
        model.statusFailed(result.message, null);
      } else {
        model.statusChanged(result.value);
      }
    },
    statusFailed: model.statusFailed,
    statusWaiting: model.statusWaiting,
  };
}
/**
 * Produce a model that generates an error on null input
 */
export function filterModel<T>(
  model: StatefulModel<T>,
  message: string
): StatefulModel<T | null> {
  return {
    reload: model.reload,
    statusChanged: (input: T | null) => {
      if (input === null) {
        model.statusFailed(message, null);
      } else {
        model.statusChanged(input);
      }
    },
    statusFailed: model.statusFailed,
    statusWaiting: model.statusWaiting,
  };
}
/**
 * Perform a conversion on the input of a model
 */
export function mapModel<T, R>(
  model: StatefulModel<R>,
  transformer: (input: T) => R
): StatefulModel<T> {
  return {
    reload: model.reload,
    statusChanged: (input: T) => model.statusChanged(transformer(input)),
    statusFailed: model.statusFailed,
    statusWaiting: model.statusWaiting,
  };
}
/**
 * Perform a conversion on the input of a model preserving some accessory data
 */
export function mapTupleModel<T, S, R>(
  model: StatefulModel<[S, R]>,
  transformer: (input: T) => R
): StatefulModel<[S, T]> {
  return {
    reload: model.reload,
    statusChanged: ([left, right]: [S, T]) =>
      model.statusChanged([left, transformer(right)]),
    statusFailed: model.statusFailed,
    statusWaiting: model.statusWaiting,
  };
}

/**
 * Perform a conversion on the input of a model
 */
export function mapSplitModel<T, S, R>(
  model: SplitStatefulModel<R, S>,
  transformer: (input: T) => R
): SplitStatefulModel<T, S> {
  return {
    statusChanged: (input: T) => model.statusChanged(transformer(input)),
    force: model.force,
    reload: model.reload,
    statusFailed: model.statusFailed,
    statusWaiting: model.statusWaiting,
  };
}

/**
 * Check if a keyword appears in an arbitrary data structure.
 * @param keyword the keyword to look for; it should be lower case
 * @param value the value to search
 */
export function matchKeywordInArbitraryData(
  keyword: string,
  value: any
): boolean {
  switch (typeof value) {
    case "function":
    case "undefined":
    case "symbol":
      return false;

    case "boolean":
    case "number":
    case "bigint":
    case "string":
      return `${value}`.toLowerCase().indexOf(keyword) != -1;
    default:
      if (Array.isArray(value)) {
        return value.some((v) => matchKeywordInArbitraryData(keyword, v));
      }
      if (value === null) {
        return false;
      }
      return Object.entries(value).some(
        ([property, propertyValue]) =>
          property.toLowerCase().indexOf(keyword) != -1 ||
          matchKeywordInArbitraryData(keyword, propertyValue)
      );
  }
}
/**
 * Merges multiple lists of locations
 *
 * This removes any overlap (_i.e._, if a location is a covered by a more broad location, it is removed).
 */
export function mergeLocations(
  ...locations: SourceLocation[][]
): SourceLocation[] {
  return locations
    .flat()
    .sort(
      (a, b) =>
        a.file.localeCompare(b.file) ||
        (a.line || 0) - (b.line || 0) ||
        (a.column || 0) - (b.column || 0) ||
        (a.hash || "").localeCompare(b.hash || "")
    )
    .filter(
      (location, index, arr) =>
        index == 0 ||
        location.file != arr[index].file ||
        (arr[index].line && location.line != arr[index].line) ||
        (arr[index].column && location.column != arr[index].column) ||
        (arr[index].hash && location.hash != arr[index].hash)
    );
}
/**
 * Produce a model that generates an error on null input
 */
export function mergingModel<T, S, R>(
  model: StatefulModel<R>,
  combine: (left: T | null, right: S | null) => R
): [StatefulModel<T>, StatefulModel<S>] {
  let lastLeft: T | null = null;
  let lastRight: S | null = null;
  return [
    {
      reload: model.reload,
      statusChanged: (input: T) => {
        lastLeft = input;
        model.statusChanged(combine(input, lastRight));
      },
      statusFailed: model.statusFailed,
      statusWaiting: model.statusWaiting,
    },
    {
      reload: model.reload,
      statusChanged: (input: S) => {
        lastRight = input;
        model.statusChanged(combine(lastLeft, input));
      },
      statusFailed: model.statusFailed,
      statusWaiting: model.statusWaiting,
    },
  ];
}

/**
 * Create a mutable store associated with a model
 *
 * This wraps a real mutable store with one that listens and update itself when the underlying store has changed.
 * @param store the real store that holds data
 * @param model the model to update
 */
export function mutableStoreWatcher<K, V>(
  store: MutableStore<K, V>,
  model: StatefulModel<Iterable<[K, V]>>
): MutableStore<K, V> {
  model.statusChanged(store);
  return {
    clear(): void {
      store.clear();
      model.statusChanged(store);
    },
    delete(key: K): void {
      store.delete(key);
      model.statusChanged(store);
    },
    get(key: K): V | undefined {
      return store.get(key);
    },
    [Symbol.iterator](): IterableIterator<[K, V]> {
      return store[Symbol.iterator]();
    },
    set(key: K, value: V): void {
      store.set(key, value);
      model.statusChanged(store);
    },
  };
}

/**
 * Create a model that can wait for promises to resolve
 */
export function promiseModel<T>(
  model: StatefulModel<T>
): StatefulModel<Promise<T>> {
  let sequence = 0;
  return {
    reload: () => {
      sequence++;
      model.reload();
    },
    statusChanged: (input: Promise<T>) => {
      const id = ++sequence;
      model.statusWaiting();
      input
        .then((s) => {
          if (id == sequence) {
            model.statusChanged(s);
          }
        })
        .catch((e) => {
          if (id == sequence) {
            if (e instanceof Error) {
              model.statusFailed(e.message, null);
            } else {
              model.statusFailed(`${e}`, null);
            }
          }
        });
    },
    statusFailed: (message, retry) => {
      sequence++;
      model.statusFailed(message, retry);
    },
    statusWaiting: () => {
      sequence++;
      model.statusWaiting();
    },
  };
}

/**
 * Create a model that can wait for promises to resolve along with some accessory data
 */
export function promiseTupleModel<T, S>(
  model: StatefulModel<[T, S]>
): StatefulModel<[T, Promise<S>]> {
  let sequence = 0;
  return {
    reload: () => {
      sequence++;
      model.reload();
    },
    statusChanged: (input: [T, Promise<S>]) => {
      const id = ++sequence;
      model.statusWaiting();
      input[1]
        .then((s) => {
          if (id == sequence) {
            model.statusChanged([input[0], s]);
          }
        })
        .catch((e) => {
          if (id == sequence) {
            if (e instanceof Error) {
              model.statusFailed(e.message, null);
            } else {
              model.statusFailed(`${e}`, null);
            }
          }
        });
    },
    statusFailed: (message, retry) => {
      sequence++;
      model.statusFailed(message, retry);
    },
    statusWaiting: () => {
      sequence++;
      model.statusWaiting();
    },
  };
}

/**
 * Create a split model that can allow transformation of the input while also updating the underlying data
 * @param model the model to target
 * @param prepare the transformation to apply to the data
 */
export function splitModel<I, O>(
  model: StatefulModel<O>,
  prepare: (input: StatefulModel<O>) => StatefulModel<I>
): SplitStatefulModel<I, O> {
  let lastInput:
    | { type: "input"; value: I }
    | { type: "output"; value: O }
    | null = null;
  const output = prepare(model);
  return {
    statusChanged: (input: I) => {
      lastInput = { type: "input", value: input };
      output.statusChanged(input);
    },
    force: (input: O) => {
      lastInput = { type: "output", value: input };
      model.statusChanged(input);
    },
    reload: () => {
      if (lastInput) {
        if (lastInput.type == "input") {
          output.statusChanged(lastInput.value);
        } else {
          model.statusChanged(lastInput.value);
        }
      }
    },
    statusFailed: output.statusFailed,
    statusWaiting: output.statusWaiting,
  };
}

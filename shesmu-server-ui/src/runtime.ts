/* Runtime support library for olive langauge on the front end
 */

export function arrayFromJson(input: any): any[] {
  if (Array.isArray(input)) {
    return input;
  } else if (typeof input == "object") {
    return Object.values(input);
  } else {
    return [];
  }
}
export function boolParse(value: string): boolean | null {
  if (value.toLowerCase() == "true") {
    return true;
  }
  if (value.toLowerCase() == "false") {
    return false;
  }
  return null;
}

function binarySearch<T>(
  arr: T[],
  compare: (input: T) => number,
  start: number,
  end: number
): boolean {
  if (start > end) return false;

  const mid = Math.floor((start + end) / 2);

  const comparison = compare(arr[mid]);

  if (comparison == 0) return true;

  if (comparison > 0) {
    return binarySearch(arr, compare, start, mid - 1);
  } else {
    return binarySearch(arr, compare, mid + 1, end);
  }
}

export function comparatorNumeric<T>(
  key: (input: T) => number
): (a: T, b: T) => number {
  return (a, b) => key(a) - key(b);
}
export function comparatorString<T>(
  key: (input: T) => string
): (a: T, b: T) => number {
  return (a, b) => key(a).localeCompare(key(b));
}
export function dateDayOfYearLocal(date: Date): number {
  // UTC has no daylight savings, so we transform the date to UTC and then do the calculation there
  return (
    (Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) -
      Date.UTC(date.getFullYear(), 0, 0)) /
    86_400_000
  );
}
export function dateDayOfYearUTC(date: Date): number {
  // UTC has no daylight savings, so we can do this raw
  return Math.floor(
    (date.getTime() - Date.UTC(date.getUTCFullYear(), 0, 0)) / 86_400_000
  );
}
export function dateDifference(left: Date, right: Date): number {
  return Math.trunc((left.getTime() - right.getTime()) / 1000);
}
export function datePlusSeconds(date: Date, seconds: number): Date {
  return new Date(date.getTime() + seconds * 1000);
}
export function dateMinusSeconds(date: Date, seconds: number): Date {
  return new Date(date.getTime() - seconds * 1000);
}
export function dateStartOfDay(date: Date): Date {
  return new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
  );
}
export function distinct<T>(
  input: T[],
  compare: (left: T, right: T) => boolean
): T[] {
  return input.filter((item, index) =>
    input.every(
      (other, otherIndex) => otherIndex >= index || !compare(item, other)
    )
  );
}

export function floatParse(value: string): number | null {
  const result = parseFloat(value);
  return Number.isNaN(result) ? null : result;
}

export function formatNumber(value: number, width: number) {
  const result = value.toString();
  return "0".repeat(Math.max(0, width - result.length)) + result;
}
export function intParse(value: string): number | null {
  const result = parseInt(value);
  return Number.isNaN(result) ? null : result;
}

export function jsonParse(value: string): any {
  try {
    return JSON.parse(value);
  } catch (_) {
    return null;
  }
}
export function mapNull<T, R>(
  reciever: T | null,
  transformer: (input: T) => R
): R | null {
  return reciever == null ? null : transformer(reciever);
}
export function mapNullOrDefault<T, R>(
  reciever: T | null,
  transformer: (input: T) => R,
  otherwise: R
): R {
  return reciever == null ? otherwise : transformer(reciever);
}
export function nullifyUndefined<T>(input: T | undefined): T | null {
  return input === undefined ? null : input;
}
export function optima<T, K>(
  input: T[],
  key: (input: T) => K,
  keepLeft: (left: K, right: K) => boolean
): T | null {
  if (input.length == 0) {
    return null;
  }
  let bestValue = input[0];
  let bestKey = key(input[0]);
  for (let i = 1; i < input.length; i++) {
    const newKey = key(input[i]);
    if (keepLeft(newKey, bestKey)) {
      bestKey = newKey;
      bestValue = input[i];
    }
  }

  return bestValue;
}
export function partitionCount<T>(
  input: T[],
  match: (input: T) => boolean
): { matched_count: number; non_matched_count: number } {
  const result = { matched_count: 0, non_matched_count: 0 };
  for (const item of input) {
    if (match(item)) {
      result.matched_count++;
    } else {
      result.non_matched_count++;
    }
  }
  return result;
}
export function pathChangePrefix(
  target: string,
  prefixes: [string, string][]
): string {
  let length = 0;
  let result = target;
  const targetParts = target.split("/");
  for (const [prefix, replacement] of prefixes) {
    const prefixParts = prefix.split("/");
    if (
      pathStartsWithArray(targetParts, prefixParts) &&
      length < prefixParts.length
    ) {
      length = prefixParts.length;
      result = [
        replacement,
        targetParts.slice(prefixParts.length, targetParts.length),
      ].join("/");
    }
  }
  return result;
}
export function pathEndsWith(check: string, suffix: string): boolean {
  const checkParts = check.split("/");
  const suffixParts = check.split("/");
  // This logic is taken from the JDK's UnixPath.java
  if (suffixParts.length > checkParts.length) return false;
  if (checkParts.length > 0 && suffixParts.length == 0) return false;
  if (checkParts.length == 0) {
    return suffixParts.length == 0;
  }

  // other path is absolute so this path must be absolute
  if (checkParts[0] == "" && suffixParts[0] != "") return false;

  // given path has more elements that this path
  if (suffixParts.length > checkParts.length) {
    return false;
  } else {
    // same number of elements
    if (suffixParts.length == checkParts.length) {
      if (checkParts.length == 0) return true;
      let expectedLen = checkParts.length;
      if (checkParts[0] == "" && suffixParts[0] != "") expectedLen--;
      if (checkParts.length != expectedLen) return false;
    } else {
      // this path has more elements so given path must be relative
      if ((suffixParts[0] = "")) return false;
    }
  }

  for (let i = 0; i < suffixParts.length; i++) {
    if (
      checkParts[checkParts.length - suffixParts.length + i] != suffixParts[i]
    ) {
      return false;
    }
  }
  return true;
}
export function pathFileName(path: string): string {
  const pathParts = path.split("/");
  return pathParts[pathParts.length - 1];
}
export function pathNormalize(path: string): string {
  let pathParts = path.split("/").filter((x) => x != ".");
  pathParts.reverse();
  for (let i = 0; i < pathParts.length; i++) {
    if (pathParts[i] == "..") {
      let upDirCounts = 1;
      while (
        upDirCounts + i < pathParts.length &&
        pathParts[upDirCounts + i] == ".."
      ) {
        upDirCounts++;
      }
      const deleteCount = Math.min(upDirCounts * 2, pathParts.length - i);
      pathParts.splice(i + deleteCount - upDirCounts * 2, deleteCount);
      i--;
    }
  }
  return pathParts.join("/");
}
export function pathParent(path: string): string {
  const position = path.lastIndexOf("/");
  return position == -1 ? "." : path.substring(0, position - 1);
}
export function pathRelativize(
  directory: string,
  target: string
): string | null {
  if (directory == target) {
    return ".";
  }
  const directoryParts = directory.split("/");
  const targetParts = target.split("/");
  if ((directoryParts[0] == "") != (targetParts[0] == "")) {
    return null;
  }

  // this path is the empty path
  if (directory == ".") return target;

  // skip matching names
  const n = Math.min(directory.length, target.length);
  let i = 0;
  while (i < n) {
    if (directoryParts[i] != targetParts[i]) {
      break;
    }
    i++;
  }

  const dotdots = directory.length - i;
  if (i < target.length) {
    return (
      "../".repeat(dotdots) +
      "/" +
      directoryParts.slice(i, target.length).join("/")
    );
  } else {
    return "../".repeat(dotdots);
  }
}
export function pathReplaceHome(path: string, home: string): string {
  return path.replace(/^(~|$HOME)\//, home);
}
export function pathResolve(root: string, child: string): string {
  if (child.startsWith("/")) {
    return child;
  }
  return `${root}/${child}`;
}
export function pathStartsWith(check: string, prefix: string): boolean {
  return pathStartsWithArray(check.split("/"), prefix.split("/"));
}
function pathStartsWithArray(path: string[], prefix: string[]): boolean {
  if (prefix.length > path.length) return false;

  for (let i = 0; i < prefix.length; i++) {
    if (prefix[i] != path[i]) {
      return false;
    }
  }

  return true;
}
export function range(start: number, end: number): number[] {
  const result = [];
  for (let i = start; i < end; i++) {
    result.push(i);
  }
  return result;
}
export function regexBind(
  regex: RegExp,
  input: string,
  captures: number
): (string | null)[] | null {
  const result = input.match(regex);
  if (result == null) {
    return null;
  } else {
    const tuple = new Array(captures);
    tuple.fill(null);
    for (let i = 0; i < captures; i++) {
      if (result[i + 1] !== undefined) {
        tuple[i] = result[i + 1];
      }
    }
    return tuple;
  }
}
export function replaceUndefined<T>(value: T | undefined): T | null {
  return value === undefined ? null : value;
}
export function setAdd<T>(
  set: T[],
  item: T,
  compare: (a: T, b: T) => number
): T[] {
  return setNew(set.concat([item]), compare);
}
export function setDifference<T>(
  left: T[],
  right: T[],
  compare: (a: T, b: T) => number
): T[] {
  return left.filter((v) => !setContains(right, v, compare));
}
export function dictCompare<K, V>(
  left: [K, V][] | { K: V },
  right: [K, V][] | { K: V },
  compare: (ak: K, av: V, bk: K, bv: V) => number
): number {
  let leftEntries = Array.isArray(left)
    ? left
    : (Object.entries(left) as unknown as [K, V][]);
  let rightEntries = Array.isArray(right)
    ? right
    : (Object.entries(right) as unknown as [K, V][]);
  if (leftEntries.length == rightEntries.length) {
    for (let i = 0; i < leftEntries.length; i++) {
      const result = compare(
        leftEntries[i][0],
        leftEntries[i][1],
        rightEntries[i][0],
        rightEntries[i][1]
      );
      if (result != 0) {
        return result;
      }
    }
    return 0;
  } else {
    return 0;
  }
}
export function dictContains<K, V>(
  haystack: [K, V][] | { K: V },
  needle: K,
  compare: (a: K, b: K) => number
): boolean {
  if (Array.isArray(haystack)) {
    return binarySearch(
      haystack,
      (item) => compare(item[0], needle),
      0,
      haystack.length - 1
    );
  } else {
    return haystack.hasOwnProperty(needle as unknown as string);
  }
}
export function dictEqual<K, V>(
  left: [K, V][] | { K: V },
  right: [K, V][] | { K: V },
  compare: (ak: K, av: V, bk: K, bv: V) => boolean
): boolean {
  let leftEntries = Array.isArray(left)
    ? left
    : (Object.entries(left) as unknown as [K, V][]);
  let rightEntries = Array.isArray(right)
    ? right
    : (Object.entries(right) as unknown as [K, V][]);
  if (leftEntries.length == rightEntries.length) {
    for (let i = 0; i < leftEntries.length; i++) {
      if (
        !compare(
          leftEntries[i][0],
          leftEntries[i][1],
          rightEntries[i][0],
          rightEntries[i][1]
        )
      ) {
        return false;
      }
    }
    return true;
  } else {
    return false;
  }
}
export function dictNew<K, V>(
  items: [K, V][],
  compare: (a: K, b: K) => number
): [K, V][] {
  return items
    .sort((a, b) => compare(a[0], b[0]))
    .filter(
      (item, index, arr) =>
        index == 0 || compare(item[0], arr[index - 1][0]) != 0
    );
}
export function dictIterator<K, V>(items: [K, V][] | { K: V }): [K, V][] {
  return Array.isArray(items)
    ? items
    : (Object.entries(items) as unknown as [K, V][]);
}
export function setCompare<T>(
  left: T[],
  right: T[],
  compare: (a: T, b: T) => number
): number {
  if (left.length == right.length) {
    for (let i = 0; i < left.length; i++) {
      const result = compare(left[i], right[i]);
      if (result != 0) {
        return result;
      }
    }
    return 0;
  } else {
    return 0;
  }
}
export function setContains<T>(
  haystack: T[],
  needle: T,
  compare: (a: T, b: T) => number
): boolean {
  return binarySearch(
    haystack,
    (item) => compare(item, needle),
    0,
    haystack.length - 1
  );
}
export function setEqual<T>(
  left: T[],
  right: T[],
  compare: (a: T, b: T) => boolean
): boolean {
  if (left.length == right.length) {
    for (let i = 0; i < left.length; i++) {
      if (!compare(left[i], right[i])) {
        return false;
      }
    }
    return true;
  } else {
    return false;
  }
}
export function setNew<T>(items: T[], compare: (a: T, b: T) => number): T[] {
  return items
    .sort(compare)
    .filter(
      (item, index, arr) => index == 0 || compare(item, arr[index - 1]) != 0
    );
}
export function setRemove<T>(
  set: T[],
  item: T,
  compare: (a: T, b: T) => number
): T[] {
  return set.filter((v) => compare(v, item) != 0);
}
export function setUnion<T>(
  left: T[],
  right: T[],
  compare: (a: T, b: T) => number
): T[] {
  return setNew(left.concat(right), compare);
}
export function stringHash(input: string): number {
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    const chr = input.charCodeAt(i);
    hash = (hash << 5) - hash + chr;
    hash |= 0;
  }
  return hash;
}
export function stringTruncate(input: string, length: number): string {
  return input.substring(0, Math.min(input.length, length));
}
type SubSampler<T> = (input: T[], output: T[]) => number;

export function subsample<T>(input: T[], subsampler: SubSampler<T>): T[] {
  const output: T[] = [];
  subsampler(input, output);
  return output;
}
export function subsampleFixed<T>(
  parent: SubSampler<T>,
  numberOfItems: number
): SubSampler<T> {
  return (input, output) => {
    const position = parent(input, output);
    let counter;
    for (
      counter = 0;
      position + counter < input.length && counter < numberOfItems;
      counter++
    ) {
      output.push(input[position + counter]);
    }
    return position + counter;
  };
}
export function subsampleFixedWithConditions<T>(
  parent: SubSampler<T>,
  numberOfItems: number,
  test: (input: T) => boolean
): SubSampler<T> {
  return (input, output) => {
    const position = parent(input, output);
    let counter;
    for (
      counter = 0;
      position + counter < input.length && counter < numberOfItems;
      counter++
    ) {
      const item = input[position + counter];
      if (!test(item)) {
        break;
      }
      output.push(item);
    }
    return position + counter;
  };
}
export function subsampleStart<T>(): SubSampler<T> {
  return (input, output) => 0;
}
export function subsampleSquish<T>(
  parent: SubSampler<T>,
  numberOfItems: number
): SubSampler<T> {
  return (input, output) => {
    const position = parent(input, output);
    if (input.length - position <= numberOfItems) {
      for (let index = position; index < input.length; index++) {
        output.push(input[index]);
      }
      return input.length;
    }
    const step = Math.trunc((input.length - position) / numberOfItems);
    let counter;
    for (
      counter = 0;
      position + counter * step < input.length && counter < numberOfItems;
      counter++
    ) {
      output.push(input[position + counter * step]);
    }
    return position + counter;
  };
}

export interface SummaryStatistics {
  average: number;
  count: number;
  maximum: number;
  minimum: number;
  sum: number;
}
export function summaryStatistics(input: number[]): SummaryStatistics {
  const sum = input.reduce((a, v) => a + v, 0);
  return {
    average: sum / input.length,
    count: input.length,
    maximum: Math.max(...input),
    minimum: Math.min(...input),
    sum,
  };
}
export function univalued<T>(
  input: T[],
  compare: (left: T, right: T) => boolean
): T | null {
  if (input.length == 0) {
    return null;
  }
  let value = input[0];
  for (let i = 1; i < input.length; i++) {
    if (!compare(value, input[i])) {
      return null;
    }
  }
  return value;
}

export function versionAtLeast(
  version: [number, number, number],
  major: number,
  minor: number,
  patch: number
): boolean {
  if (version[0] < major) {
    return false;
  }
  if (version[0] == major) {
    if (version[1] < minor) {
      return false;
    }
    if (version[1] == minor) {
      return version[2] >= patch;
    } else {
      return true;
    }
  } else {
    return true;
  }
}

export function zip<K, T extends [K], U extends [K], R>(
  left: T[],
  right: U[],
  merger: (left: T | undefined, right: U | undefined) => R
): R[] {
  const leftMap = new Map<string, T>();
  const rightMap = new Map<string, U>();
  for (const l of left) {
    leftMap.set(JSON.stringify(l[0]), l);
  }

  for (const r of right) {
    rightMap.set(JSON.stringify(r[0]), r);
  }

  const keys = new Set(leftMap.keys());
  for (const k of rightMap.keys()) {
    keys.add(k);
  }

  return Array.from(keys.values()).map((k) =>
    merger(leftMap.get(k), rightMap.get(k))
  );
}

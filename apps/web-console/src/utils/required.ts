/**
 * Runtime replacement for the `!` non-null assertion.
 *
 * `value!` silently produces `undefined` downstream when the invariant is wrong; `required()`
 * fails loudly at the call site with a message that names the missing value. Use it where the
 * invariant is guaranteed by control flow the type checker cannot see (react-query `enabled`
 * guards, map entries populated in the same loop, DOM roots from index.html).
 */
export function required<T>(value: T | null | undefined, what: string): T {
  if (value === null || value === undefined) {
    throw new Error(`${what} is required but was ${value === null ? "null" : "undefined"}`);
  }
  return value;
}

/** `Map.get` with insert-on-miss; avoids `map.get(key)!.push(...)` after `map.set`. */
export function getOrCreate<K, V>(map: Map<K, V>, key: K, create: () => V): V {
  const existing = map.get(key);
  if (existing !== undefined) return existing;
  const created = create();
  map.set(key, created);
  return created;
}

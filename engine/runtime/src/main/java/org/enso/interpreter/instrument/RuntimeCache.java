package org.enso.interpreter.instrument;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A storage for computed values. */
public class RuntimeCache {

  private final Map<UUID, SoftReference<Object>> cache = new HashMap<>();
  private Map<UUID, Long> weights = new HashMap<>();

  /**
   * Add value to the cache if it is possible.
   *
   * @param key the key of an entry.
   * @param value the added value.
   * @return {@code true} if the value was added to the cache.
   */
  public boolean offer(UUID key, Object value) {
    if (weights.getOrDefault(key, 0L) > 0) {
      cache.put(key, new SoftReference<>(value));
      return true;
    }
    return false;
  }

  /** Get the value from the cache. */
  public Object get(UUID key) {
    SoftReference<Object> ref = cache.get(key);
    return ref != null ? ref.get() : null;
  }

  /** Remove the value from the cache. */
  public Object remove(UUID key) {
    SoftReference<Object> ref = cache.remove(key);
    return ref == null ? null : ref.get();
  }

  /** @return all cache keys. */
  public Set<UUID> getKeys() {
    return cache.keySet();
  }

  /** Clear the cached values. */
  public void clear() {
    cache.clear();
  }

  /** @return the weights of this cache. */
  public Map<UUID, Long> getWeights() {
    return weights;
  }

  /** Set the new weights. */
  public void setWeights(Map<UUID, Long> weights) {
    this.weights = weights;
  }

  /** Remove the weight associated with the provided key. */
  public void removeWeight(UUID key) {
    weights.remove(key);
  }

  /** Clear the weights. */
  public void clearWeights() {
    weights.clear();
  }
}

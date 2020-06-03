package org.enso.interpreter.instrument;

import org.enso.interpreter.node.callable.FunctionCallInstrumentationNode;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A storage for computed values. */
public class RuntimeCache {

  private final Map<UUID, SoftReference<Object>> cache = new HashMap<>();
  private final Map<UUID, FunctionCallInstrumentationNode.FunctionCall> enterables =
      new HashMap<>();
  private Map<UUID, Double> weights = new HashMap<>();

  /**
   * Add value to the cache if it is possible.
   *
   * @param key the key of an entry.
   * @param value the added value.
   * @return {@code true} if the value was added to the cache.
   */
  public boolean offer(UUID key, Object value) {
    Double weight = weights.get(key);
    if (weight != null && weight > 0) {
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

  /**
   * Put a new enterable.
   *
   * @param key identifier of an enterable node.
   * @param enterable the fucntion call
   */
  public void putEnterable(UUID key, FunctionCallInstrumentationNode.FunctionCall enterable) {
    enterables.put(key, enterable);
  }

  /**
   * Get enterable by id.
   *
   * @param key identifier of an enterable node.
   * @return function call associated with node id.
   */
  public FunctionCallInstrumentationNode.FunctionCall getEnterable(UUID key) {
    return enterables.get(key);
  }

  /**
   * Remove enterable.
   *
   * @param key identifier of an enterable node.
   */
  public void removeEnterable(UUID key) {
    enterables.remove(key);
  }

  /** Clear all enterables. */
  public void clearEnterables() {
    enterables.clear();
  }

  /** @return the weights of this cache. */
  public Map<UUID, Double> getWeights() {
    return weights;
  }

  /** Set the new weights. */
  public void setWeights(Map<UUID, Double> weights) {
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

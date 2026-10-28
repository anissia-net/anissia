import {onScopeDispose} from 'vue';

/** Patch an item in mounted or cached lists without discarding the loaded pages. */
export function createListUpdates<T>() {
  const listeners = new Set<(id: number, changes: Partial<T> | null) => void>();
  return {
    publish(id: number, changes: Partial<T> | null) {
      for (const listener of listeners) listener(id, changes);
    },
    subscribe(listener: (id: number, changes: Partial<T> | null) => void) {
      listeners.add(listener);
      onScopeDispose(() => listeners.delete(listener));
    },
  };
}

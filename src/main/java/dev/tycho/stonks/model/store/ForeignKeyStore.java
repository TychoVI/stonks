package dev.tycho.stonks.model.store;

import java.util.Collection;

public interface ForeignKeyStore<P extends Entity, C extends Entity> {

  void populate();

  Collection<C> getChildren(P parent);

  P getParent(C child);
  Collection<C> putParent(P parent);
  void putRelation(C child);
}

package dev.tycho.stonks.model.core;

import dev.tycho.stonks.model.accountvisitors.IAccountVisitor;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
public abstract class Account {

  @Getter
  private int id;
  @Getter
  private UUID uuid;
  @Getter
  private String name;

  public abstract void addBalance(double amount);

  public abstract double getTotalBalance();

  public abstract void accept(IAccountVisitor visitor);


}

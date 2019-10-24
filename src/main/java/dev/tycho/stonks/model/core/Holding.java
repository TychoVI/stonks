package dev.tycho.stonks.model.core;

import lombok.Getter;

import java.util.UUID;

// A holding represents a share of an account held by a player
// This is done by ratio ( share1 : share2 : ...) so percentages do not need to be saved
// A player can withdraw as much money as is in the holding
public class Holding {

  @Getter
  private int id;
  @Getter
  private UUID player;
  @Getter
  private double balance;
  @Getter
  private double share;
  @Getter
  private int holdingsAccountId;

  public Holding() {
  }

  public Holding(int id, UUID playerUuid, double balance, double share, HoldingsAccount holdingsAccount) {
    if (share <= 0) {
      share = 1;
      System.out.println("Holding created with a 0 share");
    }
    this.id = id;
    this.player = playerUuid;
    this.balance = balance;
    this.share = share;
    this.holdingsAccountId = holdingsAccount.getId();
  }

  public boolean setShare(double share) {
    if (share > 0) {
      this.share = share;
      return true;
    } else {
      //Share must be positive and not 0
      return false;
    }
  }

  public void payIn(double amount) {
    this.balance += amount;
  }

  public boolean subtractBalance(double amount) {
    if (balance < amount) return false;
    balance -= amount;
    return true;
  }
}


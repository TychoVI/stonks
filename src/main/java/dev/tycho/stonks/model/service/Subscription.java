package dev.tycho.stonks.model.service;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

@AllArgsConstructor
public class Subscription {

  @Getter
  private int id;
  @Getter
  private int serviceId;
  @Getter
  private UUID playerId;
  @Getter
  private Date lastPaymentDate;

  public boolean isOverdue() {
    return (getDaysOverdue() > 0);
  }

  //Will return negative for a non-overdue date
  public double getDaysOverdue() {
    long millisDifference = Calendar.getInstance().getTimeInMillis() - lastPaymentDate.getTime();
    return ((double) millisDifference / 86400000) - service.getDuration();
  }

  public void registerPaid() {
    this.lastPaymentDate = new Timestamp(Calendar.getInstance().getTime().getTime());
  }
}

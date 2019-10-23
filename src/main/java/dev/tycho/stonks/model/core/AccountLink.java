package dev.tycho.stonks.model.core;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import dev.tycho.stonks.model.accountvisitors.IAccountVisitor;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
public class AccountLink {
  @Getter
  private int id;
  @Getter
  private UUID companyId;
  @Getter
  private int companyAccountId;
  @Getter
  private int holdingsAccountId;


  public Account getAccount() {
    return (companyAccount != null) ? companyAccount : holdingsAccount;
  }

  private AccountType getAccountType() {
    return (companyAccount != null) ? AccountType.CompanyAccount : AccountType.HoldingsAccount;
  }

  public Company getCompany() {
    return company;
  }

}

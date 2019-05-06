/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecuregcm.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.Transaction;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.whispersystems.textsecuregcm.util.SystemMapper;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

public abstract class Accounts {

  private static final String ID     = "id";
  private static final String NUMBER = "number";
  private static final String DATA   = "data";

  private static final ObjectMapper mapper = SystemMapper.getMapper();

  @SqlUpdate("INSERT INTO accounts (" + NUMBER + ", " + DATA + ") VALUES (:number, CAST(:data AS json))")
  abstract void insertStep(@AccountBinder Account account);

  @SqlUpdate("DELETE FROM accounts WHERE " + NUMBER + " = :number")
  abstract int removeAccount(@Bind("number") String number);

  @SqlUpdate("UPDATE accounts SET " + DATA + " = CAST(:data AS json) WHERE " + NUMBER + " = :number")
  abstract void update(@AccountBinder Account account);

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts WHERE " + NUMBER + " = :number")
  public abstract Account get(@Bind("number") String number);

  @SqlQuery("SELECT COUNT(DISTINCT " + NUMBER + ") from accounts")
  public abstract long getCount();

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts OFFSET :offset LIMIT :limit")
  abstract List<Account> getAll(@Bind("offset") int offset, @Bind("limit") int length);

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts")
  public abstract Iterator<Account> getAll();

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts ORDER BY " + NUMBER + " LIMIT :limit")
  public abstract List<Account> getAllFrom(@Bind("limit") int length);

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts WHERE " + NUMBER + " > :from ORDER BY " + NUMBER + " LIMIT :limit")
  public abstract List<Account> getAllFrom(@Bind("from") String from, @Bind("limit") int length);

  @Transaction(TransactionIsolationLevel.SERIALIZABLE)
  public boolean create(Account account) {
    int rows = removeAccount(account.getNumber());
    insertStep(account);

    return rows == 0;
  }

  @SqlUpdate("VACUUM accounts")
  public abstract void vacuum();

  public static class AccountMapper implements ResultSetMapper<Account> {
    @Override
    public Account map(int i, ResultSet resultSet, StatementContext statementContext)
        throws SQLException
    {
      try {
        Account account = mapper.readValue(resultSet.getString(DATA), Account.class);
        account.setNumber(resultSet.getString(NUMBER));

        return account;
      } catch (IOException e) {
        throw new SQLException(e);
      }
    }
  }

  @BindingAnnotation(AccountBinder.AccountBinderFactory.class)
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.PARAMETER})
  public @interface AccountBinder {
    public static class AccountBinderFactory implements BinderFactory {
      @Override
      public Binder build(Annotation annotation) {
        return new Binder<AccountBinder, Account>() {
          @Override
          public void bind(SQLStatement<?> sql,
                           AccountBinder accountBinder,
                           Account account)
          {
            try {
              String serialized = mapper.writeValueAsString(account);

              sql.bind(NUMBER, account.getNumber());
              sql.bind(DATA, serialized);
            } catch (JsonProcessingException e) {
              throw new IllegalArgumentException(e);
            }
          }
        };
      }
    }
  }

}

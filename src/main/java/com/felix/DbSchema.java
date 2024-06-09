package com.felix;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DbSchema {
    public static void setup(DataSource dataSource) throws SQLException {

        Connection connection = dataSource.getConnection();
        Statement stmt = connection.createStatement();

        String sql = """
                create table if not exists account_balances
                (
                    account_id integer        not null
                        constraint account_balances_pk
                            primary key autoincrement,
                    user_id    integer        not null,
                    balance    real default 0 not null,
                    created_at TEXT default (datetime('now')),
                    updated_at TEXT default (datetime('now'))
                );
                """;

        stmt.execute(sql);

        sql = """
                create table if not exists transaction_logs
                (
                    id         integer                           not null
                        constraint transaction_logs_pk
                            primary key,
                    account_id integer                           not null,
                    amount     real                              not null,
                    type       integer default 0                 not null,
                    details    text,
                    created_at text    default (datetime('now')) not null,
                    updated_at text    default (datetime('now')) not null
                );
                   """;
        stmt.execute(sql);

        sql = """
                create table if not exists withdrawal_requests
                (
                    id              integer                           not null
                        constraint withdrawal_requests_pk
                            primary key autoincrement,
                    from_account_id integer                           not null,
                    withdrawal_id   TEXT                              not null,
                    to_address      TEXT                              not null,
                    amount          real                              not null,
                    status          INTEGER default 0                 not null,
                    created_at      text    default (datetime('now')) not null,
                    updated_at      TEXT    default (datetime('now')) not null
                );
                                
                create unique index withdrawal_requests_withdrawal_id_uindex
                    on withdrawal_requests (withdrawal_id);
                """;
        stmt.execute(sql);
    }
}

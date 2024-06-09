package com.felix.dao;

import com.felix.exception.TransactionFailedErrorCode;
import com.felix.exception.TransactionFailedException;
import com.felix.model.TransactionLogModel;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class BalanceDAO {

    private final Connection conn;

    public BalanceDAO(Connection conn) {
        this.conn = conn;
    }


    public void subtractFromFromAccount(int fromAccountId, BigDecimal amount) throws TransactionFailedException, SQLException {
        String subtractFromAccountSql = "update account_balances set balance = balance - ? where account_id = ? and balance >= ?";
        PreparedStatement stmt = conn.prepareStatement(subtractFromAccountSql);
        stmt.setBigDecimal(1, amount);
        stmt.setInt(2, fromAccountId);
        stmt.setBigDecimal(3, amount);

        int updatedCount = stmt.executeUpdate();
        if (updatedCount <= 0) {
            throw new TransactionFailedException(TransactionFailedErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    public void addToToAccount(int toAccountId, BigDecimal amount) throws SQLException, TransactionFailedException {
        String addToAccountSql = "update account_balances set balance = balance + ? where account_id = ?";
        PreparedStatement stmt = conn.prepareStatement(addToAccountSql);
        stmt.setBigDecimal(1, amount);
        stmt.setInt(2, toAccountId);

        int updatedCount = stmt.executeUpdate();
        if (updatedCount <= 0) {
            throw new TransactionFailedException(TransactionFailedErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    public TransactionLogModel insertTransactionLog(TransactionLogModel transactionLogModel) throws SQLException {
        String insertTransactionLogSql = "insert into transaction_logs (account_id, amount, type, details) values (?, ?, ?, ?)";
        PreparedStatement insertLogStmt = conn.prepareStatement(insertTransactionLogSql);
        insertLogStmt.setInt(1, transactionLogModel.getAccountId());
        insertLogStmt.setBigDecimal(2, transactionLogModel.getAmount());
        insertLogStmt.setInt(3, transactionLogModel.getType().getCode());
        insertLogStmt.setString(4, transactionLogModel.getDetails());

        insertLogStmt.executeUpdate();
        ResultSet generatedKeys = insertLogStmt.getGeneratedKeys();

        // get inserted transactionLog id
        int transactionLogId = generatedKeys.getInt(1);

        transactionLogModel.setId(transactionLogId);
        return transactionLogModel;
    }

    /**
     * use for testing, ideally this should be done in a separate service
     */
    public void setupAccount(int accountId, int userId, BigDecimal initialBalance) throws TransactionFailedException {

        try {
            String deleteExistingAccountSql = "delete from account_balances where account_id = ?";
            PreparedStatement stmt = conn.prepareStatement(deleteExistingAccountSql);
            stmt.setString(1, String.valueOf(accountId));
            stmt.executeUpdate();

            String insertAccountSql = "insert into account_balances (account_id, user_id, balance) values (?, ?, ?)";
            stmt = conn.prepareStatement(insertAccountSql);
            stmt.setInt(1, accountId);
            stmt.setInt(2, userId);
            stmt.setBigDecimal(3, initialBalance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new TransactionFailedException(TransactionFailedErrorCode.SYSTEM_ERROR);
        }
    }

    public Map<String, BigDecimal> getAllAccountBalances() throws TransactionFailedException {
        try {
            String getBalanceSql = "select account_id, balance from account_balances";
            PreparedStatement stmt = conn.prepareStatement(getBalanceSql);
            ResultSet rs = stmt.executeQuery();
            Map<String, BigDecimal> accountBalances = new HashMap<>();
            while (rs.next()) {
                accountBalances.put(rs.getString("account_id"), rs.getBigDecimal("balance"));
            }
            return accountBalances;
        } catch (SQLException e) {
            // log error
            throw new TransactionFailedException(TransactionFailedErrorCode.SYSTEM_ERROR);
        }
    }


    public BigDecimal getAccountBalance(int accountId) throws TransactionFailedException {
        try {
            String getBalanceSql = "select balance from account_balances where account_id = ?";
            PreparedStatement stmt = conn.prepareStatement(getBalanceSql);
            stmt.setInt(1, accountId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getBigDecimal("balance");
            }
        } catch (SQLException e) {
            // log error
            throw new TransactionFailedException(TransactionFailedErrorCode.SYSTEM_ERROR);
        }
        return null;
    }

}

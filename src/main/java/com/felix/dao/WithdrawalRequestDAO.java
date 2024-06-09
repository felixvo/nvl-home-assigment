package com.felix.dao;

import com.felix.exception.TransactionFailedErrorCode;
import com.felix.exception.TransactionFailedException;
import com.felix.model.WithdrawalRequestModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WithdrawalRequestDAO {
    private final Connection conn;

    public WithdrawalRequestDAO(Connection conn) {
        this.conn = conn;
    }


    public void createWithdrawalRequest(WithdrawalRequestModel withdrawalRequest) throws SQLException, TransactionFailedException {
        PreparedStatement stmt = conn.prepareStatement(
                """
                             insert into withdrawal_requests (from_account_id, withdrawal_id, to_address, amount)
                             values (?, ?, ?, ?)
                        """
        );
        stmt.setInt(1, withdrawalRequest.getFromAccountId());
        stmt.setString(2, withdrawalRequest.getWithdrawalId());
        stmt.setString(3, withdrawalRequest.getToAddress());
        stmt.setBigDecimal(4, withdrawalRequest.getAmount());
        int inserted = stmt.executeUpdate();
        if (inserted <= 0) {
            throw new TransactionFailedException(
                    TransactionFailedErrorCode.SYSTEM_ERROR,
                    "failed to create withdrawal request"
            );
        }
    }

    public List<String> getListPendingWithdrawalIds() {
        try {
            PreparedStatement preparedStatement = conn.prepareStatement(
                    """
                            select withdrawal_id from withdrawal_requests where status = 0
                            """
            );
            var rs = preparedStatement.executeQuery();
            List<String> withdrawalIds = new ArrayList<>();
            while (rs.next()) {
                withdrawalIds.add(rs.getString("withdrawal_id"));
            }
            return withdrawalIds;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setStatus(String withdrawalId, int status) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(
                """
                        update withdrawal_requests set status = ? where withdrawal_id = ?
                        """
        );
        preparedStatement.setInt(1, status);
        preparedStatement.setString(2, withdrawalId);
        preparedStatement.executeUpdate();
    }

    public WithdrawalRequestModel getByWithdrawalId(String withdrawalId) {
        try {
            PreparedStatement preparedStatement = conn.prepareStatement(
                    """
                            select * from withdrawal_requests where withdrawal_id = ?
                            """
            );
            preparedStatement.setString(1, withdrawalId);
            var rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return WithdrawalRequestModel.builder()
                        .id(rs.getInt("id"))
                        .fromAccountId(rs.getInt("from_account_id"))
                        .withdrawalId(rs.getString("withdrawal_id"))
                        .toAddress(rs.getString("to_address"))
                        .amount(rs.getBigDecimal("amount"))
                        .status(rs.getInt("status"))
                        .build();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}

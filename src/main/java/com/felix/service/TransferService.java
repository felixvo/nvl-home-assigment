package com.felix.service;

import com.felix.dao.BalanceDAO;
import com.felix.dao.WithdrawalRequestDAO;
import com.felix.dto.RequestWithdrawalResponse;
import com.felix.dto.TransferRequest;
import com.felix.dto.WithdrawalRequest;
import com.felix.dto.WithdrawalResponseDto;
import com.felix.exception.TransactionFailedException;
import com.felix.external.WithdrawalService;
import com.felix.model.TransactionLogModel;
import com.felix.model.TransactionLogType;
import com.felix.model.WithdrawalRequestModel;
import com.felix.model.WithdrawalRequestStatusEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TransferService {
    private final DataSource dataSource;
    private final WithdrawalService withdrawalService;

    public TransferService(
            DataSource dataSource,
            WithdrawalService withdrawalService) {
        this.dataSource = dataSource;
        this.withdrawalService = withdrawalService;
    }

    public Response<TransactionLogModel> transfer(TransferRequest transferRequest) {
        validateTransferReq(transferRequest);
        Connection conn = null;
        try {
            conn = getDbConnection();
            // start transaction
            conn.setAutoCommit(false);

            BalanceDAO balanceDAO = new BalanceDAO(conn);
            int fromAccountId = transferRequest.getFromAccountId();
            int toAccountId = transferRequest.getToAccountId();
            BigDecimal amount = transferRequest.getAmount();

            // to avoid deadlocks, we will always lock the account with the lower id first
            if (fromAccountId > toAccountId) {
                balanceDAO.addToToAccount(toAccountId, amount);
                balanceDAO.subtractFromFromAccount(fromAccountId, amount);
            } else {
                balanceDAO.subtractFromFromAccount(fromAccountId, amount);
                balanceDAO.addToToAccount(toAccountId, amount);
            }

            // insert into transaction log
            TransactionLogModel transactionLogModel = TransactionLogModel.builder()
                    .accountId(fromAccountId)
                    .amount(amount)
                    .type(TransactionLogType.TRANSER)
                    .details("transfer to account " + toAccountId)
                    .build();
            balanceDAO.insertTransactionLog(transactionLogModel);

            conn.commit();
            return Response.success(transactionLogModel);
        } catch (TransactionFailedException e) {
            tryRollback(conn);
            switch (e.getErrorCode()) {
                case INSUFFICIENT_BALANCE:
                    return Response.error(ErrorCode.INSUFFICIENT_BALANCE);
                case ACCOUNT_NOT_FOUND:
                    return Response.error(ErrorCode.ACCOUNT_NOT_FOUND);
            }
        } catch (SQLException e) {
            tryRollback(conn);
        } finally {
            tryCloseConn(conn);
        }
        return Response.error(ErrorCode.SYSTEM_ERROR, "system error");
    }


    public Response<RequestWithdrawalResponse> requestWithdrawal(WithdrawalRequest withdrawalRequest) {
        validateWithdrawalReq(withdrawalRequest);
        UUID withdrawalUUID = UUID.randomUUID();

        Connection conn = null;
        try {
            conn = getDbConnection();
            // start transaction
            conn.setAutoCommit(false);

            BalanceDAO balanceDAO = new BalanceDAO(conn);
            WithdrawalRequestDAO withdrawalRequestDAO = new WithdrawalRequestDAO(conn);

            WithdrawalRequestModel reqEntity = WithdrawalRequestModel.builder()
                    .fromAccountId(withdrawalRequest.getFromAccountId())
                    .withdrawalId(withdrawalUUID.toString())
                    .toAddress(withdrawalRequest.getAddress())
                    .amount(withdrawalRequest.getAmount())
                    .status(WithdrawalRequestStatusEnum.REQUESTED.getCode())
                    .build();
            withdrawalRequestDAO.createWithdrawalRequest(reqEntity);
            balanceDAO.subtractFromFromAccount(
                    withdrawalRequest.getFromAccountId(),
                    withdrawalRequest.getAmount()
            );


            WithdrawalService.WithdrawalId withdrawalId = new WithdrawalService.WithdrawalId(withdrawalUUID);
            WithdrawalService.Address address = new WithdrawalService.Address(withdrawalRequest.getAddress());

            // request external service
            withdrawalService.requestWithdrawal(
                    withdrawalId,
                    address,
                    withdrawalRequest.getAmount()
            );

            conn.commit();
        } catch (TransactionFailedException e) {
            tryRollback(conn);
            switch (e.getErrorCode()) {
                case INSUFFICIENT_BALANCE:
                    return Response.error(ErrorCode.INSUFFICIENT_BALANCE);
                case ACCOUNT_NOT_FOUND:
                    return Response.error(ErrorCode.ACCOUNT_NOT_FOUND);
            }
        } catch (SQLException | IllegalArgumentException e) {
            tryRollback(conn);
        } finally {
            tryCloseConn(conn);
        }

        return Response.success(
                RequestWithdrawalResponse.builder()
                        .withdrawalId(withdrawalUUID.toString())
                        .build()
        );
    }

    public List<String> getListPendingWithdrawalRequest() {
        Connection conn = null;
        try {
            conn = getDbConnection();
            WithdrawalRequestDAO withdrawalRequestDAO = new WithdrawalRequestDAO(conn);
            return withdrawalRequestDAO.getListPendingWithdrawalIds();
        } catch (Exception e) {
            // log error
        } finally {
            tryCloseConn(conn);
        }
        return Collections.emptyList();
    }

    public void syncWithdrawalRequestStatus(String withdrawalId) {
        Connection conn = null;
        try {
            System.out.println("syncing withdrawal request status for withdrawalId: " + withdrawalId);
            conn = getDbConnection();
            conn.setAutoCommit(false);
            WithdrawalRequestDAO withdrawalRequestDAO = new WithdrawalRequestDAO(conn);
            BalanceDAO balanceDAO = new BalanceDAO(conn);

            WithdrawalRequestModel withdrawalRequestEntity = withdrawalRequestDAO.getByWithdrawalId(withdrawalId);
            if (withdrawalRequestEntity == null) {
                return;
            }
            WithdrawalService.WithdrawalState requestState = withdrawalService.getRequestState(
                    new WithdrawalService.WithdrawalId(UUID.fromString(withdrawalId))
            );

            switch (requestState) {
                case COMPLETED -> withdrawalRequestDAO.setStatus(
                        withdrawalId,
                        WithdrawalRequestStatusEnum.SUCCESS.getCode()
                );
                case FAILED -> {
                    withdrawalRequestDAO.setStatus(
                            withdrawalId,
                            WithdrawalRequestStatusEnum.FAILED.getCode()
                    );
                    balanceDAO.addToToAccount(
                            withdrawalRequestEntity.getFromAccountId(),
                            withdrawalRequestEntity.getAmount()
                    );
                }
            }

            conn.commit();

            System.out.println("[DONE] syncing withdrawal request status for withdrawalId: " + withdrawalId);
        } catch (SQLException e) {
            tryRollback(conn);
        } catch (TransactionFailedException e) {
            // log error
            tryRollback(conn);
        } finally {
            tryCloseConn(conn);
        }
    }

    public Response<WithdrawalResponseDto> getWithdrawalRequest(String withdrawalId) {
        Connection conn = null;
        try {
            conn = getDbConnection();
            WithdrawalRequestModel withdrawalRequestEntity = new WithdrawalRequestDAO(conn).getByWithdrawalId(withdrawalId);

            if (withdrawalRequestEntity == null) {
                return Response.error(ErrorCode.RESOURCE_NOT_FOUND, "withdrawal not found");
            }

            return Response.success(
                    WithdrawalResponseDto.builder()
                            .id(withdrawalRequestEntity.getId())
                            .fromAccountId(withdrawalRequestEntity.getFromAccountId())
                            .withdrawalId(withdrawalRequestEntity.getWithdrawalId())
                            .toAddress(withdrawalRequestEntity.getToAddress())
                            .amount(withdrawalRequestEntity.getAmount())
                            .status(
                                    WithdrawalRequestStatusEnum.fromCode(
                                            withdrawalRequestEntity.getStatus()
                                    )
                            )
                            .build());
        } catch (SQLException e) {
            // log error
        } finally {
            tryCloseConn(conn);
        }
        return Response.error(ErrorCode.SYSTEM_ERROR, "system error");
    }

    private void validateWithdrawalReq(WithdrawalRequest withdrawalRequest) {
        // validation logic
        if (withdrawalRequest.getAmount() == null) {
            throw new IllegalArgumentException("invalid amount");
        }
        if (withdrawalRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("negative amount");
        }
    }

    private void validateTransferReq(TransferRequest transferRequest) {
        if (transferRequest.getAmount() == null) {
            throw new RuntimeException("invalid amount");
        }
        if (transferRequest.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("invalid amount");
        }
        // check if fromAccountId and toAccountId are valid
    }


    private Connection getDbConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void tryCloseConn(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            // log error
        }
    }


    private void tryRollback(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.rollback();
        } catch (SQLException ex) {
            // log error
        }
    }
}

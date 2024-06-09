package com.felix;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.felix.dao.BalanceDAO;
import com.felix.dto.RequestWithdrawalResponse;
import com.felix.dto.TransferRequest;
import com.felix.dto.WithdrawalRequest;
import com.felix.dto.WithdrawalResponseDto;
import com.felix.exception.TransactionFailedException;
import com.felix.external.WithdrawalService;
import com.felix.external.WithdrawalServiceStub;
import com.felix.model.TransactionLogModel;
import com.felix.service.Response;
import com.felix.service.TransferService;
import com.zaxxer.hikari.HikariDataSource;
import org.rapidoid.data.JSON;
import org.rapidoid.http.Req;
import org.rapidoid.http.Resp;
import org.rapidoid.setup.App;
import org.rapidoid.setup.On;
import org.rapidoid.u.U;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws SQLException, TransactionFailedException {
        App.bootstrap(args);
        String dbFilename = "sample.db";
        HikariDataSource dataSource = HikariCPDataSource.createNewDataSource(dbFilename);
        DbSchema.setup(dataSource);

        WithdrawalService withdrawalService = new WithdrawalServiceStub();
        TransferService transferService = new TransferService(
                dataSource,
                withdrawalService
        );

        // setup accounts for testing
        Connection connection = dataSource.getConnection();
        BalanceDAO balanceDAO = new BalanceDAO(connection);
        balanceDAO.setupAccount(1, 1, BigDecimal.valueOf(1000000));
        balanceDAO.setupAccount(2, 2, BigDecimal.valueOf(0));
        balanceDAO.setupAccount(3, 3, BigDecimal.valueOf(0));
        balanceDAO.setupAccount(4, 4, BigDecimal.valueOf(0));
        balanceDAO.setupAccount(5, 5, BigDecimal.valueOf(0));
        connection.close();


        // setup routes
        On.post("/transfer").json((Req req, Resp resp) -> {
            TransferRequest transferRequest = new ObjectMapper().readValue(req.body(), TransferRequest.class);
            Response<TransactionLogModel> result = transferService.transfer(transferRequest);
            return buildResponse(result, resp);
        });
        On.post("/withdraw").json((Req req, Resp resp) -> {
            WithdrawalRequest withdrawalRequest = new ObjectMapper().readValue(req.body(), WithdrawalRequest.class);
            Response<RequestWithdrawalResponse> result = transferService.requestWithdrawal(withdrawalRequest);
            return buildResponse(result, resp);
        });
        On.get("/withdraw/{withdrawId}").json((Req req, Resp resp) -> {
            String withdrawId = req.param("withdrawId");
            Response<WithdrawalResponseDto> result = transferService.getWithdrawalRequest(withdrawId);
            return buildResponse(result, resp);
        });

        // test controllel
        On.get("/balances").json((Req req) -> {
            Connection conn = dataSource.getConnection();
            BalanceDAO allBalanceDAO = new BalanceDAO(conn);
            Map<String, BigDecimal> result = allBalanceDAO.getAllAccountBalances();
            return result;
        });


        // Create a ScheduledExecutorService with a single thread to sync withdrawal request status
        // Ideally, you should use a job scheduler like Quartz or Spring Scheduler
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        // Schedule a task to run every 10 seconds, with no initial delay
        executorService.scheduleAtFixedRate(() -> {
            try {
                // Call updateWithdrawalRequestStatus for each withdrawal request
                // You need to implement the getPendingWithdrawalRequests method
                List<String> listPendingWithdrawalRequest = transferService.getListPendingWithdrawalRequest();
                System.out.println("Pending withdrawal requests: " + listPendingWithdrawalRequest.size());
                for (String withdrawalId : listPendingWithdrawalRequest) {
                    try {
                        transferService.syncWithdrawalRequestStatus(withdrawalId);
                    } catch (Exception e) {
                        // Log the exception
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                // Log the exception
                e.printStackTrace();
            }
        }, 10, 10, TimeUnit.SECONDS);


        // Shutdown the executor service when the application exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            File databaseFile = new File(dbFilename);
            databaseFile.delete();

            executorService.shutdown();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Log the exception
                e.printStackTrace();
            }
        }));
    }

    private static Map<String, Object> buildResponse(Response result, Resp resp) {
        if (result.isSuccessful()) {
            return U.map("status", "success", "data", result.getData());
        } else {
            // should map error code to HTTP status code
            resp.code(400);
            return U.map(
                    "status", "failed",
                    "error", result.getErrorCode(),
                    "errorMessage", result.getErrorMessage()
            );
        }
    }
}
package com.felix.service

import com.felix.DbSchema
import com.felix.HikariCPDataSource
import com.felix.dao.BalanceDAO
import com.felix.dto.RequestWithdrawalResponse
import com.felix.dto.TransferRequest
import com.felix.dto.WithdrawalRequest
import com.felix.external.WithdrawalService
import com.felix.external.WithdrawalServiceStub
import com.felix.model.WithdrawalRequestStatusEnum
import com.zaxxer.hikari.HikariDataSource
import spock.lang.Specification

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

class TransferServiceTest extends Specification {
    BalanceDAO balanceDAO
    HikariDataSource dataSource
    WithdrawalService withdrawalService

    TransferService sut

    def setup() {
        println("setting up")
        dataSource = HikariCPDataSource.createNewDataSource("test-transfer.db")
        withdrawalService = Mock()
        DbSchema.setup(dataSource)
        sut = new TransferService(dataSource, withdrawalService)
    }

    def cleanup() {
        // delete the database file
        new File("test-transfer.db").delete()
    }

    def "transfer from one account to another"() {
        def connection = dataSource.getConnection()
        balanceDAO = new BalanceDAO(connection)
        def fromAccountId = 1
        given: "an sender account with balance of 100"
        balanceDAO.setupAccount(fromAccountId, 1, BigDecimal.valueOf(100))

        and: "an receiver account with balance of 10"
        def receiverAccountId = 2
        balanceDAO.setupAccount(receiverAccountId, 2, BigDecimal.valueOf(10))

        and: "a transfer request 50 from sender to receiver"
        def transferRequest = TransferRequest.builder()
                .fromAccountId(1)
                .toAccountId(2)
                .amount(BigDecimal.valueOf(50))
                .build()
        when:
        def transferResult = sut.transfer(transferRequest)

        then: 'no exception thrown and transfer is successful'
        noExceptionThrown()
        transferResult != null
        transferResult.isSuccessful()

        and: 'balance is updated'
        balanceDAO.getAccountBalance(fromAccountId) == BigDecimal.valueOf(50)
        balanceDAO.getAccountBalance(receiverAccountId) == BigDecimal.valueOf(60)
    }

    def "transfer with concurrent reqs from multiple threads"() {
        def connection = dataSource.getConnection()
        balanceDAO = new BalanceDAO(connection)
        given: "an sender account with balance of 1000"
        def fromAccountId = 3
        balanceDAO.setupAccount(fromAccountId, 3, BigDecimal.valueOf(1_000))

        and: "an receiver account with balance of 0"
        def receiverAccountId = 4
        balanceDAO.setupAccount(receiverAccountId, 4, BigDecimal.valueOf(0))

        and: "another receiver account with balance of 0"
        def receiverAccountId2 = 5
        balanceDAO.setupAccount(receiverAccountId2, 5, BigDecimal.valueOf(0))

        and: "a transfer request of 50 from sender to receiver 1"
        def transferRequest = TransferRequest.builder()
                .fromAccountId(3)
                .toAccountId(4)
                .amount(BigDecimal.valueOf(50))
                .build()
        and: "a transfer request of 50 from sender to receiver 2"
        def transferRequest2 = TransferRequest.builder()
                .fromAccountId(3)
                .toAccountId(5)
                .amount(BigDecimal.valueOf(50))
                .build()

        when: "10 threads concurrently transfer 50 from sender to receivers"
        def threads = (1..10).collect {
            new Thread({
                sut.transfer(transferRequest)
            })
        }
        def threads2 = (1..10).collect {
            new Thread({
                sut.transfer(transferRequest2)
            })
        }
        threads*.start()
        threads2*.start()
        threads*.join()
        threads2*.join()

        then: 'no exception thrown and transfer is successful'
        balanceDAO.getAccountBalance(fromAccountId) == BigDecimal.valueOf(0)
        balanceDAO.getAccountBalance(receiverAccountId) == BigDecimal.valueOf(500)
        balanceDAO.getAccountBalance(receiverAccountId2) == BigDecimal.valueOf(500)

    }


    def 'test withdraw success'() {
        def connection = dataSource.getConnection()
        balanceDAO = new BalanceDAO(connection)
        def fromAccountId = 1

        given: "an sender account with balance of 100"
        balanceDAO.setupAccount(fromAccountId, 1, BigDecimal.valueOf(100))

        and: "a withdrawal request"
        def req = WithdrawalRequest.builder()
                .fromAccountId(1)
                .amount(BigDecimal.valueOf(100))
                .address("abc")
                .build()

        and: "withdrawal service return success"
        withdrawalService.getRequestState(_) >> WithdrawalService.WithdrawalState.COMPLETED

        when:
        def result = sut.requestWithdrawal(req)
        sut.syncWithdrawalRequestStatus(result.data.getWithdrawalId())

        then:
        result != null

        and: "balance is updated"
        balanceDAO.getAccountBalance(fromAccountId) == BigDecimal.valueOf(0)
        and:
        sut.getWithdrawalRequest(result.data.getWithdrawalId()).getData().getStatus() == WithdrawalRequestStatusEnum.SUCCESS
    }

    def 'test withdraw failed'() {
        def connection = dataSource.getConnection()
        balanceDAO = new BalanceDAO(connection)
        def fromAccountId = 1

        given: "an sender account with balance of 100"
        balanceDAO.setupAccount(fromAccountId, 1, BigDecimal.valueOf(100))

        and: "a withdrawal request"
        def req = WithdrawalRequest.builder()
                .fromAccountId(1)
                .amount(BigDecimal.valueOf(100))
                .address("abc")
                .build()

        and: "withdrawal service return success"
        withdrawalService.getRequestState(_) >> WithdrawalService.WithdrawalState.FAILED

        when:
        def result = sut.requestWithdrawal(req)
        sut.syncWithdrawalRequestStatus(result.data.getWithdrawalId())

        then:
        result != null

        and: "balance is returned"
        balanceDAO.getAccountBalance(fromAccountId) == BigDecimal.valueOf(100)
        and:
        sut.getWithdrawalRequest(result.data.getWithdrawalId()).getData().getStatus() == WithdrawalRequestStatusEnum.FAILED
    }

    def 'test parallel withdraw'() {
        def connection = dataSource.getConnection()
        balanceDAO = new BalanceDAO(connection)
        def fromAccountId = 1

        given: "an sender account with balance of 100"
        balanceDAO.setupAccount(fromAccountId, 1, BigDecimal.valueOf(100))

        and: "a withdrawal request of 10 from sender account"
        def req = WithdrawalRequest.builder()
                .fromAccountId(1)
                .amount(BigDecimal.valueOf(10))
                .address("abc")
                .build()

        and: "withdrawal service return success"
        withdrawalService.getRequestState(_) >> WithdrawalService.WithdrawalState.COMPLETED

        when: "100 threads concurrently withdraw 10 from sender account"
        def failedCount = new AtomicInteger(0)
        def threads = (1..100).collect {
            new Thread({

                def withdrawal = sut.requestWithdrawal(req)
                if (!withdrawal.isSuccessful()) {
                    failedCount.incrementAndGet()
                }
            })
        }
        threads*.start()
        threads*.join()

        then:
        balanceDAO.getAccountBalance(fromAccountId) == BigDecimal.valueOf(0)
        and: "there should be 90 failed withdrawal requests due to insufficient balance"
        failedCount.get() == 90
    }

}

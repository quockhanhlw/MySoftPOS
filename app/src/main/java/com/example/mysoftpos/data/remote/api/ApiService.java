package com.example.mysoftpos.data.remote.api;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * Retrofit service interface for MySoftPOS Backend API.
 */
public interface ApiService {

        // ==================== Auth ====================

        @POST("/api/auth/register")
        Call<LoginResponse> register(@Body RegisterRequest request);

        @POST("/api/auth/login")
        Call<LoginResponse> login(@Body LoginRequest request);

        @POST("/api/auth/refresh")
        Call<LoginResponse> refreshToken(@Body Map<String, String> body);

        // ==================== Users (Admin) ====================

        @GET("/api/users")
        Call<List<UserDto>> getUsers(@Header("Authorization") String token);

        @POST("/api/users")
        Call<UserDto> createUser(@Header("Authorization") String token,
                        @Body CreateUserRequest request);

        @PUT("/api/users/{id}")
        Call<UserDto> updateUser(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body CreateUserRequest request);

        @DELETE("/api/users/{id}")
        Call<Map<String, String>> deleteUser(@Header("Authorization") String token,
                        @Path("id") long id);

        @PUT("/api/users/{id}/reset-password")
        Call<Map<String, String>> resetPassword(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body Map<String, String> body);

        // ==================== Merchants (Admin) ====================

        @GET("/api/merchants")
        Call<List<MerchantDto>> getMerchants(@Header("Authorization") String token);

        @POST("/api/merchants")
        Call<MerchantDto> createMerchant(@Header("Authorization") String token,
                        @Body Map<String, String> body);

        @PUT("/api/merchants/{id}")
        Call<MerchantDto> updateMerchant(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body Map<String, String> body);

        // ==================== Terminals (Admin) ====================

        @GET("/api/terminals")
        Call<List<TerminalDto>> getTerminals(@Header("Authorization") String token);

        @POST("/api/terminals")
        Call<TerminalDto> createTerminal(@Header("Authorization") String token,
                        @Body Map<String, String> body);

        @PUT("/api/terminals/{id}")
        Call<TerminalDto> updateTerminal(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body Map<String, String> body);

        // ==================== Transactions ====================

        @POST("/api/transactions/sync")
        Call<Map<String, Integer>> syncTransactions(@Header("Authorization") String token,
                        @Body TransactionSyncRequest request);

        @GET("/api/transactions")
        Call<List<TransactionSummaryDto>> getAllTransactions(@Header("Authorization") String token);

        @GET("/api/transactions/terminal/{code}")
        Call<List<TransactionSummaryDto>> getByTerminal(@Header("Authorization") String token,
                        @Path("code") String code);

        @GET("/api/transactions/user/{userId}")
        Call<List<TransactionSummaryDto>> getByUser(@Header("Authorization") String token,
                        @Path("userId") long userId);

        // ==================== Test Suites (Admin) ====================

        @GET("/api/test-suites")
        Call<List<TestSuiteDto>> getTestSuites(@Header("Authorization") String token);

        @GET("/api/test-suites/{id}")
        Call<TestSuiteDto> getTestSuiteWithCases(@Header("Authorization") String token,
                        @Path("id") long id);

        @POST("/api/test-suites")
        Call<TestSuiteDto> createTestSuite(@Header("Authorization") String token,
                        @Body TestSuiteDto req);

        @PUT("/api/test-suites/{id}")
        Call<TestSuiteDto> updateTestSuite(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body TestSuiteDto req);

        @DELETE("/api/test-suites/{id}")
        Call<Map<String, String>> deleteTestSuite(@Header("Authorization") String token,
                        @Path("id") long id);

        @GET("/api/test-suites/{suiteId}/cases")
        Call<List<TestCaseDto>> getTestCases(@Header("Authorization") String token,
                        @Path("suiteId") long suiteId);

        @POST("/api/test-suites/{suiteId}/cases")
        Call<TestCaseDto> createTestCase(@Header("Authorization") String token,
                        @Path("suiteId") long suiteId,
                        @Body TestCaseDto req);

        @PUT("/api/test-suites/cases/{caseId}")
        Call<TestCaseDto> updateTestCase(@Header("Authorization") String token,
                        @Path("caseId") long caseId,
                        @Body TestCaseDto req);

        @DELETE("/api/test-suites/cases/{caseId}")
        Call<Map<String, String>> deleteTestCase(@Header("Authorization") String token,
                        @Path("caseId") long caseId);

        @POST("/api/test-suites/sync")
        Call<Map<String, Integer>> syncTestSuites(@Header("Authorization") String token,
                        @Body List<TestSuiteDto> suites);

        // ==================== Inner DTOs ====================

        class LoginRequest {
                public String username;
                public String password;

                public LoginRequest(String username, String password) {
                        this.username = username;
                        this.password = password;
                }
        }

        class RegisterRequest {
                public String username;
                public String password;
                public String fullName;
                public String phone;
                public String email;

                public RegisterRequest(String username, String password, String fullName, String phone, String email) {
                        this.username = username;
                        this.password = password;
                        this.fullName = fullName;
                        this.phone = phone;
                        this.email = email;
                }
        }

        class CreateUserRequest {
                public String password;
                public String fullName;
                public String phone;
                public String email;
                public String terminalId;
                public String serverIp;
                public Integer serverPort;

                public CreateUserRequest(String password, String fullName, String phone, String email,
                                String terminalId, String serverIp, Integer serverPort) {
                        this.password = password;
                        this.fullName = fullName;
                        this.phone = phone;
                        this.email = email;
                        this.terminalId = terminalId;
                        this.serverIp = serverIp;
                        this.serverPort = serverPort;
                }
        }

        class LoginResponse {
                public String accessToken;
                public String refreshToken;
                public UserDto user;
        }

        class UserDto {
                public long id;
                public String role;
                public String fullName;
                public String phone;
                public String email;
                public String terminalId;
                public String serverIp;
                public Integer serverPort;
                public boolean active;
        }

        class MerchantDto {
                public long id;
                public String merchantCode;
                public String merchantName;
                public long adminId;
        }

        class TerminalDto {
                public long id;
                public String terminalCode;
                public MerchantDto merchant;
                public String serverIp;
                public Integer serverPort;
        }

        class TransactionSyncRequest {
                public java.util.List<TxnItem> transactions;

                public TransactionSyncRequest(java.util.List<TxnItem> txns) {
                        this.transactions = txns;
                }
        }

        class TxnItem {
                public String traceNumber;
                public String amount;
                public String status;
                public String maskedPan;
                public String cardScheme;
                public String terminalCode;
                public String deviceId;
                public long txnTimestamp;
        }

        class TransactionSummaryDto {
                public long id;
                public String traceNumber;
                public String amount;
                public String status;
                public String maskedPan;
                public String cardScheme;
                public String terminalCode;
                public String deviceId;
                public String txnTimestamp;
                public String syncedAt;
                public Long userId;
                public String username;
        }

        class TestSuiteDto {
                public Long id;
                public String name;
                public String description;
                public Long adminId;
                public String createdAt;
                public java.util.List<TestCaseDto> testCases;
        }

        class TestCaseDto {
                public Long id;
                public Long suiteId;
                public String name;
                public String transactionType;
                public String status;
                public String amount;
                public String de22;
                public String maskedPan;
                public String expiry;
                public String track2;
                public String scheme;
                public String fieldConfigJson;
                public String createdAt;
        }
}

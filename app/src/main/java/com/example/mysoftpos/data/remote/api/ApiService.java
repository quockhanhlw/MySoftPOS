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

        @PUT("/api/auth/change-password")
        Call<Map<String, String>> changePassword(@Header("Authorization") String token,
                        @Body ChangePasswordRequest request);

        // ==================== Merchants (Admin) ====================

        @GET("/api/merchants")
        Call<List<MerchantDto>> getMerchants(@Header("Authorization") String token);

        @GET("/api/merchants/{id}/accounts")
        Call<List<PosAccountDto>> getMerchantAccounts(@Header("Authorization") String token,
                        @Path("id") long merchantId);

        @GET("/api/merchants/{id}/branches")
        Call<List<BranchDto>> getMerchantBranches(@Header("Authorization") String token,
                        @Path("id") long merchantId);

        @GET("/api/merchants/{merchantId}/branches/{branchId}/accounts")
        Call<List<PosAccountDto>> getMerchantBranchAccounts(@Header("Authorization") String token,
                        @Path("merchantId") long merchantId,
                        @Path("branchId") long branchId);

        @POST("/api/merchants")
        Call<MerchantDto> createMerchant(@Header("Authorization") String token,
                        @Body Map<String, String> body);

        @PUT("/api/merchants/{id}")
        Call<MerchantDto> updateMerchant(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body Map<String, String> body);

        @DELETE("/api/merchants/{id}")
        Call<Map<String, String>> deleteMerchant(@Header("Authorization") String token,
                        @Path("id") long id);

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

        @DELETE("/api/terminals/{id}")
        Call<Map<String, String>> deleteTerminal(@Header("Authorization") String token,
                        @Path("id") long id);

        @DELETE("/api/merchants/{merchantId}/branches/{branchId}")
        Call<Map<String, String>> deleteBranch(@Header("Authorization") String token,
                        @Path("merchantId") long merchantId,
                        @Path("branchId") long branchId);

        // ==================== Transactions ====================

        @POST("/api/transactions/sync")
        Call<Map<String, Integer>> syncTransactions(@Header("Authorization") String token,
                        @Body TransactionSyncRequest request);

        @GET("/api/transactions")
        Call<List<TransactionRecordDto>> getAllTransactions(@Header("Authorization") String token,
                        @Query("merchantId") Long merchantId,
                        @Query("terminalId") Long terminalId);

        @POST("/api/transactions/admin/backfill")
        Call<Map<String, Integer>> backfillAdminTransactions(@Header("Authorization") String token,
                        @Query("merchantId") Long merchantId);

        @GET("/api/transactions/terminal/{code}")
        Call<List<TransactionRecordDto>> getByTerminal(@Header("Authorization") String token,
                        @Path("code") String code);

        @GET("/api/transactions/pos-accounts/{id}")
        Call<List<TransactionRecordDto>> getByPosAccount(@Header("Authorization") String token,
                        @Path("id") long id);

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

        @GET("/api/cards")
        Call<List<CardDto>> getCards(@Header("Authorization") String token);

        @POST("/api/cards/sync")
        Call<Map<String, Integer>> syncCards(@Header("Authorization") String token,
                        @Body List<CardDto> cards);

        // ==================== POS Accounts (Admin, domain alias of users) ====================

        @GET("/api/pos-accounts")
        Call<List<PosAccountDto>> getPosAccounts(@Header("Authorization") String token);

        @POST("/api/pos-accounts")
        Call<PosAccountDto> createPosAccount(@Header("Authorization") String token,
                        @Body CreatePosAccountRequest request);

        @PUT("/api/pos-accounts/{id}")
        Call<PosAccountDto> updatePosAccount(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body CreatePosAccountRequest request);

        @PUT("/api/pos-accounts/{id}/connection")
        Call<PosAccountDto> updatePosAccountConnection(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body PosAccountConnectionRequest request);

        @DELETE("/api/pos-accounts/{id}")
        Call<Map<String, String>> deletePosAccount(@Header("Authorization") String token,
                        @Path("id") long id);

        @PUT("/api/pos-accounts/{id}/reset-password")
        Call<Map<String, String>> resetPosAccountPassword(@Header("Authorization") String token,
                        @Path("id") long id,
                        @Body Map<String, String> body);

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
                public String password;
                public String fullName;
                public String phone;
                public String email;
                public String dob;
                public String gender;
                public String storeName;
                public String businessType;
                public String storeAddress;
                public Integer branchCount;
                public String branchAddresses;
                public Integer accountCount;
                public String bankName;

                public RegisterRequest(String password, String fullName, String phone, String email) {
                        this(password, fullName, phone, email,
                                null, null, null, null, null);
                }

                public RegisterRequest(String password, String fullName, String phone, String email,
                                String dob, String gender, String storeName, String businessType,
                                String storeAddress) {
                        this(password, fullName, phone, email,
                                dob, gender, storeName, businessType, storeAddress,
                                null, null, null);
                }

                public RegisterRequest(String password, String fullName, String phone, String email,
                                String dob, String gender, String storeName, String businessType,
                                String storeAddress, Integer branchCount, String branchAddresses,
                                Integer accountCount) {
                        this(password, fullName, phone, email,
                                dob, gender, storeName, businessType, storeAddress,
                                branchCount, branchAddresses, accountCount, null);
                }

                public RegisterRequest(String password, String fullName, String phone, String email,
                                String dob, String gender, String storeName, String businessType,
                                String storeAddress, Integer branchCount, String branchAddresses,
                                Integer accountCount, String bankName) {
                        this.password = password;
                        this.fullName = fullName;
                        this.phone = phone;
                        this.email = email;
                        this.dob = dob;
                        this.gender = gender;
                        this.storeName = storeName;
                        this.businessType = businessType;
                        this.storeAddress = storeAddress;
                        this.branchCount = branchCount;
                        this.branchAddresses = branchAddresses;
                        this.accountCount = accountCount;
                        this.bankName = bankName;
                }
        }

        class CreatePosAccountRequest {
                public String password;
                public String fullName;
                public String phone;
                public String email;
                public String dob;
                public String gender;
                public String storeName;
                public String businessType;
                public String storeAddress;
                public Long merchantId;
                public Long branchId;
                public String terminalId;
                public String serverIp;
                public Integer serverPort;

                public CreatePosAccountRequest(String password, String fullName, String phone, String email,
                                String terminalId, String serverIp, Integer serverPort) {
                        this(password, fullName, phone, email,
                                        null, null, null, null, null, null,
                                        null, terminalId, serverIp, serverPort);
                }

                public CreatePosAccountRequest(String password, String fullName, String phone, String email,
                                String dob, String gender, String storeName, String businessType,
                                String storeAddress, Long merchantId,
                                String terminalId, String serverIp, Integer serverPort) {
                        this(password, fullName, phone, email,
                                dob, gender, storeName, businessType, storeAddress, merchantId,
                                null, terminalId, serverIp, serverPort);
                }

                public CreatePosAccountRequest(String password, String fullName, String phone, String email,
                                String dob, String gender, String storeName, String businessType,
                                String storeAddress, Long merchantId, Long branchId,
                                String terminalId, String serverIp, Integer serverPort) {
                        this.password = password;
                        this.fullName = fullName;
                        this.phone = phone;
                        this.email = email;
                        this.dob = dob;
                        this.gender = gender;
                        this.storeName = storeName;
                        this.businessType = businessType;
                        this.storeAddress = storeAddress;
                        this.merchantId = merchantId;
                        this.branchId = branchId;
                        this.terminalId = terminalId;
                        this.serverIp = serverIp;
                        this.serverPort = serverPort;
                }
        }

        class ChangePasswordRequest {
                public String currentPassword;
                public String newPassword;
                public String confirmPassword;

                public ChangePasswordRequest(String currentPassword, String newPassword, String confirmPassword) {
                        this.currentPassword = currentPassword;
                        this.newPassword = newPassword;
                        this.confirmPassword = confirmPassword;
                }
        }

        class PosAccountConnectionRequest {
                public String terminalId;
                public String serverIp;
                public Integer serverPort;

                public PosAccountConnectionRequest(String terminalId, String serverIp, Integer serverPort) {
                        this.terminalId = terminalId;
                        this.serverIp = serverIp;
                        this.serverPort = serverPort;
                }
        }


        class LoginResponse {
                public String accessToken;
                public String refreshToken;
                public PosAccountDto posAccount;
                public PosAccountDto user;
        }

        class PosAccountDto {
                public long id;
                public Long merchantId;
                public Long branchId;
                public String branchCode;
                public String branchName;
                public String role;
                public String fullName;
                public String username;
                public String phone;
                public String merchantPhone;
                public String email;
                public String dob;
                public String gender;
                public String storeName;
                public String bankName;
                public String businessType;
                public String storeAddress;
                public String merchantCode;
                public Boolean phoneVerified;
                public String terminalId;
                public String serverIp;
                public Integer serverPort;
                public boolean active;
        }

        class MerchantDto {
                public long id;
                public String merchantCode;
                public String merchantName;
                public String fullName;
                public String phone;
                public String email;
                public String dob;
                public String gender;
                public String bankName;
                public Long adminId;
                public Long ownerUserId;
                public String businessType;
                public String storeAddress;
                public Integer branchCount;
                public String branchAddresses;
                public Integer accountCount;
        }

        class TerminalDto {
                public long id;
                public String terminalCode;
                public MerchantDto merchant;
                public Long branchId;
                public Long posAccountId;
                public String serverIp;
                public Integer serverPort;
        }

        class BranchDto {
                public long id;
                public Long merchantId;
                public String branchCode;
                public String branchName;
                public String branchAddress;
                public Integer accountCount;
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
                public Long terminalId;
                public Long cardId;
                public String terminalCode;
                public String deviceId;
                public String requestHex;
                public String responseHex;
                public String processingCode;
                public String currencyCode;
                public String rrn;
                public String ownerUsername;
                public long txnTimestamp;
        }

        class TransactionRecordDto {
                public long id;
                public String traceNumber;
                public String amount;
                public String status;
                public String maskedPan;
                public String cardScheme;
                public Long terminalId;
                public Long cardId;
                public String terminalCode;
                public String deviceId;
                public String txnTimestamp;
                public String syncedAt;
                public Long posAccountId;
                public Long userId;
                public String username;
                public String requestHex;
                public String responseHex;
                public String processingCode;
                public String currencyCode;
                public String rrn;
                public String ownerUsername;
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
                public String reqFilePath;
                public String resFilePath;
                public String track2;
                public String scheme;
                public String fieldConfigJson;
                public Boolean isDefault;
                public String createdAt;
        }

        class CardDto {
                public Long id;
                public String panMasked;
                public String bin;
                public String last4;
                public String scheme;
                public Long adminId;
                public Long posAccountId;
        }
}

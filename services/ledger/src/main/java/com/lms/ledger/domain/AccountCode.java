package com.lms.ledger.domain;

/**
 * The chart of accounts (docs/domain-rules.md "Chart of accounts"). A fixed domain vocabulary, not
 * product configuration, so it lives in the domain. The numeric {@link #code} is what the {@code
 * accounts} table is seeded with and what postings reference.
 */
public enum AccountCode {
    BANK_OPERATING("1001", "Bank (operating)", AccountType.ASSET),
    LOAN_PRINCIPAL_RECEIVABLE("1100", "Loan principal receivable", AccountType.ASSET),
    INTEREST_RECEIVABLE("1110", "Interest receivable", AccountType.ASSET),
    PENAL_RECEIVABLE("1130", "Penal receivable", AccountType.ASSET),
    GST_PAYABLE("2100", "GST payable", AccountType.LIABILITY),
    CUSTOMER_SUSPENSE("2200", "Customer suspense (unallocated receipts)", AccountType.LIABILITY),
    DISBURSEMENT_IN_TRANSIT("2300", "Disbursement in transit", AccountType.LIABILITY),
    INTEREST_INCOME("4001", "Interest income", AccountType.INCOME),
    PROCESSING_FEE_INCOME("4002", "Processing fee income", AccountType.INCOME),
    PENAL_INCOME("4003", "Penal income", AccountType.INCOME),
    BAD_DEBT_WRITTEN_OFF("5001", "Bad debt written off", AccountType.EXPENSE);

    private final String code;
    private final String accountName;
    private final AccountType type;

    AccountCode(String code, String accountName, AccountType type) {
        this.code = code;
        this.accountName = accountName;
        this.type = type;
    }

    public String code() {
        return code;
    }

    public String accountName() {
        return accountName;
    }

    public AccountType type() {
        return type;
    }

    public PostingSide normalSide() {
        return type.normalSide();
    }

    public static AccountCode ofCode(String code) {
        for (AccountCode account : values()) {
            if (account.code.equals(code)) {
                return account;
            }
        }
        throw new IllegalArgumentException("unknown account code: " + code);
    }
}

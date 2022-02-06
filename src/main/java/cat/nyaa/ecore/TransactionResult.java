package cat.nyaa.ecore;

enum Status {
    SUCCESS,
    INSUFFICIENT_BALANCE,
    UPSTREAM_FAILURE,
    UNKNOWN_ERROR
}

public interface TransactionResult {
    Status status();

    boolean isSuccess();

    Receipt getReceipt();
}

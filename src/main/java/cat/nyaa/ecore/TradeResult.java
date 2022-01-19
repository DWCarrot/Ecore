package cat.nyaa.ecore;

public interface TradeResult {
    Status isSuccess();

    Receipt getReceipt();
}

enum Status{
    SUCCESS,
    INSUFFICIENT_BALANCE,
    UPSTREAM_FAILURE,
    UNKNOWN_ERROR
}

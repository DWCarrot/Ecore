package cat.nyaa.ecore;

public interface TransactionResult {
    TransactionStatus status();

    boolean isSuccess();

    Receipt getReceipt();
}

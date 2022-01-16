package cat.nyaa.ecore;

import java.util.UUID;

public interface EconomyCore {
    TradeResult playerTransfer(UUID fromVault, UUID toVault, double amount);

    TradeResult playerTrade(UUID fromVault, UUID toVault, double amount);

    boolean depositPlayer(UUID vault, double amount);

    boolean withdrawPlayer(UUID vault, double amount);

    boolean withdrawSystemVault(double amount);

    boolean depositSystemVault(double amount);

    double getBalance(UUID vault);
}

interface TradeResult {
    boolean isSuccess();

    Receipt getReceipt();
}

interface Receipt {
    UUID getPayer();

    UUID getReceiver();

    double getAmountTransacted();

    double getFee();

    double getAmount();

    double getFeeRate();

    double getPayerRemain();

    double getReceiverRemain();

    long getTradeId();
}
package cat.nyaa.ecore;

import java.util.UUID;

public interface ECoreEconomy {
    TradeResult playerTransaction(UUID fromVault, UUID toVault, double amount);
    TradeResult playerTrade(UUID fromVault,UUID toVault, double amount);
    boolean withdrawSystemVault();
    boolean depositSystemVault();
}

interface TradeResult{
    boolean isSuccess();
    Receipt getReceipt();
}

interface Receipt{
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
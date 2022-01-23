package cat.nyaa.ecore;

import java.util.List;
import java.util.UUID;

public interface EconomyCore {
    TradeResult playerTransfer(UUID fromVault, UUID toVault, double amount);

    TradeResult playerTransferToMultiple(UUID fromVault, List<UUID> toVault, double amount);

    TradeResult playerTrade(UUID fromVault, UUID toVault, double amount);

    boolean depositPlayer(UUID vault, double amount);

    boolean withdrawPlayer(UUID vault, double amount);

    boolean setPlayerBalance(UUID vault,double amount);

    boolean withdrawSystemVault(double amount);

    boolean depositSystemVault(double amount);

    double getPlayerBalance(UUID vault);

    boolean setSystemBalance(double amount);

    double getSystemBalance();

    double getTransferFeeRate();

    double getTradeFeeRate();
}
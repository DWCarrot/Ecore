package cat.nyaa.ecore;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ECore implements ECoreEconomy {
    private final Economy economy;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private Config config;
    private double internalVaultBalance;
    private OfflinePlayer vaultPlayer = null;
    private boolean isInternalVaultEnabled;

    public ECore(Config config, Economy economy, JavaPlugin pluginInstance) throws IOException {
        this.economy = economy;
        load(config, pluginInstance);
    }

    private void load(Config config, JavaPlugin pluginInstance) throws IOException {
        this.config = config;
        if (config.vault.type.equals("internal")) {
            isInternalVaultEnabled = true;
            var data = new File("ecore_data");
            if (data.createNewFile() || data.length() == 0) {
                pluginInstance.getLogger().info("Created new ecore data file.");
                internalVaultBalance = 0;
            } else {
                internalVaultBalance = Double.parseDouble(new BufferedReader(new FileReader(data)).readLine());
                pluginInstance.getLogger().info("Loaded ecore data file.");
            }
        } else if (config.vault.type.equals("external")) {
            isInternalVaultEnabled = false;
            vaultPlayer = Bukkit.getOfflinePlayer(UUID.fromString(config.vault.externalPlayerVaultUUID));
            if (!economy.hasAccount(vaultPlayer)) {
                economy.createPlayerAccount(vaultPlayer);
                pluginInstance.getLogger().info("Created new external vault account.");
            }
        } else {
            throw new RuntimeException("Unknown vault type: " + config.vault.type);
        }
    }

    private TradeResult transactionWithFeeRate(UUID fromVault, UUID toVault, double amount, double feeRate) {
        var payer = Bukkit.getOfflinePlayer(fromVault);
        var receiver = Bukkit.getOfflinePlayer(toVault);
        createPlayerBankAccountIfNotExist(payer);
        createPlayerBankAccountIfNotExist(receiver);

        var withdrawResult = economy.withdrawPlayer(payer, amount);
        if (withdrawResult.type != EconomyResponse.ResponseType.SUCCESS)
            return new TradeResultImpl(false, null);
        var transactionFee = amount * feeRate;
        var amountFinal = amount - transactionFee;
        economy.depositPlayer(receiver, amountFinal);
        depositSystemVault(transactionFee);

        return new TradeResultImpl(true, new ReceiptImpl(fromVault, toVault, amountFinal, transactionFee, amount, config.serviceFee.transferFee, economy.getBalance(payer), economy.getBalance(receiver), random.nextLong()));
    }

    @Override
    public TradeResult playerTransfer(UUID fromVault, UUID toVault, double amount) {
        var receipt = transactionWithFeeRate(fromVault, toVault, amount, config.serviceFee.transferFee);
        if (config.misc.logTransactionToConsole)
            Bukkit.getLogger().info("(Transfer) " + receipt);
        return receipt;
    }


    @Override
    public TradeResult playerTrade(UUID fromVault, UUID toVault, double amount) {
        var receipt = transactionWithFeeRate(fromVault, toVault, amount, config.serviceFee.tradeFee);
        if (config.misc.logTransactionToConsole)
            Bukkit.getLogger().info("(Trade) " + receipt);
        return receipt;
    }

    public boolean withdrawSystemVault(double amount) {
        if (isInternalVaultEnabled) {
            if (internalVaultBalance < amount) {
                return false;
            } else {
                internalVaultBalance -= amount;
                return true;
            }
        } else {
            return economy.withdrawPlayer(vaultPlayer, amount).type == EconomyResponse.ResponseType.SUCCESS;
        }
    }

    public boolean depositSystemVault(double amount) {
        if (isInternalVaultEnabled) {
            internalVaultBalance += amount;
            return true;
        } else {
            return economy.depositPlayer(vaultPlayer, amount).type == EconomyResponse.ResponseType.SUCCESS;
        }
    }

    private void createPlayerBankAccountIfNotExist(OfflinePlayer player) {
        if (!economy.hasAccount(player)) {
            economy.createPlayerAccount(player);
        }
    }


}

class TradeResultImpl implements TradeResult {
    private final boolean flag;
    private final Receipt receipt;

    TradeResultImpl(boolean isSuccess, Receipt receipt) {
        this.flag = isSuccess;
        this.receipt = receipt;
    }

    @Override
    public boolean isSuccess() {
        return flag;
    }

    @Override
    public Receipt getReceipt() {
        return receipt;
    }
}

class ReceiptImpl implements Receipt {
    // RECEIPT
    private final UUID payer;
    private final UUID receiver;
    // ---
    private final double amountTransacted;
    private final double fee;
    // ---
    private final double amount;
    private final double feeRate;
    // ---
    private final double payerRemain;
    private final double receiverRemain;
    // ---
    private final long tradeId;

    public ReceiptImpl(UUID payer, UUID receiver, double amountTransacted, double fee, double amount, double feeRate, double payerRemain, double receiverRemain, long tradeId) {
        this.payer = payer;
        this.receiver = receiver;
        this.amountTransacted = amountTransacted;
        this.fee = fee;
        this.amount = amount;
        this.feeRate = feeRate;
        this.payerRemain = payerRemain;
        this.receiverRemain = receiverRemain;
        this.tradeId = tradeId;
    }

    public UUID getPayer() {
        return payer;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public double getAmountTransacted() {
        return amountTransacted;
    }

    public double getFee() {
        return fee;
    }

    public double getAmount() {
        return amount;
    }

    public double getFeeRate() {
        return feeRate;
    }

    public double getPayerRemain() {
        return payerRemain;
    }

    public double getReceiverRemain() {
        return receiverRemain;
    }

    public long getTradeId() {
        return tradeId;
    }

    @Override
    public String toString() {
        return "Receipt{" +
                "payer=" + payer +
                ", receiver=" + receiver +
                ", amountTransacted=" + amountTransacted +
                ", fee=" + fee +
                ", amount=" + amount +
                ", payerRemain=" + payerRemain +
                ", receiverRemain=" + receiverRemain +
                ", tradeId=" + Long.toHexString(tradeId) +
                '}';
    }
}

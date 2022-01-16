package cat.nyaa.ecore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EconomyCoreProvider implements EconomyCore {
    private final Economy economy;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final File economyCoreInternalDataFile;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private Config config;
    private double internalVaultBalance;
    private OfflinePlayer vaultPlayer = null;
    private final JavaPlugin pluginInstance;
    private boolean isInternalVaultEnabled;

    public EconomyCoreProvider(Config config, Economy economy, JavaPlugin pluginInstance) throws IOException {
        this.economy = economy;
        this.pluginInstance = pluginInstance;
        economyCoreInternalDataFile = new File(pluginInstance.getDataFolder(), "ecore_internal_data.json");
        load(config);
    }

    private void load(Config config) throws IOException {
        this.config = config;
        if (config.vault.type.equals("internal")) {
            isInternalVaultEnabled = true;
            if (economyCoreInternalDataFile.createNewFile() || economyCoreInternalDataFile.length() == 0) {
                pluginInstance.getLogger().info("Created new ecore data file.");
                internalVaultBalance = 0;
            } else {
                var ecoreData = gson.fromJson(new FileReader(economyCoreInternalDataFile), EcoreDataInternal.class);
                if (ecoreData == null) {
                    ecoreData = new EcoreDataInternal(0);
                    saveInternalVaultBalance();
                }
                internalVaultBalance = ecoreData.internalVaultBalance();
                pluginInstance.getLogger().info("Loaded ecore data file.");
            }
            pluginInstance.getServer().getScheduler().runTaskTimer(pluginInstance, () -> {
                try {
                    saveInternalVaultBalance();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, 0, 20 * config.vault.internalVaultAutoSaveIntervalInSeconds);
            pluginInstance.getLogger().info("Using " + config.vault.type + " vault as system account.");
        } else if (config.vault.type.equals("external")) {
            isInternalVaultEnabled = false;
            vaultPlayer = Bukkit.getOfflinePlayer(UUID.fromString(config.vault.externalPlayerVaultUUID));
            if (!economy.hasAccount(vaultPlayer)) {
                economy.createPlayerAccount(vaultPlayer);
                pluginInstance.getLogger().info("Created new external vault account.");
            }
            pluginInstance.getLogger().info("Using " + config.vault.type + " vault as system account. Vault account UUID: " + vaultPlayer.getUniqueId());
        } else {
            throw new RuntimeException("Unknown vault type: " + config.vault.type);
        }
    }

    private void saveInternalVaultBalance() throws IOException {
        var ecoreData = new EcoreDataInternal(internalVaultBalance);
        var writer = new FileWriter(economyCoreInternalDataFile);
        gson.toJson(ecoreData, writer);
        writer.close();
    }

    public void onDisable() {
        try {
            saveInternalVaultBalance();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TradeResult transactionWithFeeRate(UUID fromVault, UUID toVault, double amount, double feeRate) {
        var transactionFee = amount * feeRate;
        var amountFinal = amount - transactionFee;
        if (!depositPlayer(toVault, amountFinal)) {
            return new TradeResultInternal(false, null);
        }
        depositSystemVault(transactionFee);
        return new TradeResultInternal(true, new ReceiptInternal(fromVault, toVault, amountFinal, transactionFee, amount, config.serviceFee.transferFee, getBalance(fromVault), getBalance(toVault), random.nextLong()));
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

    @Override
    public boolean depositPlayer(UUID vault, double amount) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        var withdrawResult = economy.depositPlayer(player, amount);
        return withdrawResult.type != EconomyResponse.ResponseType.SUCCESS;
    }

    @Override
    public boolean withdrawPlayer(UUID vault, double amount) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        var withdrawResult = economy.withdrawPlayer(player, amount);
        return withdrawResult.type != EconomyResponse.ResponseType.SUCCESS;
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

    @Override
    public double getBalance(UUID vault) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        return economy.getBalance(player);
    }

    private void createPlayerBankAccountIfNotExist(OfflinePlayer player) {
        if (!economy.hasAccount(player)) {
            economy.createPlayerAccount(player);
        }
    }


}

class TradeResultInternal implements TradeResult {
    private final boolean flag;
    private final Receipt receipt;

    TradeResultInternal(boolean isSuccess, Receipt receipt) {
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

record ReceiptInternal(UUID payer, UUID receiver, double amountTransacted, double fee,
                       double amount, double feeRate, double payerRemain, double receiverRemain,
                       long tradeId) implements Receipt {

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

record EcoreDataInternal(double internalVaultBalance) {
}

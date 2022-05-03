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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EconomyCoreProvider implements EconomyCore {
    private final Economy economy;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final File economyCoreInternalDataFile;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final JavaPlugin pluginInstance;
    private Config config;
    private double internalVaultBalance;
    private OfflinePlayer vaultPlayer = null;
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
                internalVaultBalance = ecoreData.getInternalVaultBalance();
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

    private TransactionResult transactionWithFeeRate(UUID fromVault, List<UUID> toVaults, double amount, double feeRate, double feeMin, double feeMax, ServiceFeePreference serviceFeePreference) {
        var transactedPlayers = new ArrayList<UUID>();
        var transactionFee = amount * feeRate;
        if (transactionFee < feeMin)
            transactionFee = feeMin;
        else if (transactionFee > feeMax)
            transactionFee = feeMax;

        var amountNeedPerTransaction = switch (serviceFeePreference){
            case INTERNAL -> amount;
            case ADDITIONAL -> amount + transactionFee;
        };

        var amountArrivePerTransaction = switch (serviceFeePreference) {
            case INTERNAL -> amount - transactionFee;
            case ADDITIONAL -> amount;
        };

        if (getPlayerBalance(fromVault) < amountNeedPerTransaction) {
            return new TransactionResultInternal(TransactionStatus.INSUFFICIENT_BALANCE, null);
        }

        for (UUID toVault : toVaults) {
            //step 0: withdraw from vault
            if (!withdrawPlayer(fromVault, amountNeedPerTransaction)) {
                break;
            }

            //step 1: deposit service fee to system vault
            var depositServiceFeeSuccess = depositSystemVault(transactionFee);
            if (!depositServiceFeeSuccess) {
                var rollbackSuccess = depositPlayer(fromVault, amountNeedPerTransaction);
                if (!rollbackSuccess) {
                    throw new RuntimeException("Failed to rollback transaction: deposit " + amountNeedPerTransaction + " to " + fromVault + " failed.");
                }
                break;
            }
            //step2: deposit to target Vault
            var depositPlayerSuccess = depositPlayer(toVault, amountArrivePerTransaction);
            if (!depositPlayerSuccess) {
                var rollbackStep1Success = withdrawSystemVault(transactionFee);
                if (!rollbackStep1Success) {
                    throw new RuntimeException("Failed to rollback transaction: withdraw " + transactionFee + " from system vault and " + "deposit " + amount + " to " + fromVault + " failed.");
                }
                var rollbackStep0Success = depositPlayer(fromVault, amountNeedPerTransaction);
                if (!rollbackStep0Success) {
                    throw new RuntimeException("Failed to rollback transaction: deposit " + amountNeedPerTransaction + " to " + fromVault + " failed.");
                }
                break;
            }
            transactedPlayers.add(toVault);
        }

        if (transactedPlayers.isEmpty()) {
            return new TransactionResultInternal(TransactionStatus.UNKNOWN_ERROR, null);
        } else {
            return new TransactionResultInternal(TransactionStatus.SUCCESS, new ReceiptInternal(fromVault, transactedPlayers, amount, amountArrivePerTransaction, transactionFee, feeRate, getPlayerBalance(fromVault), serviceFeePreference, random.nextLong()));
        }
    }

    @Override
    public TransactionResult playerTransfer(UUID fromVault, UUID toVault, double amount) {
        return playerTransferToMultiple(fromVault, List.of(toVault), amount);
    }

    @Override
    public TransactionResult playerTransferToMultiple(UUID fromVault, List<UUID> toVault, double amount) {
        return playerTransferToMultiple(fromVault, toVault, amount, ServiceFeePreference.INTERNAL);
    }

    @Override
    public TransactionResult playerTransferToMultiple(UUID fromVault, List<UUID> toVault, double amount, ServiceFeePreference serviceFeePreference) {
        var receipt = transactionWithFeeRate(fromVault, toVault, amount, config.serviceFee.transferFee, 0, Double.MAX_VALUE, serviceFeePreference);
        if (config.misc.logTransactionToConsole)
            pluginInstance.getLogger().info("(Transfer) " + receipt);
        return receipt;
    }


    @Override
    public TransactionResult playerTrade(UUID consumer, UUID merchant, double price) {
        return playerTrade(consumer, merchant, price, config.serviceFee.tradeFee);
    }

    @Override
    public TransactionResult playerTrade(UUID consumer, UUID merchant, double price, ServiceFeePreference serviceFeePreference) {
        return playerTrade(consumer, merchant, price, config.serviceFee.tradeFee, serviceFeePreference);
    }

    @Override
    public TransactionResult playerTrade(UUID consumer, UUID merchant, double price, double feeRate) {
        return playerTrade(consumer, merchant, price, feeRate, 0, Double.MAX_VALUE);
    }

    @Override
    public TransactionResult playerTrade(UUID consumer, UUID merchant, double price, double feeRate, ServiceFeePreference serviceFeePreference) {
        return playerTrade(consumer, merchant, price, feeRate, 0, Double.MAX_VALUE, serviceFeePreference);
    }

    @Override
    public TransactionResult playerTrade(UUID consumer, UUID merchant, double price, double feeRate, double feeMin, double feeMax) {
        var receipt = transactionWithFeeRate(consumer, List.of(merchant), price, feeRate, feeMin, feeMax, ServiceFeePreference.INTERNAL);
        if (config.misc.logTransactionToConsole)
            pluginInstance.getLogger().info("(Trade) " + receipt);
        return receipt;
    }

    @Override
    public TransactionResult playerTrade(UUID consumer, UUID merchant, double price, double feeRate, double feeMin, double feeMax, ServiceFeePreference serviceFeePreference) {
        var receipt = transactionWithFeeRate(consumer, List.of(merchant), price, feeRate, feeMin, feeMax, serviceFeePreference);
        if (config.misc.logTransactionToConsole)
            pluginInstance.getLogger().info("(Trade) " + receipt);
        return receipt;
    }

    @Override
    public boolean depositPlayer(UUID vault, double amount) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        var withdrawResult = economy.depositPlayer(player, amount);
        return withdrawResult.type == EconomyResponse.ResponseType.SUCCESS;
    }

    @Override
    public boolean withdrawPlayer(UUID vault, double amount) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        var withdrawResult = economy.withdrawPlayer(player, amount);
        return withdrawResult.type == EconomyResponse.ResponseType.SUCCESS;
    }

    @Override
    public boolean setPlayerBalance(UUID vault, double amount) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        var distance = amount - getPlayerBalance(vault);
        if (distance > 0) {
            return depositPlayer(vault, distance);
        } else {
            return withdrawPlayer(vault, -distance);
        }
    }

    @Override
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

    @Override
    public boolean depositSystemVault(double amount) {
        if (isInternalVaultEnabled) {
            internalVaultBalance += amount;
            return true;
        } else {
            return economy.depositPlayer(vaultPlayer, amount).type == EconomyResponse.ResponseType.SUCCESS;
        }
    }

    @Override
    public double getPlayerBalance(UUID vault) {
        var player = Bukkit.getOfflinePlayer(vault);
        createPlayerBankAccountIfNotExist(player);
        return economy.getBalance(player);
    }

    @Override
    public boolean setSystemBalance(double amount) {
        if (isInternalVaultEnabled) {
            internalVaultBalance = amount;
            return true;
        } else {
            return setPlayerBalance(vaultPlayer.getUniqueId(), amount);
        }
    }

    @Override
    public double getSystemBalance() {
        if (isInternalVaultEnabled) {
            return internalVaultBalance;
        } else {
            return economy.getBalance(vaultPlayer);
        }
    }

    @Override
    public double getTransferFeeRate() {
        return config.serviceFee.transferFee;
    }

    @Override
    public double getTradeFeeRate() {
        return config.serviceFee.tradeFee;
    }

    @Override
    public String currencyNameSingular() {
        return economy.currencyNameSingular();
    }

    @Override
    public String currencyNamePlural() {
        return economy.currencyNamePlural();
    }

    @Override
    public String systemVaultName() {
        return config.vault.friendlyName;
    }

    private void createPlayerBankAccountIfNotExist(OfflinePlayer player) {
        try {
            //if possible
            if (!economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }
        } catch (Exception ignored) {
        }
    }


}

record TransactionResultInternal(TransactionStatus transactionStatus, Receipt receipt) implements TransactionResult {
    @Override
    public TransactionStatus status() {
        return transactionStatus;
    }

    @Override
    public boolean isSuccess() {
        return transactionStatus == TransactionStatus.SUCCESS;
    }

    @Override
    public Receipt getReceipt() {
        return receipt;
    }
}

record ReceiptInternal(UUID payer, List<UUID> receivers, double amount, double arrivalAmount,
                       double fee,
                       double feeRate, double payerRemain, ServiceFeePreference serviceFeePreference,
                       long receiptId) implements Receipt {

    @Override
    public UUID getPayer() {
        return payer;
    }

    @Override
    public List<UUID> getReceiver() {
        return receivers;
    }

    @Override
    public double getAmountArrivePerTransaction() {
        return arrivalAmount;
    }

    @Override
    public double getAmountArriveTotally() {
        return getAmountArrivePerTransaction() * receivers.size();
    }

    @Override
    public double getFeePerTransaction() {
        return fee;
    }

    @Override
    public double getFeeTotally() {
        return fee * receivers.size();
    }

    @Override
    public double getAmountPerTransaction() {
        return amount;
    }

    @Override
    public double getAmountTotally() {
        return amount * receivers.size();
    }

    @Override
    public double getFeeRate() {
        return feeRate;
    }

    @Override
    public double getFeeRatePercent() {
        return getFeeRate() * 100;
    }

    @Override
    public double getPayerRemain() {
        return payerRemain;
    }

    @Override
    public ServiceFeePreference getTaxPreference() {
        return serviceFeePreference;
    }

    @Override
    public long getId() {
        return receiptId;
    }

    @Override
    public String toString() {
        return "ReceiptInternal{" +
                "payer=" + payer +
                ", receivers=" + receivers.toString() +
                ", amount=" + amount + "(" + getAmountTotally() + " totally)" +
                ", arrivalAmount=" + arrivalAmount +
                ", fee=" + fee + "(" + getFeeTotally() + " totally)" +
                ", feeRate=" + feeRate +
                ", payerRemain=" + payerRemain +
                ", serviceFeePreference=" + serviceFeePreference +
                ", receiptId=" + Long.toHexString(receiptId) +
                '}';
    }
}

class EcoreDataInternal {
    private double internalVaultBalance;

    public EcoreDataInternal(double internalVaultBalance) {
        this.internalVaultBalance = internalVaultBalance;
    }

    public double getInternalVaultBalance() {
        return internalVaultBalance;
    }

    public void setInternalVaultBalance(double internalVaultBalance) {
        this.internalVaultBalance = internalVaultBalance;
    }
}

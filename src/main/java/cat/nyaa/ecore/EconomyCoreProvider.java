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

    private TradeResult transactionWithFeeRate(UUID fromVault, List<UUID> toVaults, double amount, double feeRate) {
        var transacted = new ArrayList<UUID>();
        var total = amount * toVaults.size();
        if (getPlayerBalance(fromVault) < total) {
            return new TradeResultInternal(Status.INSUFFICIENT_BALANCE, null);
        }

        var transactionFee = amount * feeRate;
        var amountArrive = amount - transactionFee;
        for (UUID toVault : toVaults) {
            if (!withdrawPlayer(fromVault, amount)) {
                break;
            }
            depositSystemVault(transactionFee);
            depositPlayer(toVault, amountArrive);
            transacted.add(toVault);
        }
        return new TradeResultInternal(Status.SUCCESS, new ReceiptInternal(fromVault, transacted, amount, transactionFee, config.serviceFee.transferFee, getPlayerBalance(fromVault), random.nextLong()));
    }

    @Override
    public TradeResult playerTransfer(UUID fromVault, UUID toVault, double amount) {
        return playerTransferToMultiple(fromVault, List.of(toVault), amount);
    }

    @Override
    public TradeResult playerTransferToMultiple(UUID fromVault, List<UUID> toVault, double amount) {
        var receipt = transactionWithFeeRate(fromVault, toVault, amount, config.serviceFee.transferFee);
        if (config.misc.logTransactionToConsole)
            pluginInstance.getLogger().info("(Transfer) " + receipt);
        return receipt;
    }


    @Override
    public TradeResult playerTrade(UUID fromVault, UUID toVault, double amount) {
        var receipt = transactionWithFeeRate(fromVault, List.of(toVault), amount, config.serviceFee.tradeFee);
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
        }else{
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
        if(isInternalVaultEnabled){
            internalVaultBalance = amount;
            return true;
        }else{
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

    private void createPlayerBankAccountIfNotExist(OfflinePlayer player) {
        if (!economy.hasAccount(player)) {
            economy.createPlayerAccount(player);
        }
    }


}

record TradeResultInternal(Status status, Receipt receipt) implements TradeResult {
    @Override
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @Override
    public Receipt getReceipt() {
        return receipt;
    }
}

record ReceiptInternal(UUID payer, List<UUID> receivers, double amount,
                       double fee,
                       double feeRate, double payerRemain,
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
        return amount - fee;
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
    public long getId() {
        return receiptId;
    }

    @Override
    public String toString() {
        return "Receipt{" +
                "payer=" + payer +
                ", receivers=" + receivers.toString() +
                ", amount =" + amount + "(" + getAmountTotally() + " totally)" +
                ", fee=" + fee + "(" + getFeeTotally() + " totally)" +
                ", arrive=" + getAmountArrivePerTransaction() + "(" + getAmountArriveTotally() + " totally)" +
                ", payerRemain=" + payerRemain +
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

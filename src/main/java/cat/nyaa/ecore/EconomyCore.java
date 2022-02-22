package cat.nyaa.ecore;

import java.util.List;
import java.util.UUID;

/**
 *  Interface for economy core.
 *  <p>Implemented by {@link EconomyCoreProvider}.</p>
 *
 *  <p>You could get the instance of {@link EconomyCoreProvider} as EconomyCore in bukkit/spigot server as follows:</p>
 *  <pre>
 *      EconomyCoreProvider economyCoreProvider = (EconomyCoreProvider) Bukkit.getServicesManager().getRegistration(EconomyCoreProvider.class).getProvider();
 *      EconomyCore economyCore = economyCoreProvider.getProvider();
 *  </pre>
 *
 *  <p>It is suggested that check the ECore plugin is pretty installed before load your plugins.</p>
 *  <pre>
 *      class ... extends JavaPlugin {
 *          public void onEnable() {
 *              ...
 *              // check if ECore is installed at first
 *              if(this.getServer().getPluginManager().getPlugin("ECore") == null) {
 *                  this.getLogger().severe("ECore is not installed!");
 *                  this.getServer().getPluginManager().disablePlugin(this);
 *                  return;
 *              }
 *              ...
 *              //then get the ECore provider, or {@link ClassNotFoundException} will be thrown at the line below (while access <code>EconomyCore.class</code> which cannot be found)
 *              EconomyCoreProvider economyCoreProvider = Bukkit.getServicesManager().getRegistration(EconomyCore.class).getProvider();
 *              EconomyCore economyCore = economyCoreProvider.getProvider();
 *          }
 *      }
 * </pre>
 */
public interface EconomyCore {
    /**
     * transfer specific amount of balance to another player, automatically charging service fee.
     * @param fromVault the player to transfer from
     * @param toVault the player to transfer to
     * @param amount the amount to transfer
     * @return the result of the transfer
     */
    TransactionResult playerTransfer(UUID fromVault, UUID toVault, double amount);

    /**
     * transfer specific amount of balance to every player in the list, automatically charging service fee.
     * @param fromVault the player to transfer from
     * @param toVault players to transfer to
     * @param amount the amount to transfer per player
     * @return the result of the transfer
     */
    TransactionResult playerTransferToMultiple(UUID fromVault, List<UUID> toVault, double amount);

    /**
     * trade between two players, automatically charging service fee.
     * @param consumer the player who act as consumer
     * @param merchant the player who act as merchant
     * @param price price of the goods
     * @return the result of the trade
     *
     */
    TransactionResult playerTrade(UUID consumer, UUID merchant, double price);

    /**
     * trade between two players, automatically charging service fee.
     * @param consumer the player who act as consumer
     * @param merchant the player who act as merchant
     * @param price price of the goods
     * @param feeRate custom feeRate in <code>double</code>. For example, <code>0.05</code> for <code>5%</code>.
     * @return the result of the trade
     *
     */
    TransactionResult playerTrade(UUID consumer, UUID merchant, double price, double feeRate);

    /**
     * trade between two players, automatically charging service fee.
     * @param consumer the player who act as consumer
     * @param merchant the player who act as merchant
     * @param price price of the goods
     * @param feeRate custom feeRate in <code>double</code>. For example, <code>0.05</code> for <code>5%</code>.
     * @param feeMin minimum service fee
     * @param feeMax maximum service fee
     * @return the result of the trade
     *
     */
    TransactionResult playerTrade(UUID consumer, UUID merchant, double price, double feeRate,double feeMin,double feeMax);


    /**
     * add balance to an account.
     * @param vault the account to act on
     * @param amount the amount to deduct
     * @return true for ok and false for failed
     */
    boolean depositPlayer(UUID vault, double amount);

    /**
     * deduct the balance from an account.
     * @param vault the account to act on
     * @param amount the amount to add
     * @return true for ok and false for failed
     */
    boolean withdrawPlayer(UUID vault, double amount);

    /**
     * set the balance of an account.
     * @param vault the account to act on
     * @param amount the amount to set
     * @return true for ok and false for failed
     */
    boolean setPlayerBalance(UUID vault,double amount);

    /**
     * deduct balance from system account.
     * @param amount the amount to deduct
     * @return true for ok and false for failed
     */
    boolean withdrawSystemVault(double amount);

    /**
     * add balance to system account.
     * @param amount the amount to add
     * @return true for ok and false for failed
     */
    boolean depositSystemVault(double amount);

    /**
     * get the balance of an account.
     * @param vault the account to get
     * @return the balance of the account
     */
    double getPlayerBalance(UUID vault);

    /**
     * set the balance of system account.
     * @param amount the amount to set
     * @return true for ok and false for failed
     */
    boolean setSystemBalance(double amount);

    /**
     * get the balance of system account.
     * @return the balance of system account
     */
    double getSystemBalance();

    /**
     * get the transfer fee rate setting in <code>double</code>. For example, <code>0.02</code> for <code>2%</code>.
     * @return the transfer fee rate
     */
    double getTransferFeeRate();

    /**
     * get the trade fee rate setting in <code>double</code>. For example, <code>0.05</code> for <code>5%</code>.
     * @return the trade fee rate
     */
    double getTradeFeeRate();

    /**
     * returns the name of the currency in plural form.
     */
    String currencyNameSingular();

    /**
     * returns the name of the currency in singular form.
     */
    String currencyNamePlural();

    /**
     * returns System vault name.
     */
    String systemVaultName();
}
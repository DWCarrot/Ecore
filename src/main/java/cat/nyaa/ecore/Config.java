package cat.nyaa.ecore;

public class Config {
    public SystemVault vault = new SystemVault();
    public ServiceFee serviceFee = new ServiceFee();
}

class SystemVault{
    public String type = "internal";
    public String external_vault_id = "0";
}

class ServiceFee{
    public double transaction_fee = 0.02;
    public double trade_fee = 0.1;
}

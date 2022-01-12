package cat.nyaa.ecore;

public class Config {
    public SystemVault vault = new SystemVault();
    public ServiceFee serviceFee = new ServiceFee();
    public Misc misc = new Misc();
}

class SystemVault {
    public String type = "internal";
    public String externalPlayerVaultUUID = "0";
}

class ServiceFee {
    public double transferFee = 0.02;
    public double tradeFee = 0.1;
}

class Misc {
    public boolean logTransactionToConsole = true;
    public boolean logTradeToConsole = true;
}

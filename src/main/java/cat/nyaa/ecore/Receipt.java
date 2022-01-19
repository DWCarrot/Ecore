package cat.nyaa.ecore;

import java.util.List;
import java.util.UUID;

public interface Receipt {
    UUID getPayer();

    List<UUID> getReceiver();

    double getAmountPerTransaction();

    double getAmountTotally();

    double getFeePerTransaction();

    double getFeeTotally();

    double getCostPerTransaction();

    double getCostTotally();

    double getFeeRate();

    double getPayerRemain();

    long getTradeId();
}

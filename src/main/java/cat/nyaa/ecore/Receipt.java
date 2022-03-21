package cat.nyaa.ecore;

import java.util.List;
import java.util.UUID;

public interface Receipt {
    UUID getPayer();

    List<UUID> getReceiver();

    double getAmountArrivePerTransaction();

    double getAmountArriveTotally();

    double getFeePerTransaction();

    double getFeeTotally();

    double getAmountPerTransaction();

    double getAmountTotally();

    double getFeeRate();

    double getFeeRatePercent();

    double getPayerRemain();

    ServiceFeePreference getTaxPreference();

    long getId();
}

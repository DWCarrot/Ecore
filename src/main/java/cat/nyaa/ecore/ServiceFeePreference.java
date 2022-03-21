package cat.nyaa.ecore;

/**
 * Preference while charging service fee
 * INTERNAL means charge service fee from receiver. amount arrival will less than the payer specified if fee rate is positive number. For example, if payer transit 100 to receiver with 1% fee rate, payer will cost 100$ and receiver receives 99$.
 * <p>ADDITIONAL means charge service fee additional from payer. payer will pay additional money for service fee if service fee rate is positive. For example, if payer transit 100 to receiver with 1% fee rate, payer will cost 101$ and receiver will receive 100$</p>
 */
public enum ServiceFeePreference {
    INTERNAL, ADDITIONAL
}

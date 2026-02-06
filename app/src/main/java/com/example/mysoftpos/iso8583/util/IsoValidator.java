package com.example.mysoftpos.iso8583.util;

import com.example.mysoftpos.iso8583.util.IsoValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.mysoftpos.iso8583.message.IsoMessage;
import com.example.mysoftpos.iso8583.spec.IsoField;
import com.example.mysoftpos.iso8583.spec.FieldRules;
import com.example.mysoftpos.iso8583.spec.NapasFieldSpecConfig;

/**
 * Validates required/conditional fields presence and NAPAS formatting rules.
 * Combines "NapasFieldSpecConfig" (Format) and Business Logic (Rules).
 */
public final class IsoValidator {

    private IsoValidator() {
    }

    /**
     * Validate PURCHASE transaction (MTI 0200).
     */
    public static void validatePurchase(IsoMessage msg) {
        // 1. Format check based on Spec (Length, Regex)
        validateAgainstNapasSpec(msg.getMti(), msg.getFields(), true, "PURCHASE");

        // 2. Mandatory fields check
        validateRequired(msg, FieldRules.PURCHASE_REQUIRED, "PURCHASE");

        // 3. Logic check: Processing code vs MTI
        validateProcessingCodeByContext(msg, "PURCHASE");

        // 4. Business checks
        requireNotBlank(msg, IsoField.AMOUNT_4, "PURCHASE requires amount (F4)");
        requireCardData(msg, "PURCHASE");

        // NAPAS: Currency fixed VND 704
        String ccy = msg.getField(IsoField.CURRENCY_CODE_49);
        if (!"704".equals(ccy)) {
            throw new IllegalStateException("PURCHASE F49 must be '704' but was: " + ccy);
        }

        validateCommonFormats(msg, "PURCHASE");
        validatePosEntryMode22(msg, "PURCHASE");
        validatePurchaseConditional(msg);

        // Time format checks
        validateDateTimeFields(msg, "PURCHASE");

        // Private field checks
        validateField60IfPresent(msg, "PURCHASE");
    }

    /**
     * Validate PURCHASE response per NAPAS flow diagram: 0200 -> 0210.
     * Minimal requirements enforced for simulator:
     * - MTI must be 0210
     * - Must contain DE39 (Response Code)
     * - Validate present fields against {@link NapasFieldSpecConfig}
     */
    public static void validatePurchaseResponse(IsoMessage response) {
        if (response == null)
            throw new IllegalArgumentException("response == null");
        validateResponseAgainstNapasSpec(response.getMti(), response.getFields(), "PURCHASE-RESP");

        if (!"0210".equals(response.getMti())) {
            throw new IllegalStateException("PURCHASE response MTI must be 0210, got: " + response.getMti());
        }
        requireNotBlank(response, 39, "PURCHASE response requires F39 (Response Code)");
    }

    /**
     * Validate a response based on the original request MTI.
     * - 0200 -> 0210
     */
    public static void validateResponseByRequest(IsoMessage request, IsoMessage response) {
        if (request == null)
            throw new IllegalArgumentException("request == null");
        if (response == null)
            throw new IllegalArgumentException("response == null");

        String reqMti = request.getMti();
        if ("0200".equals(reqMti)) {
            validatePurchaseResponse(response);
            return;
        }
        throw new IllegalStateException("Unsupported request MTI for response validation: " + reqMti);
    }

    public static void validateAgainstNapasSpec(String mti, Map<Integer, String> fields, boolean validateMti,
            String contextName) {
        if (validateMti) {
            NapasFieldSpecConfig.FieldSpec mtiSpec = NapasFieldSpecConfig.get(0);
            if (mtiSpec != null) {
                validateValueBySpec(0, mti, mtiSpec, contextName);
            } else {
                if (mti == null || !mti.matches("\\d{4}")) {
                    throw new IllegalStateException(contextName + " invalid MTI: " + mti);
                }
            }
        }

        if (fields == null)
            return;

        for (Map.Entry<Integer, String> e : fields.entrySet()) {
            int de = e.getKey();
            String value = e.getValue();

            // Skip validation for mock fields or variable text fields to avoid false
            // positives
            if (de == IsoField.MERCHANT_NAME_LOCATION_43 || de == IsoField.MAC_128) {
                continue;
            }

            NapasFieldSpecConfig.FieldSpec spec = NapasFieldSpecConfig.get(de);
            if (spec == null) {
                continue; // Skip unknown fields
            }
            validateValueBySpec(de, value, spec, contextName);
        }
    }

    public static void validateResponseAgainstNapasSpec(String mti, Map<Integer, String> fields, String contextName) {
        validateAgainstNapasSpec(mti, fields, true, contextName == null ? "RESPONSE" : contextName);
    }

    private static void validateValueBySpec(int de, String value, NapasFieldSpecConfig.FieldSpec spec,
            String contextName) {
        if (value == null) {
            throw new IllegalStateException(contextName + " invalid DE" + de + ": null value (" + spec.errorCode + ")");
        }

        int len = value.length();
        if (len < spec.minLen || len > spec.maxLen) {
            throw new IllegalStateException(
                    contextName + " invalid DE" + de + " length=" + len + " expected " + spec.minLen + ".."
                            + spec.maxLen
                            + " (" + spec.errorCode + ")");
        }

        if (spec.pattern != null && !spec.pattern.matcher(value).matches()) {
            throw new IllegalStateException(
                    contextName + " invalid DE" + de + " pattern mismatch (" + spec.errorCode + ") value=" + value);
        }
    }

    private static void validateRequired(IsoMessage msg, Set<Integer> required, String name) {
        List<Integer> missing = new ArrayList<>();
        for (int f : required) {
            if (!msg.hasField(f) || isBlank(msg.getField(f))) {
                missing.add(f);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(name + " missing required fields: " + missing);
        }
    }

    private static void requireNotBlank(IsoMessage msg, int field, String error) {
        if (!msg.hasField(field) || isBlank(msg.getField(field))) {
            throw new IllegalStateException(error);
        }
    }

    private static void validateDateTimeFields(IsoMessage msg, String name) {
        validateNumericFixed(msg, IsoField.TRANSMISSION_DATETIME_7, 10, name + " F7 must be 10n (MMddHHmmss)");
        validateNumericFixed(msg, IsoField.LOCAL_TIME_12, 6, name + " F12 must be 6n (HHmmss)");
        validateNumericFixed(msg, IsoField.LOCAL_DATE_13, 4, name + " F13 must be 4n (MMdd)");
    }

    private static void validateNumericFixed(IsoMessage msg, int field, int len, String error) {
        String v = msg.getField(field);
        if (isBlank(v))
            return;
        if (v.length() != len || !v.matches("\\d{" + len + "}")) {
            throw new IllegalStateException(error + ". Got: " + v);
        }
    }

    /**
     * Logic check for Processing Code (F3) vs MTI.
     *
     * Diagram-based:
     * - Purchase request: MTI 0200, F3=000000
     */
    private static void validateProcessingCodeByContext(IsoMessage msg, String name) {
        String mti = msg.getMti();
        String f3 = msg.getField(IsoField.PROCESSING_CODE_3);
        if (isBlank(f3) || !f3.matches("\\d{6}")) {
            throw new IllegalStateException(name + " F3 must be 6n. Got: " + f3);
        }

        if ("0200".equals(mti)) {
            if (!"000000".equals(f3)) {
                throw new IllegalStateException(name + " PURCHASE requires F3=000000 (MTI=0200). Got: " + f3);
            }
            return;
        }

        throw new IllegalStateException(name + " unsupported MTI for request validation: " + mti);
    }

    private static void requireCardData(IsoMessage msg, String name) {
        String pan = msg.getField(IsoField.PAN_2);
        String track2 = msg.getField(IsoField.TRACK2_35);
        if (isBlank(pan) && isBlank(track2)) {
            throw new IllegalStateException(name + " requires card data: F2 (PAN) or F35 (Track2)");
        }
        if (isBlank(pan)) {
            throw new IllegalStateException(name + " requires PAN (F2)");
        }
    }

    private static void validateCommonFormats(IsoMessage msg, String name) {
        String f4 = msg.getField(IsoField.AMOUNT_4);
        if (!isBlank(f4) && !f4.matches("\\d{12}")) {
            throw new IllegalStateException(name + " F4 must be 12n zero-padded. Got: " + f4);
        }

        String f11 = msg.getField(IsoField.STAN_11);
        if (!isBlank(f11) && !f11.matches("\\d{6}")) {
            throw new IllegalStateException(name + " F11 must be 6n zero-padded. Got: " + f11);
        }

        String f41 = msg.getField(IsoField.TERMINAL_ID_41);
        if (!isBlank(f41) && f41.length() != 8) {
            throw new IllegalStateException(name + " F41 must be fixed 8 (space padded). Got length=" + f41.length());
        }

        String f42 = msg.getField(IsoField.MERCHANT_ID_42);
        if (!isBlank(f42) && f42.length() != 15) {
            throw new IllegalStateException(name + " F42 must be fixed 15 (space padded). Got length=" + f42.length());
        }
    }

    private static void validatePosEntryMode22(IsoMessage msg, String name) {
        String f22 = msg.getField(IsoField.POS_ENTRY_MODE_22);
        if (isBlank(f22))
            return;
        if (!f22.matches("\\d{3}")) {
            throw new IllegalStateException(name + " F22 must be 3n. Got: " + f22);
        }

        if (!("012".equals(f22) || "021".equals(f22) || "022".equals(f22) || f22.startsWith("01"))) {
            throw new IllegalStateException(name + " F22 unsupported/unknown value: " + f22);
        }
    }

    private static void validatePurchaseConditional(IsoMessage msg) {
        if (msg.hasField(IsoField.PIN_BLOCK_52) && isBlank(msg.getField(IsoField.PIN_BLOCK_52))) {
            throw new IllegalStateException("PURCHASE field 52 is blank");
        }
        // Chip Data check removed
    }

    private static void validateField60IfPresent(IsoMessage msg, String name) {
        if (!msg.hasField(IsoField.RESERVED_PRIVATE_60))
            return;
        String f60 = msg.getField(IsoField.RESERVED_PRIVATE_60);
        if (isBlank(f60)) {
            throw new IllegalStateException(name + " F60 is present but blank");
        }
        if (f60.length() > 60) {
            throw new IllegalStateException(name + " F60 exceeds 60 chars. len=" + f60.length());
        }
        for (int i = 0; i < f60.length(); i++) {
            char ch = f60.charAt(i);
            if (ch < 0x20 || ch > 0x7E) {
                throw new IllegalStateException(name + " F60 contains non-ASCII printable char at index " + i);
            }
        }

        // Extra strict check for UPI Chip Case A (27 digits total)
        if (f60.matches("\\d{27}")) {
            String cap = f60.substring(0, 1);
            String status = f60.substring(1, 2);
            String channel = f60.substring(2, 4);
            String reserved1 = f60.substring(4, 12);
            String exp = f60.substring(12, 15);
            String init = f60.substring(15, 16);
            String reserved2 = f60.substring(16, 27);

            if (!reserved1.equals("00000000"))
                throw new IllegalStateException(name + " F60 UPI reserved(5-12) must be 00000000");
            if (!reserved2.equals("00000000000"))
                throw new IllegalStateException(name + " F60 UPI reserved(5-15) must be 00000000000");
            if (!channel.matches("\\d{2}"))
                throw new IllegalStateException(name + " F60 UPI channel must be 2 digits");
            if (!exp.matches("\\d{3}"))
                throw new IllegalStateException(name + " F60 UPI exponent must be 3 digits");

            // Sanity checks (avoid unused vars warnings + enforce digit semantics)
            if (!cap.matches("[0-6]"))
                throw new IllegalStateException(name + " F60 UPI capability invalid: " + cap);
            if (!status.matches("[0-2]"))
                throw new IllegalStateException(name + " F60 UPI txn status invalid: " + status);
            if (!init.matches("[0-9]"))
                throw new IllegalStateException(name + " F60 UPI initiation invalid: " + init);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

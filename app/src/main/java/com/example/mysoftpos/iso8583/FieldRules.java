package com.example.mysoftpos.iso8583;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Field rules exactly as provided by user requirement. */
public final class FieldRules {
    private FieldRules() {}

    public static final Set<Integer> PURCHASE_REQUIRED;
    public static final Set<Integer> PURCHASE_CONDITIONAL;
    public static final Set<Integer> PURCHASE_OPTIONAL;

    public static final Set<Integer> REVERSAL_REQUIRED;
    public static final Set<Integer> REVERSAL_CONDITIONAL;
    public static final Set<Integer> REVERSAL_OPTIONAL;

    static {
        // Thanh toán - bắt buộc: 2,3,4,7,11,12,13,18,22,25,32,37,41,42,43,49
        HashSet<Integer> pr = new HashSet<>();
        Collections.addAll(pr,
                2,3,4,7,11,12,13,18,22,25,32,37,41,42,43,49);
        PURCHASE_REQUIRED = Collections.unmodifiableSet(pr);

        // Thanh toán - conditional: 19,23,35,52,55,60
        HashSet<Integer> pc = new HashSet<>();
        Collections.addAll(pc, 19,23,35,52,55,60);
        PURCHASE_CONDITIONAL = Collections.unmodifiableSet(pc);

        // Thanh toán - not mandatory: 14,36,45,128
        HashSet<Integer> po = new HashSet<>();
        Collections.addAll(po, 14,36,45,128);
        PURCHASE_OPTIONAL = Collections.unmodifiableSet(po);

        // Huỷ/Đảo - required + giữ nguyên: 2,3,4,7,11,12,13,18,32,37,41,42,43,49,90,128
        HashSet<Integer> rr = new HashSet<>();
        Collections.addAll(rr,
                2,3,4,7,11,12,13,18,32,37,41,42,43,49,90,128);
        REVERSAL_REQUIRED = Collections.unmodifiableSet(rr);

        // Huỷ/Đảo - conditional + giữ nguyên: 19,23,55,60
        HashSet<Integer> rc = new HashSet<>();
        Collections.addAll(rc, 19,23,55,60);
        REVERSAL_CONDITIONAL = Collections.unmodifiableSet(rc);

        // Huỷ/Đảo - not mandatory: 5,6,9,10,38,50,51,63
        HashSet<Integer> ro = new HashSet<>();
        Collections.addAll(ro, 5,6,9,10,38,50,51,63);
        REVERSAL_OPTIONAL = Collections.unmodifiableSet(ro);
    }
}


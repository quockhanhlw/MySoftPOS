package com.example.mysoftpos.iso8583;

import com.example.mysoftpos.data.local.TransactionTemplateEntity;
import com.example.mysoftpos.domain.model.CardInputData;
import com.example.mysoftpos.data.model.FieldConfig;
import com.example.mysoftpos.utils.ConfigManager;
import org.json.JSONObject;
import java.util.Iterator;

public class ConfigurableIsoBuilder {

    private final ConfigManager configManager;

    public ConfigurableIsoBuilder(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public IsoMessage build(TransactionTemplateEntity template, TransactionContext ctx, CardInputData card) {
        IsoMessage msg = new IsoMessage(template.mti);

        if (template.fieldConfigJson == null || template.fieldConfigJson.isEmpty()) {
            return msg;
        }

        try {
            JSONObject rules = new JSONObject(template.fieldConfigJson);
            Iterator<String> keys = rules.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                int field = Integer.parseInt(key);
                JSONObject ruleObj = rules.getJSONObject(key);

                FieldConfig rule = new FieldConfig();
                rule.type = ruleObj.optString("type");
                rule.value = ruleObj.optString("value");
                rule.param = ruleObj.optString("param");
                rule.generator = ruleObj.optString("generator");
                rule.configKey = ruleObj.optString("key");

                String value = resolveValue(rule, ctx, card);
                if (value != null) {
                    msg.setField(field, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return msg;
    }

    private String resolveValue(FieldConfig rule, TransactionContext ctx, CardInputData card) {
        if (rule.type == null)
            return null;

        switch (rule.type.toUpperCase()) {
            case "FIXED":
                return rule.value;

            case "INPUT":
                if ("amount".equalsIgnoreCase(rule.param))
                    return ctx.amount4;
                if ("pan".equalsIgnoreCase(rule.param))
                    return card.getPan();
                if ("expiry".equalsIgnoreCase(rule.param))
                    return card.getExpiryDate();
                if ("track2".equalsIgnoreCase(rule.param))
                    return card.getTrack2();
                if ("pin".equalsIgnoreCase(rule.param))
                    return card.getPinBlock();
                return null;

            case "AUTO":
                if ("STAN".equalsIgnoreCase(rule.generator))
                    return ctx.stan11;
                if ("RRN".equalsIgnoreCase(rule.generator))
                    return ctx.rrn37;
                if ("DATE".equalsIgnoreCase(rule.generator))
                    return ctx.localDate13;
                if ("TIME".equalsIgnoreCase(rule.generator))
                    return ctx.localTime12;
                if ("DATETIME".equalsIgnoreCase(rule.generator))
                    return ctx.transmissionDt7;
                return null;

            case "CONFIG":
                if ("terminal_id".equalsIgnoreCase(rule.configKey))
                    return configManager.getTerminalId();
                if ("merchant_id".equalsIgnoreCase(rule.configKey))
                    return configManager.getMerchantId();
                if ("merchant_name".equalsIgnoreCase(rule.configKey))
                    return configManager.getMerchantName();
                if ("currency".equalsIgnoreCase(rule.configKey))
                    return configManager.getCurrencyCode49();
                if ("mcc".equalsIgnoreCase(rule.configKey))
                    return configManager.getMcc18();
                return null;

            default:
                return null;
        }
    }
}

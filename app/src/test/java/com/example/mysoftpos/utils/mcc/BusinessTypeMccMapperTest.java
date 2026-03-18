package com.example.mysoftpos.utils.mcc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BusinessTypeMccMapperTest {

    @Test
    public void displayOptions_shouldNotExposeMccCodePrefixes() {
        assertFalse(BusinessTypeMccMapper.getDisplayOptions().isEmpty());
        for (String option : BusinessTypeMccMapper.getDisplayOptions()) {
            assertFalse(option.matches("^\\d{4}\\b.*"));
        }
    }

    @Test
    public void toMcc_shouldResolveDisplayLabelToInternalCode() {
        assertEquals("5411", BusinessTypeMccMapper.toMcc("Tạp hóa / siêu thị / Ô tô và phương tiện"));
        assertEquals("4814", BusinessTypeMccMapper.toMcc("Viễn thông"));
    }

    @Test
    public void toMcc_shouldSupportLegacyStoredValues() {
        assertEquals("4111", BusinessTypeMccMapper.toMcc("4111 - Vận tải hành khách chung"));
        assertEquals("4000", BusinessTypeMccMapper.toMcc("4000"));
    }

    @Test
    public void toDisplay_shouldHideCodeForStoredValues() {
        assertEquals("Vận tải hành khách chung", BusinessTypeMccMapper.toDisplay("4111 - Vận tải hành khách chung"));
        String display = BusinessTypeMccMapper.toDisplay("4814");
        assertFalse(display.isEmpty());
        assertFalse(display.matches("^\\d{4}\\b.*"));
        assertFalse("4814".equals(display));
    }

    @Test
    public void supportedSelection_shouldRejectUnknownValue() {
        assertTrue(BusinessTypeMccMapper.isSupportedSelection("Viễn thông"));
        assertFalse(BusinessTypeMccMapper.isSupportedSelection("Loại hình không tồn tại"));
    }
}

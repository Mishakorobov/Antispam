package ru.antispam.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun normalize_standardPlusSeven() {
        assertEquals(74951234567L, PhoneNormalizer.normalize("+7 (495) 123-45-67"))
        assertEquals(79031234567L, PhoneNormalizer.normalize("+79031234567"))
        assertEquals(79031234567L, PhoneNormalizer.normalize("+7-903-123-45-67"))
    }

    @Test
    fun normalize_leadingEight() {
        assertEquals(74951234567L, PhoneNormalizer.normalize("8 (495) 123-45-67"))
        assertEquals(79031234567L, PhoneNormalizer.normalize("89031234567"))
        assertEquals(79031234567L, PhoneNormalizer.normalize("8 903 123 45 67"))
    }

    @Test
    fun normalize_tenDigitsWithoutCountryCode() {
        assertEquals(79031234567L, PhoneNormalizer.normalize("9031234567"))
        assertEquals(74951234567L, PhoneNormalizer.normalize("4951234567"))
    }

    @Test
    fun normalize_invalidOrShortNumbers() {
        assertNull(PhoneNormalizer.normalize("12345"))
        assertNull(PhoneNormalizer.normalize(""))
        assertNull(PhoneNormalizer.normalize(null))
        assertNull(PhoneNormalizer.normalize("abc"))
        assertNull(PhoneNormalizer.normalize("123456789012345")) // Too long
    }

    @Test
    fun test_isKazakhstanOrForeignPlus7() {
        // Kazakhstan mobile & geographic ranges (600..799)
        assertTrue(PhoneNormalizer.isKazakhstanOrForeignPlus7(77011234567L)) // Kcell
        assertTrue(PhoneNormalizer.isKazakhstanOrForeignPlus7(77771234567L)) // Beeline KZ
        assertTrue(PhoneNormalizer.isKazakhstanOrForeignPlus7(77271234567L)) // Almaty geo
        assertTrue(PhoneNormalizer.isKazakhstanOrForeignPlus7(76001234567L)) // 6xx range

        // Russian ranges (300..599 and 800..999)
        assertFalse(PhoneNormalizer.isKazakhstanOrForeignPlus7(74951234567L)) // Moscow geo
        assertFalse(PhoneNormalizer.isKazakhstanOrForeignPlus7(78121234567L)) // SPb geo
        assertFalse(PhoneNormalizer.isKazakhstanOrForeignPlus7(79031234567L)) // Beeline RU
        assertFalse(PhoneNormalizer.isKazakhstanOrForeignPlus7(78001234567L)) // Toll-free RU
    }

    @Test
    fun test_formatRussian_kazakhstanTagging() {
        assertEquals("+7 (701) 123-45-67 (Казахстан)", PhoneNormalizer.formatRussian(77011234567L))
        assertEquals("+7 (903) 123-45-67", PhoneNormalizer.formatRussian(79031234567L))
        assertEquals("+7 (495) 123-45-67", PhoneNormalizer.formatRussian(74951234567L))
    }
}

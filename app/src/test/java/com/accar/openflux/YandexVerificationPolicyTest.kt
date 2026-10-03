package com.accar.openflux

import org.junit.Assert.*
import org.junit.Test

class YandexVerificationPolicyTest {
    @Test fun checkpointMayBeOnTheHostOrPath() {
        for (url in listOf("https://captcha.yandex.ru/", "https://smartcaptcha.yandex.ru/", "https://passport.yandex.kz/", "https://docs.yandex.ru/showcaptcha", "https://smartcaptcha.yandexcloud.net/", "about:blank")) {
            assertTrue(url, YandexVerificationPolicy.isCheckpoint(url))
        }
        assertFalse(YandexVerificationPolicy.isCheckpoint("https://disk.yandex.ru/i/document"))
    }
    @Test fun acceptsTrustedRegionalRedirects() {
        for (url in listOf("https://docs.yandex.kz/document/", "https://passport.yandex.by/", "https://yandex.com.tr/", "https://docs.yandex.uz/", "https://smartcaptcha.yandexcloud.net/", "https://DOCS.YANDEX.RU:443/")) {
            assertTrue(url, YandexVerificationPolicy.allows(url))
        }
    }
    @Test fun rejectsLookalikesAndUnsafeOrigins() {
        for (url in listOf("http://docs.yandex.kz/", "https://docs.yandex.kz.evil.example/", "https://evilyandex.ru/", "https://yandex.ru@evil.example/", "https://evil.example@yandex.ru/", "https://yandex.ru:8443/", "file:///yandex.ru", "https://other.yandexcloud.net/", "https://yandex.ru\\@evil.example/")) {
            assertFalse(url, YandexVerificationPolicy.allows(url))
        }
    }
    @Test fun finalDocumentCookieWinsOverRootAndInitialPage() {
        val initial = "https://docs.yandex.kz/captcha"
        val final = "https://docs.yandex.kz/document/abc"
        val headers = mapOf(final to "session=document; session=less-specific; token=a=b", initial to "session=initial; initialOnly=1", "https://yandex.kz/" to "session=root; regional=2", "https://yandex.ru/" to "session=ru-root; fallback=3")
        val values = YandexVerificationPolicy.collect(initial, final, headers::get)
        assertEquals("document", values["session"])
        assertEquals("a=b", values["token"])
        assertEquals("1", values["initialOnly"])
        assertEquals("2", values["regional"])
        assertNull(values["fallback"]) // do not scope a .ru-only cookie to .kz
        assertEquals("yandex.kz", YandexVerificationPolicy.cookieDomain(final))
    }
    @Test fun foreignPageIsNeverQueriedForCookies() {
        val queried = mutableListOf<String>()
        YandexVerificationPolicy.collect("https://docs.yandex.ru/", "https://evil.example/secret") { queried.add(it); "a=1" }
        assertFalse(queried.any { it.contains("evil.example") })
        assertEquals(queried.distinct(), queried)
    }
}

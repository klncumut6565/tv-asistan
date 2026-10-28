package com.siam.tvasistan

/**
 * Toshiba 50UA3D63DT (Vestel "shandao", Android 14, MediaTek) — sistem paketleri.
 * Kaynak: CleanYourTV examples/cecotec-shandao (aynı kart, aynı firmware UKNV.260528.001) ve
 * docs/CLEANYOURTV_ENTEGRASYON_RAPORU.md Bölüm 3.2 (TV'de ADB ile doğrulandı).
 */
object SistemPaketleri {
    /** Canlı TV ve kaynak menüsü. com.cltv.fast canlı TV DEĞİLDİR (Vestel'in kapalı FAST uygulaması). */
    const val CANLI_TV = "com.mediatek.wwtv.tvcenter"
    val CANLI_TV_DEGIL = setOf("com.cltv.fast")

    /** Asla sonlandırılmaz / kapatılmaz: donanım, girişler, kumanda, Wi-Fi, mağaza ve Google çekirdeği. */
    val KORUMALI = setOf(
        CANLI_TV,
        "com.mediatek.tv.service", "com.mediatek.tvinput", "com.mediatek.autopair",
        "com.mediatek.android.leanbacklauncher.partnercustomizer", // kapatılırsa tv.service çöküş döngüsü
        "com.mediatek.tvinputservice.arbitratorservice",
        "com.mediatek.android.tv.globalkeyoverlayvestel",
        "com.mediatek.tv.factory", "mediatek.factorymenu.ui", "com.mediatek.wwtv.setupwizard",
        "com.vestel.tv.service", "com.vestel.tv.keycontroller", "com.vestel.tv.backlight", "com.vestel.tv.eib",
        "com.google.android.tungsten.setupwraith", // kayıtlı Wi-Fi ağı ona bağlı olabilir
        "com.google.android.tvlauncher", "com.android.tv.settings", "com.android.vending",
        "com.google.android.gms", "com.google.android.gsf", "com.android.systemui",
        "com.android.providers.tv", "com.dolby.android.audio.service",
        "com.google.android.katniss", // kumandadaki Asistan tuşu
        "com.google.android.apps.mediashell", // Chromecast
        "fusion.android.tv.demo", // bazı Vestel'lerde kumandanın Kaynak tuşunu o işler
    )

    private val CEKIRDEK = Regex("^(android|com\\.android\\.(phone|server.*|shell|systemui|providers\\..*|inputmethod.*|bluetooth|networkstack.*|permissioncontroller))$")

    fun korumaliMi(paket: String): Boolean = paket in KORUMALI || CEKIRDEK.matches(paket)
}

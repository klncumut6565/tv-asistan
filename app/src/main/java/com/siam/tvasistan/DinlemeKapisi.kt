package com.siam.tvasistan

/**
 * Mikrofon sesinin nereye gideceği kararı (saf mantık, birim testli).
 *
 *  - GEMINI: ses konuşma kapısından Gemini Live'a gider (normal dinleme).
 *  - YEREL : ses Gemini'ye GİTMEZ; yalnız TV içindeki "Jülide" dinleyicisine ve son 3 sn tamponuna gider.
 *
 * Dinleme penceresi kumanda tuşu, "Jülide" kelimesi ya da cevap sonrası takip süresiyle açılır.
 * Pencere dışında:
 *  - Normal modda yalnız video/müzik çalarken (Jülide ses odağını almamışsa) YEREL olur — eski davranış AYNEN.
 *  - Kalabalık modunda her zaman YEREL olur: odadaki konuşmalar Gemini'ye hiç gitmez.
 */
object DinlemeKapisi {

    enum class Yol { GEMINI, YEREL }

    fun yol(
        surekliSohbet: Boolean,
        simdi: Long,
        pencereBitis: Long,
        medyaAktif: Boolean,
        odakJulidede: Boolean,
        kalabalik: Boolean,
    ): Yol {
        if (!surekliSohbet || simdi <= pencereBitis) return Yol.GEMINI
        if (kalabalik) return Yol.YEREL
        return if (medyaAktif && !odakJulidede) Yol.YEREL else Yol.GEMINI
    }

    /** Telefonda başlayan yeni konuşma dinleme penceresini açsın mı (ve Jülide'nin sözünü kessin mi)? */
    fun telefonPencereAcsin(kalabalik: Boolean, simdi: Long, pencereBitis: Long): Boolean =
        !kalabalik || simdi <= pencereBitis
}

package com.nube.physiohelper.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import com.nube.physiohelper.data.Settings
import java.util.Locale

/** Voice cues, beeps and vibration for the session timer. */
class Speaker(context: Context, private val settings: Settings) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var utterance = 0
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }.getOrNull()
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val r = tts?.setLanguage(Locale("en", "IN"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale.ENGLISH)
            tts?.setSpeechRate(1.05f)
        }
    }

    fun say(text: String, flush: Boolean = true) {
        if (!settings.voiceOn || !ready) return
        tts?.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "u${utterance++}")
    }

    fun beep(long: Boolean = false) {
        if (!settings.beepsOn) return
        tone?.startTone(if (long) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, if (long) 350 else 120)
    }

    fun buzz(ms: Long = 120) {
        if (!settings.vibrateOn) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        runCatching { tone?.release() }
    }
}

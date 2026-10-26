package dev.russell.mmtrainer.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Text-to-speech for audio practice, over the system TTS engine (voice and speech rate come from
 * the system's text-to-speech settings). Callbacks run on the main thread.
 */
class Speaker(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private class Pending(val lastWord: Int, val onHeard: (Boolean) -> Unit)
    private val pending = mutableMapOf<String, Pending>()            // utterance id → its callback
    private var nextId = 0
    private var ready: Boolean? = null                               // null while the engine starts
    private var queued: Pair<String, (Boolean) -> Unit>? = null      // asked for before the engine was up
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { onInit(status == TextToSpeech.SUCCESS) }
    }

    private fun onInit(ok: Boolean) {
        ready = ok && pickEnglish()
        if (ready == true) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}
                override fun onDone(id: String) = finish(id, true)
                override fun onRangeStart(id: String, start: Int, end: Int, frame: Int) {
                    main.post {
                        if (end > (pending[id]?.lastWord ?: Int.MAX_VALUE)) pending.remove(id)?.onHeard?.invoke(true)
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String) = finish(id, false)
                override fun onError(id: String, errorCode: Int) = finish(id, false)
                override fun onStop(id: String, interrupted: Boolean) { main.post { pending.remove(id) } }
            })
        }
        queued?.let { (text, onHeard) -> speak(text, onHeard) }
        queued = null
    }

    /** The prompts are English: keep an English device locale (en-GB etc.), otherwise use US English. */
    private fun pickEnglish(): Boolean {
        val device = Locale.getDefault()
        for (locale in listOf(device, Locale.US).filter { it.language == "en" }) {
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
                return true
            }
        }
        return false
    }

    private fun finish(id: String, spoken: Boolean) {
        main.post { pending.remove(id)?.onHeard?.invoke(spoken) }
    }

    /**
     * Say [text], cutting off anything still being said. [onHeard] gets true as the last word starts
     * (once it has all been said, if the engine doesn't report word progress), or false if speech
     * isn't available. It isn't called when later speech interrupts this.
     */
    fun speak(text: String, onHeard: (Boolean) -> Unit = {}) {
        when (ready) {
            null -> queued = text to onHeard
            false -> onHeard(false)
            true -> {
                val id = "u${nextId++}"
                pending[id] = Pending(text.trimEnd().lastIndexOf(' ') + 1, onHeard)
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                    pending.remove(id)
                    onHeard(false)
                }
            }
        }
    }

    fun stop() {
        queued = null
        pending.clear()
        if (ready == true) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }
}

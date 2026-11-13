package com.example.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusView: TextView

    private val aiModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = "YOUR_GEMINI_API_KEY",
        systemInstruction = content {
            text("Act as a concise, helpful British voice assistant. Keep replies under two short sentences.")
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        statusView = TextView(this).apply {
            text = "Tap screen to give a voice command"
            textSize = 20f
            setPadding(60, 200, 60, 60)
            setOnClickListener { beginVoiceCapture() }
        }
        setContentView(statusView)

        checkMicPermission()
        tts = TextToSpeech(this, this)
        initRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.UK
            tts.setPitch(0.9f)
            tts.setSpeechRate(1.05f)
            speakOut("Assistant initialized and ready.")
        }
    }

    private fun initRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusView.text = "Listening..."
            }
            override fun onResults(results: Bundle?) {
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                statusView.text = "Heard: $transcript"
                handleOrder(transcript.lowercase(Locale.getDefault()))
            }
            override fun onError(error: Int) {
                statusView.text = "Tap to retry voice capture"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun beginVoiceCapture() {
        val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer.startListening(voiceIntent)
    }

    private fun handleOrder(cmd: String) {
        when {
            cmd.contains("flashlight on") || cmd.contains("torch on") -> {
                switchTorch(true)
                speakOut("Flashlight activated.")
            }
            cmd.contains("flashlight off") || cmd.contains("torch off") -> {
                switchTorch(false)
                speakOut("Flashlight deactivated.")
            }
            cmd.contains("battery") -> {
                val mgr = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = mgr.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                speakOut("Battery charge is at $pct percent.")
            }
            cmd.startsWith("open ") -> {
                val target = cmd.removePrefix("open ").trim()
                launchApp(target)
            }
            cmd.contains("alarm") -> {
                startActivity(Intent(AlarmClock.ACTION_SET_ALARM))
                speakOut("Opening alarm setup.")
            }
            else -> queryAi(cmd)
        }
    }

    private fun switchTorch(enabled: Boolean) {
        try {
            val camMgr = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val firstCam = camMgr.cameraIdList[0]
            camMgr.setTorchMode(firstCam, enabled)
        } catch (ex: Exception) {
            speakOut("Camera torch hardware unavailable.")
        }
    }

    private fun launchApp(targetName: String) {
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matched = installed.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(targetName)
        }
        if (matched != null) {
            val startIntent = pm.getLaunchIntentForPackage(matched.packageName)
            startActivity(startIntent)
            speakOut("Opening $targetName.")
        } else {
            speakOut("Application $targetName was not found.")
        }
    }

    private fun queryAi(userPrompt: String) {
        lifecycleScope.launch {
            try {
                val res = aiModel.generateContent(userPrompt)
                val answer = res.text ?: "No response generated."
                statusView.text = answer
                speakOut(answer)
            } catch (ex: Exception) {
                speakOut("Unable to reach the language model.")
            }
        }
    }

    private fun speakOut(message: String) {
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "VoiceUtterance")
    }

    private fun checkMicPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}

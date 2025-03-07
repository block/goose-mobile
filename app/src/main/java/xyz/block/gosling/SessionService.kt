package xyz.block.gosling

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import xyz.block.gosling.features.assistant.AssistantActivity

class SessionService : VoiceInteractionSessionService() {
    override fun onNewSession(bundle: Bundle?): VoiceInteractionSession {
        return Session(this)
    }

    private class Session(context: Context) : VoiceInteractionSession(context) {
        private var speechRecognizer: SpeechRecognizer? = null

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)

            Log.d("GOS", "onShow!")

            val intent = Intent(context, AssistantActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            Log.d("GOS", "starting gosling assistant")
            context.startActivity(intent)

            Log.d("GOS", "starting speech")
            startSpeechRecognition()
        }

        private fun startSpeechRecognition() {
            Log.d("GOS", "Creating SpeechRecognizer...")

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            if (speechRecognizer == null) {
                Log.d("GOS", "SpeechRecognizer creation FAILED")
                return
            } else {
                Log.d("GOS", "SpeechRecognizer created successfully.")
            }


            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val voiceCommand =
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                    if (!voiceCommand.isNullOrEmpty()) {
                        println("Command: $voiceCommand") // Send this to command processor
                    }
                    hide()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val voiceCommand =
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                    if (!voiceCommand.isNullOrEmpty()) {
                        Log.d("GOS", "Partial command: $voiceCommand")
                    } else {
                        Log.d("GOS", "No partial command received.")
                    }
                }

                override fun onError(error: Int) {
                    Log.d("GOS", "Error occurred: $error")
                    hide()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("GOS", "Ready for speech.")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("GOS", "Beginning of speech.")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    Log.d("GOS", "RMS changed: $rmsdB")
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    Log.d("GOS", "Buffer received.")
                }

                override fun onEndOfSpeech() {
                    Log.d("GOS", "End of speech.")
                    hide()
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d("GOS", "Event occurred: $eventType")
                }
            })

            speechRecognizer?.startListening(intent)
            Log.d("GOS", "Started listening for speech.")
        }
    }
}

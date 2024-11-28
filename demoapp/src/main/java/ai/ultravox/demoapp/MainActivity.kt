package ai.ultravox.demoapp

import ai.ultravox.Transcript
import ai.ultravox.UltravoxSession
import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

    private lateinit var joinButton: Button
    private lateinit var muteMicButton: Button
    private lateinit var muteSpeakerButton: Button
    private lateinit var joinText: EditText
    private lateinit var session: UltravoxSession
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    joinCall()
                } else {
                    Toast.makeText(
                        this.applicationContext,
                        "Missing record permission",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        joinButton = findViewById(R.id.join_button)
        muteMicButton = findViewById(R.id.mic_mute_button)
        muteSpeakerButton = findViewById(R.id.speaker_mute_button)
        joinText = findViewById(R.id.join_url_text)
        session = UltravoxSession(applicationContext, lifecycleScope)
        joinButton.setOnClickListener { onJoinClicked() }
        muteMicButton.visibility = View.INVISIBLE
        muteSpeakerButton.visibility = View.INVISIBLE
        muteMicButton.setOnClickListener { onMicMute() }
        muteSpeakerButton.setOnClickListener { onSpeakerMute() }
    }

    private fun onJoinClicked() {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            joinCall()
        } else {
            requestPermissionLauncher.launch(RECORD_AUDIO)
        }
    }

    private fun onLeaveClicked() {
        session.leaveCall()
        joinButton.text = "Join"
        joinButton.setOnClickListener { onJoinClicked() }
        muteMicButton.visibility = View.INVISIBLE
        muteSpeakerButton.visibility = View.INVISIBLE
    }

    private fun onMicMute() {
        session.toggleMicMuted()
        if (session.micMuted) {
            muteMicButton.text = "Unmute User"
        } else {
            muteMicButton.text = "Mute User"
        }
    }

    private fun onSpeakerMute() {
        session.toggleSpeakerMuted()
        if (session.speakerMuted) {
            muteSpeakerButton.text = "Unmute Agent"
        } else {
            muteSpeakerButton.text = "Mute Agent"
        }
    }

    private fun joinCall() {
        session.listen("transcripts") {
            run {
                val last = session.lastTranscript
                if (last != null && last.isFinal) {
                    val prefix = if (last.speaker == Transcript.Role.USER) "USER: " else "AGENT: "
                    Toast.makeText(this.applicationContext, prefix + last.text, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
        session.joinCall(joinText.text.toString())
        joinButton.text = "Leave"
        joinButton.setOnClickListener { onLeaveClicked() }
        muteMicButton.visibility = View.VISIBLE
        muteSpeakerButton.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        session.leaveCall()
        super.onDestroy()
    }
}
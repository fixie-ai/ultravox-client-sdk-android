package ai.ultravox.demoapp

import ai.ultravox.Transcript
import ai.ultravox.UltravoxSession
import android.Manifest.permission.RECORD_AUDIO
import android.content.pm.PackageManager
import android.os.Bundle
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

class MainActivity : AppCompatActivity() {

    private lateinit var joinButton: Button
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
        joinText = findViewById(R.id.join_url_text)
        session = UltravoxSession(applicationContext, lifecycleScope)
        joinButton.setOnClickListener { onJoinClicked() }
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

    private fun joinCall() {
        val sessionState = session.joinCall(joinText.text.toString())
        sessionState.listen("transcript") {
            run {
                val last = sessionState.lastTranscript
                if (last != null && last.isFinal) {
                    val prefix = if (last.speaker == Transcript.Role.USER) "USER: " else "AGENT: "
                    Toast.makeText(this.applicationContext, prefix + last.text, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
        sessionState.listen("status") {
            run {
                Toast.makeText(
                    this.applicationContext,
                    sessionState.status.name,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        session.leaveCall()
        super.onDestroy()
    }
}
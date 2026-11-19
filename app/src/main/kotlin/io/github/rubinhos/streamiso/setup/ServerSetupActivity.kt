package io.github.rubinhos.streamiso.setup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import io.github.rubinhos.streamiso.R
import io.github.rubinhos.streamiso.server.Health
import io.github.rubinhos.streamiso.server.ServerAddress
import io.github.rubinhos.streamiso.server.ServerChoice
import io.github.rubinhos.streamiso.server.ServerList
import io.github.rubinhos.streamiso.server.ServerStore
import io.github.rubinhos.streamiso.server.StreamIsoClient
import io.github.rubinhos.streamiso.web.WebShellActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Asks for the server's address (and optionally a second one, such as its Tailscale address) and which to use,
 * and checks them with GET /healthz before saving.
 */
class ServerSetupActivity : Activity() {

    private val background: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var addressInput: EditText
    private lateinit var secondInput: EditText
    private lateinit var choiceGroup: RadioGroup
    private lateinit var errorText: TextView
    private lateinit var connectButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_setup)

        addressInput = findViewById(R.id.address_input)
        secondInput = findViewById(R.id.second_input)
        choiceGroup = findViewById(R.id.choice_group)
        errorText = findViewById(R.id.error_text)
        connectButton = findViewById(R.id.connect_button)
        progress = findViewById(R.id.progress)

        if (savedInstanceState == null) {
            ServerStore(this).load()?.let { saved ->
                addressInput.setText(saved.first.display)
                secondInput.setText(saved.second?.display.orEmpty())
                choiceGroup.check(
                    when (saved.choice) {
                        ServerChoice.AUTO -> R.id.choice_auto
                        ServerChoice.FIRST -> R.id.choice_first
                        ServerChoice.SECOND -> R.id.choice_second
                    },
                )
            }
        }
        connectButton.setOnClickListener { connect() }
        val go = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connect()
                true
            } else {
                false
            }
        }
        addressInput.setOnEditorActionListener(go)
        secondInput.setOnEditorActionListener(go)
    }

    override fun onDestroy() {
        background.shutdownNow()
        super.onDestroy()
    }

    private fun connect() {
        val first = ServerAddress.parse(addressInput.text.toString())
        if (first == null) {
            showError(getString(R.string.setup_error_invalid))
            return
        }
        val secondText = secondInput.text.toString().trim()
        val second = if (secondText.isEmpty()) null else ServerAddress.parse(secondText)
        if (secondText.isNotEmpty() && second == null) {
            showError(getString(R.string.setup_error_second_invalid))
            return
        }
        val choice = when (choiceGroup.checkedRadioButtonId) {
            R.id.choice_first -> ServerChoice.FIRST
            R.id.choice_second -> if (second != null) ServerChoice.SECOND else ServerChoice.AUTO
            else -> ServerChoice.AUTO
        }
        val servers = ServerList(first, second, choice)
        setBusy(true)
        errorText.visibility = View.GONE
        background.execute {
            val (reached, health) = StreamIsoClient.reach(servers)
            runOnUiThread {
                if (!isDestroyed) onChecked(servers, reached, health)
            }
        }
    }

    private fun onChecked(servers: ServerList, reached: ServerAddress, health: Health) {
        setBusy(false)
        when (health) {
            Health.OK -> {
                ServerStore(this).save(servers)
                // Replaces everything: a shell that was open still points to the old address.
                startActivity(
                    Intent(this, WebShellActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                finish()
            }
            Health.NOT_STREAMISO -> showError(getString(R.string.setup_error_not_streamiso, reached.display))
            Health.UNREACHABLE -> showError(
                getString(R.string.setup_error_unreachable, servers.candidates.joinToString(" / ") { it.display }),
            )
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun setBusy(busy: Boolean) {
        connectButton.isEnabled = !busy
        addressInput.isEnabled = !busy
        secondInput.isEnabled = !busy
        choiceGroup.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
    }
}

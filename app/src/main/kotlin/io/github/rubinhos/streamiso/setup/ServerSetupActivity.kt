package io.github.rubinhos.streamiso.setup

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import io.github.rubinhos.streamiso.R
import io.github.rubinhos.streamiso.server.Health
import io.github.rubinhos.streamiso.server.ServerAddress
import io.github.rubinhos.streamiso.server.ServerStore
import io.github.rubinhos.streamiso.server.StreamIsoClient
import io.github.rubinhos.streamiso.web.WebShellActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Asks for the server address and checks it with GET /healthz before saving it. */
class ServerSetupActivity : Activity() {

    private val background: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var addressInput: EditText
    private lateinit var errorText: TextView
    private lateinit var connectButton: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_setup)

        addressInput = findViewById(R.id.address_input)
        errorText = findViewById(R.id.error_text)
        connectButton = findViewById(R.id.connect_button)
        progress = findViewById(R.id.progress)

        if (savedInstanceState == null) {
            addressInput.setText(ServerStore(this).load()?.display.orEmpty())
        }
        connectButton.setOnClickListener { connect() }
        addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connect()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        background.shutdownNow()
        super.onDestroy()
    }

    private fun connect() {
        val address = ServerAddress.parse(addressInput.text.toString())
        if (address == null) {
            showError(getString(R.string.setup_error_invalid))
            return
        }
        setBusy(true)
        errorText.visibility = View.GONE
        background.execute {
            val health = StreamIsoClient(address).checkHealth()
            runOnUiThread {
                if (!isDestroyed) onChecked(address, health)
            }
        }
    }

    private fun onChecked(address: ServerAddress, health: Health) {
        setBusy(false)
        when (health) {
            Health.OK -> {
                ServerStore(this).save(address)
                // Replaces everything: a shell that was open still points to the old address.
                startActivity(
                    Intent(this, WebShellActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                finish()
            }
            Health.NOT_STREAMISO -> showError(getString(R.string.setup_error_not_streamiso, address.display))
            Health.UNREACHABLE -> showError(getString(R.string.setup_error_unreachable, address.display))
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun setBusy(busy: Boolean) {
        connectButton.isEnabled = !busy
        addressInput.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
    }
}

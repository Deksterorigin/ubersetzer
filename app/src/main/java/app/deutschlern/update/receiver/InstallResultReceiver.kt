package app.deutschlern.update.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifier: InstallStatusNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (_: Exception) {
                        // Background activity start may be restricted on some Android versions.
                        // The ViewModel/UI will also receive this confirmationIntent via notifier and can launch it.
                    }

                    if (::notifier.isInitialized) {
                        notifier.emitEvent(InstallEvent.PendingUserAction(confirmIntent))
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                if (::notifier.isInitialized) {
                    notifier.emitEvent(InstallEvent.Success)
                }
            }

            else -> {
                if (::notifier.isInitialized) {
                    notifier.emitEvent(InstallEvent.Failure(status, message))
                }
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.deutschlern.action.INSTALL_STATUS"
    }
}

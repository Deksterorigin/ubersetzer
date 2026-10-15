package app.deutschlern.update.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.deutschlern.update.model.UpdateError
import app.deutschlern.update.receiver.InstallResultReceiver
import java.io.File
import java.io.FileInputStream

/**
 * Interface wrapping PackageInstaller session management.
 * Allows injecting fake implementations in unit tests.
 */
interface PackageInstallerWrapper {
    fun install(apkFile: File): Result<Unit>
    fun canRequestPackageInstalls(): Boolean
}

/**
 * Standard implementation using Android PackageInstaller.
 */
class DefaultPackageInstallerWrapper(
    private val context: Context
) : PackageInstallerWrapper {

    override fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    override fun install(apkFile: File): Result<Unit> {
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                setSize(apkFile.length())
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                activeSession.openWrite("package", 0, apkFile.length()).use { outputStream ->
                    FileInputStream(apkFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    activeSession.fsync(outputStream)
                }

                // Explicit intent targeting InstallResultReceiver
                val callbackIntent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = InstallResultReceiver.ACTION_INSTALL_STATUS
                    setPackage(context.packageName)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    callbackIntent,
                    flags
                )

                activeSession.commit(pendingIntent.intentSender)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(UpdateError.GenericError("PackageInstaller session error: ${e.message}"))
        }
    }
}

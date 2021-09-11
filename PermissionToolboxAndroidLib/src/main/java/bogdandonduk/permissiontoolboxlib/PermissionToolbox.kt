package bogdandonduk.permissiontoolboxlib

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

object PermissionToolbox {
    @PublishedApi
    internal var userSentToAppSettings = false

    private const val delimiter = "_"

    private const val LIBRARY_SHARED_PREFS_SUFFIX = "${delimiter}shared${delimiter}prefs${delimiter}bogdandonduk.permissiontoolboxlib"
    private const val DO_NOT_ASK_AGAIN_SUFFIX = "${delimiter}do${delimiter}not${delimiter}ask${delimiter}again${delimiter}$LIBRARY_SHARED_PREFS_SUFFIX"

    private const val PACKAGE_SCHEME = "package"

    @PublishedApi
    internal const val READ_EXTERNAL_STORAGE_REQUEST_CODE = 1

    @PublishedApi
    internal const val READ_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 2

    private fun getPreferences(context: Context) =
        context.getSharedPreferences(context.packageName + LIBRARY_SHARED_PREFS_SUFFIX, Context.MODE_PRIVATE)

    @PublishedApi
    internal fun isDoNotAskAgainFlagSet(context: Context, permission: String) = getPreferences(context).getBoolean("$permission$DO_NOT_ASK_AGAIN_SUFFIX", false)

    @PublishedApi
    internal fun setDoNotAskAgainFlag(context: Context, permission: String, value: Boolean = true) {
        getPreferences(context).edit().apply() {
            val key = "$permission$DO_NOT_ASK_AGAIN_SUFFIX"

            if(value)
                putBoolean(key, true)
            else
                remove(key)
        }.apply()
    }

    @PublishedApi
    internal fun sendUserToAppSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)

            if(context !is Activity)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })

        userSentToAppSettings = true
    }

    fun sendUserToAppSettingsForManageStorage(context: Context) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts(PACKAGE_SCHEME, context.packageName, null)

                if(context !is Activity)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })

            userSentToAppSettings = true
        }
    }

    inline fun handleUserReturnFromAppSettingsForPermission(activity: Activity, permission: String, deniedAction: () -> Unit, allowedAction: () -> Unit) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && userSentToAppSettings) {
            userSentToAppSettings = false

            if(activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
                allowedAction.invoke()
            else
                deniedAction.invoke()
        }
    }

    inline fun handleUserReturnFromAppSettingsForManageStorage(context: Context, deniedAction: () -> Unit, allowedAction: () -> Unit) {
        if(userSentToAppSettings) {
            userSentToAppSettings = false

            if(checkManageExternalStorage(context))
                allowedAction.invoke()
            else
                deniedAction.invoke()
        }
    }

    fun checkSingle(context: Context, permission: String) =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        else true

    fun checkManageExternalStorage(context: Context) =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            else
                Environment.isExternalStorageManager()
        } else
            true

    fun checkReadManageExternalStorage(context: Context) =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
                context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            else
                Environment.isExternalStorageManager()
        } else
            true

    inline fun executeForReadExternalStorageOrRequest(activity: Activity, deniedRationaleAction: () -> Unit, doNotAskAgainRationaleAction: () -> Unit, grantedAction: () -> Unit) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                checkSingle(activity, Manifest.permission.READ_EXTERNAL_STORAGE) -> grantedAction.invoke()
                activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> deniedRationaleAction.invoke()
                isDoNotAskAgainFlagSet(activity, Manifest.permission.READ_EXTERNAL_STORAGE) -> doNotAskAgainRationaleAction.invoke()

                else -> activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE_REQUEST_CODE)
            }
        } else
            grantedAction.invoke()
    }

    inline fun handleRequestResultReadExternalStorage(activity: Activity, deniedAction: () -> Unit, grantedAction: () -> Unit) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSingle(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                setDoNotAskAgainFlag(activity, Manifest.permission.READ_EXTERNAL_STORAGE, false)

                grantedAction.invoke()
            } else {
                if(!activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE))
                    setDoNotAskAgainFlag(activity, Manifest.permission.READ_EXTERNAL_STORAGE, false)

                deniedAction.invoke()
            }
        } else
            grantedAction.invoke()
    }

    inline fun executeForManageExternalStorageOrRequest(activity: Activity,  deniedRationaleAction: (requestAction: () -> Int) -> Unit, doNotAskAgainRationaleAction: (requestAction: () -> Unit) -> Unit, manageExternalStorageApi30RationaleAction: (requestAction: () -> Unit) -> Unit, grantedAction: () -> Unit) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                when {
                    activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        -> grantedAction.invoke()

                    activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) || activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        -> deniedRationaleAction.invoke {
                            activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), READ_WRITE_EXTERNAL_STORAGE_REQUEST_CODE)

                            READ_WRITE_EXTERNAL_STORAGE_REQUEST_CODE
                        }

                    isDoNotAskAgainFlagSet(activity, Manifest.permission.READ_EXTERNAL_STORAGE) || isDoNotAskAgainFlagSet(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        -> doNotAskAgainRationaleAction.invoke {
                            sendUserToAppSettings(activity)
                        }

                    else -> activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), READ_WRITE_EXTERNAL_STORAGE_REQUEST_CODE)
                }
            } else {
                if(Environment.isExternalStorageManager())
                    grantedAction.invoke()
                else
                    manageExternalStorageApi30RationaleAction.invoke {
                        sendUserToAppSettingsForManageStorage(activity)
                    }
            }
        } else
            grantedAction.invoke()
    }

    inline fun executeForManageExternalStorage(activity: Activity, deniedAction: () -> Unit = {  }, grantedAction: () -> Unit) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if(activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                    grantedAction.invoke()
                else
                    deniedAction.invoke()
            } else {
                if(Environment.isExternalStorageManager())
                    grantedAction.invoke()
                else
                    deniedAction.invoke()
            }
        else
            grantedAction.invoke()
    }

    inline fun handleRequestResultManageExternalStorage(activity: Activity, deniedAction: () -> Unit, grantedAction: () -> Unit) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if(checkSingle(activity, Manifest.permission.READ_EXTERNAL_STORAGE) && checkSingle(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                setDoNotAskAgainFlag(activity, Manifest.permission.READ_EXTERNAL_STORAGE, false)
                setDoNotAskAgainFlag(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE, false)

                grantedAction.invoke()
            } else {
                if(!activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) && !activity.shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    setDoNotAskAgainFlag(activity, Manifest.permission.READ_EXTERNAL_STORAGE, true)
                    setDoNotAskAgainFlag(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE, true)
                }

                deniedAction.invoke()
            }
        } else
            grantedAction.invoke()
    }

    fun restoreState() {

    }
}
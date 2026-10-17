package com.daytoday.server

import com.daytoday.usecase.UserUseCases
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ensures the app always has a valid session against the phone-local server.
 *
 * The app has no login UI; it is a single-user personal app backed by the server
 * running on the same device. On first run (or if the stored token is stale) this
 * logs in the fixed local account, registering it if it does not exist yet.
 */
@Singleton
class SessionProvisioner @Inject constructor(
    private val userUseCases: UserUseCases,
) {
    suspend fun ensure(): Boolean {
        val login = userUseCases.login(LOCAL_EMAIL, LOCAL_PASSWORD)
        android.util.Log.i("LocalServer", "login: success=${login.isSuccess} error=${login.errorOrNull()}")
        if (login.isSuccess) return true
        val register = userUseCases.register(LOCAL_EMAIL, LOCAL_PASSWORD, LOCAL_NAME)
        android.util.Log.i("LocalServer", "register: success=${register.isSuccess} error=${register.errorOrNull()}")
        return register.isSuccess
    }

    companion object {
        const val LOCAL_EMAIL = "local@daytoday.app"
        const val LOCAL_PASSWORD = "daytoday-local"
        const val LOCAL_NAME = "Local"
    }
}

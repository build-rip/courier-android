package rip.build.courier.ui.navigation

import androidx.lifecycle.ViewModel
import rip.build.courier.data.remote.auth.AuthPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavGraphViewModel @Inject constructor(
    val authPreferences: AuthPreferences
) : ViewModel()

package com.flovar.attendance

import android.location.Location
import android.os.Build

@Suppress("DEPRECATION")
val Location.mocked: Boolean
    get() = if (Build.VERSION.SDK_INT >= 31) isMock else isFromMockProvider

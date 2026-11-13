package com.flovar.attendance

object Config {
    const val BASE_URL = "https://erp.flovarservices.com"
    const val MAX_ACCURACY_M = 50f   // reject weak GPS fixes on the device (server re-checks)
}

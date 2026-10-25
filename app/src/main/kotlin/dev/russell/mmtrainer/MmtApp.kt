package dev.russell.mmtrainer

import android.app.Application
import dev.russell.mmtrainer.data.AndroidDb

class MmtApp : Application() {
    /** The single progress store; every Store/Session call runs on [dev.russell.mmtrainer.ui.StoreThread]. */
    val store: Store by lazy { Store(AndroidDb(this)) }
}

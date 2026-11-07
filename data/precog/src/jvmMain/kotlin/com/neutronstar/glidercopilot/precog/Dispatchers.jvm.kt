package com.neutronstar.glidercopilot.precog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val ioDispatcher: CoroutineDispatcher get() = Dispatchers.IO

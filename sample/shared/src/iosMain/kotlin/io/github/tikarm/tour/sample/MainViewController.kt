package io.github.tikarm.tour.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused") // Called from Swift
fun MainViewController(): UIViewController = ComposeUIViewController { SampleApp() }

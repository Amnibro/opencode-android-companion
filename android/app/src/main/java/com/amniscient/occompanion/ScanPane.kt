// Copyright 2026 Amniscient. SPDX-License-Identifier: MIT
package com.amniscient.occompanion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "CompanionScan"

@Composable
fun ScanPane(injectImage: String?, error: String, onBack: () -> Unit, onCode: (String) -> Unit) {
    val term = LocalTerm.current
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var note by remember { mutableStateOf("Point the camera at the pairing code. The terminal draws it light on dark.") }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    val askedLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        asked = true
        granted = it
    }
    val consumed = remember { AtomicBoolean(false) }
    val preview = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val activity = context as? android.app.Activity
    val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    val permissionNote = ScanAccess.message(granted, asked, canAskAgain)

    LaunchedEffect(error) {
        if (error.isNotBlank()) consumed.set(false)
    }

    LaunchedEffect(injectImage) {
        val path = injectImage
        if (!path.isNullOrBlank()) {
            note = "Reading the injected pairing image."
            val hit = decodeFile(path)
            if (hit == null) note = Pairing.UNREADABLE
            else if (consumed.compareAndSet(false, true)) onCode(hit.text)
        } else if (!granted) {
            askedLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(granted, injectImage) {
        if (!granted || !injectImage.isNullOrBlank()) {
            onDispose { }
        } else {
            val exec = Executors.newSingleThreadExecutor()
            val frames = AtomicInteger()
            val future = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                try {
                    val camera = future.get()
                    val previewUse = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(exec) { image ->
                        val n = frames.incrementAndGet()
                        try {
                            val hit = decodeProxy(image)
                            if (hit != null) {
                                Log.d(TAG, "frame $n decoded len=${hit.text.length} inverted=${hit.inverted}")
                                if (consumed.compareAndSet(false, true)) onCode(hit.text)
                            } else if (n == 1 || n % 30 == 0) {
                                Log.d(TAG, "frame $n no qr")
                            }
                        } catch (e: Exception) {
                            if (n == 1 || n % 30 == 0) Log.d(TAG, "frame $n analyzer error ${e.javaClass.simpleName}")
                        } finally {
                            image.close()
                        }
                    }
                    camera.unbindAll()
                    camera.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, previewUse, analysis)
                    Log.d(TAG, "camera bound preview+analysis")
                } catch (e: Exception) {
                    Log.d(TAG, "camera bind failed ${e.javaClass.simpleName}")
                    note = "This device has no camera. Paste the link instead."
                }
            }
            future.addListener(listener, ContextCompat.getMainExecutor(context))
            onDispose {
                exec.shutdown()
                runCatching { future.get().unbindAll() }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Back",
            color = term.ink(),
            fontFamily = UiFont,
            fontSize = 15.sp,
            modifier = Modifier.clickable(role = Role.Button, onClick = onBack).padding(vertical = 12.dp),
        )
        Text("Scan", color = term.ink(), fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Text(note, color = term.ink(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        if (!permissionNote.isNullOrBlank()) {
            Text(permissionNote, color = term.warnc(), fontFamily = UiFont, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            PillButton(if (asked && !canAskAgain) "Open settings" else "Allow camera") {
                if (asked && !canAskAgain) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    context.startActivity(intent)
                } else {
                    askedLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(term.bubble())
                .semantics { contentDescription = "Pairing camera" },
        ) {
            if (injectImage.isNullOrBlank() && granted) {
                AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(10.dp))
        TermField(pasted, { pasted = it }, "Paste the pairing link", KeyboardType.Text)
        Spacer(Modifier.height(8.dp))
        PillButton("Use this code", primary = pasted.isNotBlank()) {
            val raw = pasted.trim()
            if (raw.isNotEmpty()) {
                pasted = ""
                onCode(raw)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun decodeFile(path: String): QrDecode.Hit? {
    val raw = BitmapFactory.decodeFile(path) ?: return null
    return decodeBitmap(raw).also { hit ->
        Log.d(TAG, if (hit == null) "injected image no qr" else "injected image decoded len=${hit.text.length} inverted=${hit.inverted}")
    }
}

private fun decodeProxy(image: ImageProxy): QrDecode.Hit? = decodeBitmap(image.toBitmap())

private fun decodeBitmap(bitmap: Bitmap): QrDecode.Hit? {
    val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    val longest = maxOf(argb.width, argb.height)
    val software = if (longest <= 1080) argb else Bitmap.createScaledBitmap(argb, argb.width * 1080 / longest, argb.height * 1080 / longest, true)
    val pixels = IntArray(software.width * software.height)
    software.getPixels(pixels, 0, software.width, 0, 0, software.width, software.height)
    val width = software.width
    val height = software.height
    if (software !== argb) software.recycle()
    if (argb !== bitmap) argb.recycle()
    return QrDecode.decode(pixels, width, height)
}

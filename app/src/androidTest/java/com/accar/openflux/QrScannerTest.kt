package com.accar.openflux

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class QrScannerTest {
    @Test fun inAppCameraStartsAndStops() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, QrScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun findScanner(view: View): DecoratedBarcodeView? {
            if (view is DecoratedBarcodeView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findScanner(view.getChildAt(i))?.let { return it }
            return null
        }
        try {
            var ready = false
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (!ready && android.os.SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { ready = findScanner(activity.window.decorView)?.barcodeView?.isPreviewActive == true }
                Thread.sleep(100)
            }
            assertTrue("The in-app camera preview did not start", ready)
        } finally { instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync() }
    }
    @Test fun localDecoderReadsProfileQr() {
        val content = "paperflux://config?v=1&id=1&name=Test&server=example.invalid&ip=10.10.10.2&doc=https%3A%2F%2Fdisk.yandex.ru%2Fi%2Fexample&token=" + "x".repeat(32)
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 640, 640)
        val pixels = IntArray(640 * 640) { i -> if (matrix[i % 640, i / 640]) -0x1000000 else -1 }
        val result = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(640, 640, pixels))))
        assertEquals(content, result.text)
        assertTrue(QrProfilePayload.accepts(result.text))
    }
    @Test fun arbitraryLinksAndOversizedPayloadsAreRejected() {
        assertFalse(QrProfilePayload.accepts("https://example.invalid"))
        assertFalse(QrProfilePayload.accepts("javascript:alert(1)"))
        assertFalse(QrProfilePayload.accepts("paperflux://config?" + "x".repeat(16_384)))
        assertTrue(QrProfilePayload.accepts("""{"id":1,"token":"test","doc":"example"}"""))
    }
}

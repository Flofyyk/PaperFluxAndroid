package com.paperflux.speedprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.widget.TextView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Separate UID from PaperFlux so the benchmark traverses Android's VPN. Not shipped in the client APK. */
public final class ProbeActivity extends Activity {
    private static final String TAG = "PaperFluxSpeedProbe";
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        status = new TextView(this);
        setContentView(status);
        startProbe(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        startProbe(intent);
    }

    private void startProbe(Intent intent) {
        if (intent.getBooleanExtra("routeOnly", false)) {
            new Thread(() -> inspectRoute(intent.getStringExtra("expectedExit")), "route-probe").start();
            return;
        }
        status.setText("Measuring network throughput…");
        int targetBytes = Math.max(65536, Math.min(16 * 1024 * 1024,
                intent.getIntExtra("bytes", 4 * 1024 * 1024)));
        int streams = Math.max(1, Math.min(4, intent.getIntExtra("streams", 1)));
        for (int stream = 1; stream <= streams; stream++) {
            int streamId = stream;
            new Thread(() -> measure(targetBytes, streamId), "speed-probe-" + streamId).start();
        }
    }

    private void inspectRoute(String expectedExit) {
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        String route = "vpn=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setUseCaches(false);
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = new byte[128];
                int length = input.read(bytes);
                String address = length > 0 ? new String(bytes, 0, length, java.nio.charset.StandardCharsets.UTF_8).trim() : "";
                route += " exitMatches=" + address.equals(expectedExit) + " result=OK";
            }
        } catch (Exception error) { route += " result=" + error.getClass().getSimpleName(); }
        finally { if (connection != null) connection.disconnect(); }
        String report = route;
        Log.i(TAG, "route " + report);
        runOnUiThread(() -> status.setText(report));
    }

    private void measure(int targetBytes, int stream) {
        long started = SystemClock.elapsedRealtime();
        long bytes = 0;
        long lastProgress = started;
        String result;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    "https://speed.cloudflare.com/__down?bytes=" + targetBytes).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.connect();
            int http = connection.getResponseCode();
            if (http != 200) throw new IllegalStateException("HTTP " + http);
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    bytes += read;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastProgress >= 5000) {
                        Log.i(TAG, "stream=" + stream + " progress bytes=" + bytes + " elapsedMs=" + (now - started));
                        lastProgress = now;
                    }
                    if (now - started > 90000) {
                        throw new IllegalStateException("probe deadline exceeded");
                    }
                }
            }
            result = bytes == targetBytes ? "OK" : "SHORT";
        } catch (Exception error) {
            result = error.getClass().getSimpleName() + ": " + error.getMessage();
        } finally {
            if (connection != null) connection.disconnect();
        }
        long elapsed = SystemClock.elapsedRealtime() - started;
        String report = "stream=" + stream + " result=" + result + " bytes=" + bytes + " requested=" + targetBytes
                + " elapsedMs=" + elapsed;
        Log.i(TAG, report);
        runOnUiThread(() -> status.setText(report));
    }
}

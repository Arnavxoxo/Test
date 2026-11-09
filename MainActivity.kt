package com.arnav.appversionchecker

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.zip.ZipFile

private data class InstalledApp(val label: String, val packageName: String, val version: String, val system: Boolean)
private data class ApkReport(val packageName: String, val version: String, val minSdk: Int, val nativeAbis: Set<String>, val compatible: Boolean, val reason: String)

class MainActivity : ComponentActivity() {
    private var reportCallback: ((ApkReport?) -> Unit)? = null
    private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        reportCallback?.invoke(uri?.let { inspectApk(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF9CCBFF), secondary = Color(0xFFB7C9E2),
                background = Color(0xFF101418), surface = Color(0xFF191F25),
                surfaceVariant = Color(0xFF27313A)
            )) {
                AppScreen(
                    scanApps = { loadApps() },
                    openSearch = { app ->
                        val q = Uri.encode("${app.label} ${app.packageName} site: apkmirror.com")
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")))
                    },
                    pickApk = { callback -> reportCallback = callback; apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun loadApps(): List<InstalledApp> {
        val pm = packageManager
        val packages = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                       else pm.getInstalledPackages(PackageManager.GET_META_DATA)
        return packages.mapNotNull { p ->
            val ai = p.applicationInfo ?: return@mapNotNull null
            val label = pm.getApplicationLabel(ai).toString()
            val version = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode.toString() else p.versionCode.toString()
            InstalledApp(label, p.packageName, p.versionName?.let { "$it ($version)" } ?: version,
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
        }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun inspectApk(uri: Uri): ApkReport? {
        return try {
            val temp = cacheDir.resolve("candidate.apk")
            contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return null
            @Suppress("DEPRECATION")
            val pi = packageManager.getPackageArchiveInfo(temp.absolutePath, 0) ?: return null
            val ai = pi.applicationInfo ?: return null
            val minSdk = ai.minSdkVersion
            val abis = linkedSetOf<String>()
            ZipFile(temp).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("lib/")) {
                        val parts = name.split('/')
                        if (parts.size >= 3) abis.add(parts[1])
                    }
                }
            }
            val supported = Build.SUPPORTED_ABIS.toSet()
            val abiOk = abis.isEmpty() || abis.any { it in supported }
            val sdkOk = minSdk <= 31
            val ok = sdkOk && abiOk
            val reason = when {
                !sdkOk -> "Requires Android API $minSdk; this phone is API 31"
                !abiOk -> "Native CPU libraries do not match this phone. APK: ${abis.joinToString()}; phone: ${supported.joinToString()}"
                abis.isEmpty() -> "Android 12 API check passed. No native libraries detected; One UI-specific behavior is not guaranteed."
                else -> "Android 12 and CPU architecture checks passed. One UI-specific behavior is not guaranteed."
            }
            ApkReport(pi.packageName ?: "Unknown package", pi.versionName ?: pi.longVersionCode.toString(), minSdk, abis, ok, reason)
        } catch (_: Exception) { null }
    }
}

@Composable
private fun AppScreen(scanApps: () -> List<InstalledApp>, openSearch: (InstalledApp) -> Unit, pickApk: ((ApkReport?) -> Unit) -> Unit) {
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var query by remember { mutableStateOf("") }
    var includeSystem by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ApkReport?>(null) }
    var scanDone by remember { mutableStateOf(false) }
    val filtered = apps.filter { (includeSystem || !it.system) && (it.label.contains(query, true) || it.packageName.contains(query, true)) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("App Version Checker", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Galaxy S10 · Android 12 / One UI 4.1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("DEVICE COMPATIBILITY PROFILE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("Android API 31  ·  ${Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown ABI"}", fontWeight = FontWeight.SemiBold)
                    Text("Minimum SDK must be ≤ 31. Native libraries must support one of this device’s ABIs.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { apps = scanApps(); scanDone = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Scan installed apps")
                }
                OutlinedButton(onClick = { pickApk { report = it } }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(6.dp)); Text("Check downloaded APK")
                }
            }
            report?.let { r ->
                Card(Modifier.fillMaxWidth().padding(top = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (r.compatible) "Compatibility checks passed" else "Not compatible", fontWeight = FontWeight.Bold,
                            color = if (r.compatible) Color(0xFF8BE0AE) else Color(0xFFFF9B9B))
                        Text("${r.packageName} · ${r.version}", fontSize = 12.sp)
                        Text("Minimum SDK: ${r.minSdk} · Native ABIs: ${r.nativeAbis.ifEmpty { setOf("No native libraries") }.joinToString()}", fontSize = 11.sp)
                        Text(r.reason, fontSize = 12.sp)
                    }
                }
            }
            if (scanDone) {
                Spacer(Modifier.height(12.dp))
                Text("${filtered.size} apps shown · ${apps.size} detected", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search installed apps") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeSystem, onCheckedChange = { includeSystem = it })
                    Text("Include system apps", fontSize = 13.sp)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(app.version, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { openSearch(app) }) { Text("APKMirror") }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(22.dp))
                Text("How it works", fontWeight = FontWeight.Bold)
                Text("Scan your installed apps, open an APKMirror search for each app, then select a downloaded APK here to inspect its Android minimum version and CPU libraries before installing.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text("Important limitation", fontWeight = FontWeight.Bold)
                Text("APKMirror does not provide a public API for reliably fetching every app’s newest release and variant. This first build does not claim an update is available without checking the listing. One UI 4.1 vendor-specific behavior also cannot be proven from the APK manifest alone.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

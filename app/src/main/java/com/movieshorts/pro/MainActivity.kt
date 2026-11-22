package com.movieshorts.pro

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportException
import java.io.File

private val Navy = Color(0xFF080D1D)
private val Panel = Color(0xFF121D35)
private val Muted = Color(0xFFAAB8D3)
private val Cyan = Color(0xFF39C6F4)
private val Purple = Color(0xFF852BFF)
private val Blue = Color(0xFF176BEE)

data class ClipRecord(val projectName: String, val name: String, val file: File, val durationSeconds: Int)

class MainActivity : ComponentActivity() {
    private var pendingProjectName = ""
    private var currentSource: Uri? = null
    private var currentDurationMs = 0L
    private var nextClipIndex = 0
    private var projectOutputDir: File? = null
    private var activeTransformer: Transformer? = null
    private val clips = mutableStateListOf<ClipRecord>()
    private val projects = mutableStateListOf<String>()
    private var filePendingSave: File? = null

    private val saveClipPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destination ->
        val source = filePendingSave
        if (destination != null && source != null) {
            runCatching {
                contentResolver.openOutputStream(destination)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Could not open destination")
                status = "Saved ${source.name} to your selected phone folder."
            }.onFailure { status = "Save failed: ${it.message ?: "unknown error"}" }
        }
        filePendingSave = null
    }
    private var status by mutableStateOf("Choose a movie to create your first project.")

    private val moviePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            currentSource = uri
            beginProjectProcessing(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreLocalProjects()
        setContent { MovieShortsApp(
            status = status,
            clips = clips,
            projects = projects,
            onUpload = { showProjectNameDialog() },
            onOpenClip = { file -> shareOrOpen(file) },
            onSaveClip = { file -> filePendingSave = file; saveClipPicker.launch(file.name) }
        ) }
    }

    private fun showProjectNameDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "e.g. My Movie Project"
            setSingleLine(true)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Name your project")
            .setMessage("Every movie is saved in its own project.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Choose movie") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    status = "Please enter a project name."
                } else {
                    pendingProjectName = name
                    moviePicker.launch(arrayOf("video/*"))
                }
            }.show()
    }

    private fun restoreLocalProjects() {
        val root = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return
        root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
            if (!projects.contains(directory.name)) projects.add(directory.name)
            directory.listFiles()?.filter { it.isFile && it.extension.equals("mp4", true) }
                ?.sortedBy { it.name }?.forEach { file ->
                    if (clips.none { it.file.absolutePath == file.absolutePath }) {
                        val retriever = MediaMetadataRetriever()
                        val seconds = runCatching {
                            retriever.setDataSource(file.absolutePath)
                            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000L
                        }.getOrDefault(0L).toInt()
                        runCatching { retriever.release() }
                        clips.add(ClipRecord(directory.name, file.name, file, seconds))
                    }
                }
        }
    }

    private fun beginProjectProcessing(uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            currentDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            if (currentDurationMs <= 0L) {
                status = "Could not read this video's duration. Try another video."
                return
            }
            projectOutputDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), safeName(pendingProjectName))
                .apply { mkdirs() }
            clips.clear()
            projects.add(pendingProjectName)
            nextClipIndex = 0
            status = "Preparing ${pendingProjectName}…"
            exportNextClip()
        } catch (e: Exception) {
            status = "Could not open the selected movie: ${e.message ?: "unknown error"}"
        }
    }

    private fun exportNextClip() {
        val startMs = nextClipIndex * 90_000L
        if (startMs >= currentDurationMs) {
            status = "Finished: ${clips.size} clips saved in Movies/MovieShortsPro."
            activeTransformer = null
            return
        }
        val endMs = minOf(startMs + 90_000L, currentDurationMs)
        val out = File(projectOutputDir, "clip_${(nextClipIndex + 1).toString().padStart(4, '0')}.mp4")
        val source = currentSource ?: return
        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            ).build()
        status = "Creating clip ${nextClipIndex + 1} (${startMs / 1000}s–${endMs / 1000}s)…"
        activeTransformer = Transformer.Builder(this)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    clips.add(ClipRecord(pendingProjectName, out.name, out, ((endMs - startMs) / 1000L).toInt()))
                    nextClipIndex++
                    exportNextClip()
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    status = "Clip ${nextClipIndex + 1} failed: ${exportException.message ?: "export error"}"
                    activeTransformer = null
                }
            }).build().also { it.start(mediaItem, out.absolutePath) }
    }

    private fun shareOrOpen(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure {
            val share = Intent(Intent.ACTION_SEND).setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Open or share clip"))
        }
    }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "Project" }
}

@Composable
fun MovieShortsApp(
    status: String,
    clips: List<ClipRecord>,
    projects: List<String>,
    onUpload: () -> Unit,
    onOpenClip: (File) -> Unit,
    onSaveClip: (File) -> Unit
) {
    var selected by remember { mutableStateOf("Home") }
    MaterialTheme(colorScheme = darkColorScheme(background = Navy, surface = Panel, primary = Purple, secondary = Cyan)) {
        Scaffold(
            containerColor = Navy,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0D1428)) {
                    listOf("Home", "Upload", "Projects", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { selected = item; if (item == "Upload") onUpload() },
                            icon = {
                                Icon(when (item) {
                                    "Home" -> Icons.Default.Home
                                    "Upload" -> Icons.Default.Add
                                    "Projects" -> Icons.Default.Movie
                                    else -> Icons.Default.Settings
                                }, contentDescription = item)
                            }, label = { Text(item) }
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_mark),
                            contentDescription = "MovieShorts Pro logo",
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("MovieShorts", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text(" Pro", color = Cyan, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF15264A), Color(0xFF32165D))), RoundedCornerShape(24.dp)).padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("YOUR PERSONAL MOVIE STUDIO", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Turn full movies into\n90-second shorts.", color = Color.White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Select a movie from your phone, name the project and create sequential clips stored on this device.", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                        Button(onClick = onUpload, colors = ButtonDefaults.buttonColors(containerColor = Purple), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)) {
                            Text("＋  Upload your movie", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("Projects", projects.distinct().size.toString(), Modifier.weight(1f))
                        StatCard("Short clips", clips.size.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(18.dp)) {
                        Text("PROCESSING STATUS", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(status, color = Color.White)
                        if (status.startsWith("Creating")) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Cyan)
                        }
                    }
                }
                item {
                    Text("GENERATED CLIPS", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (clips.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(20.dp)) {
                            Text("Your clips will appear here", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Each clip is up to 1 minute 30 seconds. The final clip may be shorter.", color = Muted)
                        }
                    }
                } else {
                    items(clips) { clip ->
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(clip.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${clip.projectName} · ${clip.durationSeconds}s", color = Muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Button(onClick = { onOpenClip(clip.file) }) { Text("Preview / Share") }
                                TextButton(onClick = { onSaveClip(clip.file) }) { Text("Save to phone") }
                            }
                        }
                    }
                }
                item { Text("Selected tab: $selected", color = Muted, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Muted, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
    }
}

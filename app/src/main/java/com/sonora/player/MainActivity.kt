package com.sonora.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class Track(val id: String, val title: String, val artist: String, val artwork: String?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = ExoPlayer.Builder(this).build()
        setContent { SonoraApp(player) }
    }
    override fun onDestroy() { super.onDestroy() }
}

@Composable
fun SonoraApp(player: ExoPlayer) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sonora", 0) }
    var query by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var nowPlaying by remember { mutableStateOf("Choose a track to start listening") }

    val bg = Color(0xFF100D19)
    val panel = Color(0xFF211A30)
    val pink = Color(0xFFFF72C6)
    val lilac = Color(0xFFB99AFF)

    MaterialTheme(colorScheme = darkColorScheme(
        background = bg, surface = panel, primary = pink, secondary = lilac,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF281638), bg, Color(0xFF100D19))))
                .statusBarsPadding().padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text("SONORA", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("your music, gathered in one place", color = lilac, fontSize = 13.sp)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search songs or artists") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(18.dp),
                trailingIcon = {
                    TextButton(enabled = query.isNotBlank() && !loading, onClick = {
                        loading = true; error = null
                        kotlinx.coroutines.MainScope().launch {
                            try { tracks = Audius.search(query); if (tracks.isEmpty()) error = "No results. Try another search." }
                            catch (e: Exception) { error = "Couldn't reach Audius. Check your connection and try again." }
                            finally { loading = false }
                        }
                    }) { Text("SEARCH") }
                }
            )
            Spacer(Modifier.height(18.dp))
            Text("DISCOVER", color = lilac, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = pink, modifier = Modifier.padding(vertical = 8.dp)) }
            if (tracks.isEmpty() && !loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("♫", fontSize = 58.sp, color = pink)
                        Text("Find something worth replaying.", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Search the Audius music catalog to get started.", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp))
                                .clickable {
                                    if (playingId == track.id && player.isPlaying) player.pause()
                                    else {
                                        val url = Audius.streamUrl(track.id)
                                        player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); player.play()
                                        playingId = track.id; nowPlaying = "${track.title} — ${track.artist}"
                                    }
                                }.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(44.dp).background(Color(0xFF493052), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(if (playingId == track.id && player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = pink)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(track.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                            }
                            IconButton(onClick = {
                                val updated = favorites.toMutableSet()
                                if (!updated.add(track.id)) updated.remove(track.id)
                                favorites = updated
                                prefs.edit().putStringSet("favorites", updated).apply()
                            }) {
                                Icon(if (track.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    null, tint = pink)
                            }
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(Color(0xFF251B32), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).padding(14.dp)) {
                Text("NOW PLAYING", color = lilac, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(nowPlaying, color = Color.White, maxLines = 1, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                        Text(if (player.isPlaying) "PAUSE" else "RESUME")
                    }
                }
            }
        }
    }
}

object Audius {
    private val client = OkHttpClient()
    private const val BASE = "https://api.audius.co/v1"
    private const val APP = "SONORA"

    suspend fun search(term: String): List<Track> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(term, "UTF-8")
        val request = Request.Builder().url("$BASE/tracks/search?query=$q&app_name=$APP&limit=25").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Audius request failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val data = root.optJSONArray("data") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val user = item.optJSONObject("user")
                    add(Track(
                        id = item.optString("id"),
                        title = item.optString("title", "Untitled"),
                        artist = user?.optString("name") ?: "Unknown artist",
                        artwork = item.optJSONObject("artwork")?.optString("150x150")
                    ))
                }
            }
        }
    }

    fun streamUrl(trackId: String): String = "$BASE/tracks/$trackId/stream?app_name=$APP"
}

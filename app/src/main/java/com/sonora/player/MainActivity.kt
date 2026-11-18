package com.sonora.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class Track(val id: String, val title: String, val artist: String, val artwork: String?, val source: String = "Audius")

private val Ink = Color(0xFF0B0A10)
private val SurfaceOne = Color(0xFF17141F)
private val SurfaceTwo = Color(0xFF211B2B)
private val Pink = Color(0xFFFF79C8)
private val Lavender = Color(0xFFBBA4FF)
private val Muted = Color(0xFFA9A3B3)

class MainActivity : ComponentActivity() {
    private var sonoraPlayer: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = ExoPlayer.Builder(this).build()
        sonoraPlayer = player
        setContent { SonoraApp(player) }
    }
    override fun onDestroy() {
        sonoraPlayer?.release()
        sonoraPlayer = null
        super.onDestroy()
    }
}

@Composable
fun SonoraApp(player: ExoPlayer) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sonora", 0) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf("All sources") }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var knownTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var current by remember { mutableStateOf<Track?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf(prefs.getString("display_name", "") ?: "") }

    fun toggleFavorite(track: Track) {
        val updated = favorites.toMutableSet()
        if (!updated.add(track.id)) updated.remove(track.id)
        favorites = updated
        prefs.edit().putStringSet("favorites", updated).apply()
        if (knownTracks.none { it.id == track.id }) knownTracks = knownTracks + track
    }

    fun play(track: Track) {
        current = track
        knownTracks = (listOf(track) + knownTracks.filterNot { it.id == track.id }).take(30)
        scope.launch {
            try {
                val stream = if (track.source == "Internet Archive") ArchiveCatalog.streamUrl(track.id) else Audius.streamUrl(track.id)
                player.setMediaItem(MediaItem.fromUri(stream))
                player.prepare()
                player.play()
            } catch (_: Exception) {
                error = "This track could not be streamed. Try another result."
            }
        }
    }

    fun search() {
        if (query.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                tracks = when (selectedSource) {
                    "Audius" -> Audius.search(query)
                    "Internet Archive" -> ArchiveCatalog.search(query)
                    else -> (Audius.search(query) + ArchiveCatalog.search(query)).distinctBy { it.source + it.id }
                }
                knownTracks = (tracks + knownTracks).distinctBy { it.id }.take(40)
                if (tracks.isEmpty()) error = "No tracks found. Try another artist or title."
            } catch (_: Exception) {
                error = "Couldn't reach the music catalog. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = SurfaceOne, primary = Pink, secondary = Lavender,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF24152D), Ink, Ink))
            )
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("SONORA", fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("MUSIC WITHOUT THE NOISE", fontSize = 9.sp, color = Lavender, letterSpacing = 2.sp)
                    }
                    IconButton(onClick = { showAccount = true }) {
                        Icon(Icons.Default.AccountCircle, "Account", tint = Lavender, modifier = Modifier.size(29.dp))
                    }
                }

                when {
                    showAccount -> AccountScreen(
                        displayName = displayName,
                        onNameChange = { displayName = it; prefs.edit().putString("display_name", it).apply() },
                        onBack = { showAccount = false }
                    )
                    showPlayer && current != null -> FullPlayer(
                        track = current!!,
                        isPlaying = player.isPlaying,
                        onBack = { showPlayer = false },
                        onToggle = { if (player.isPlaying) player.pause() else player.play() },
                        onFavorite = { toggleFavorite(current!!) },
                        isFavorite = current!!.id in favorites
                    )
                    else -> {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (tab) {
                                "Home" -> HomeScreen(
                                    displayName = displayName,
                                    recent = knownTracks,
                                    onSearch = { tab = "Search" },
                                    onPlay = { play(it); showPlayer = true },
                                    onOpenLibrary = { tab = "Library" }
                                )
                                "Search" -> SearchScreen(
                                    query = query, onQuery = { query = it }, onSearch = { search() },
                                    source = selectedSource, onSource = { selectedSource = it; tracks = emptyList(); error = null },
                                    tracks = tracks, loading = loading, error = error,
                                    favorites = favorites, onPlay = { play(it); showPlayer = true },
                                    onFavorite = { toggleFavorite(it) }
                                )
                                "Library" -> LibraryScreen(
                                    tracks = knownTracks.filter { it.id in favorites },
                                    onPlay = { play(it); showPlayer = true },
                                    onFavorite = { toggleFavorite(it) }
                                )
                                else -> SettingsScreen(
                                    onAccount = { showAccount = true },
                                    onClear = {
                                        favorites = emptySet()
                                        prefs.edit().remove("favorites").apply()
                                    }
                                )
                            }
                        }

                        if (current != null) MiniPlayer(
                            track = current!!,
                            isPlaying = player.isPlaying,
                            onOpen = { showPlayer = true },
                            onToggle = { if (player.isPlaying) player.pause() else player.play() }
                        )
                        NavigationBar(
                            containerColor = Color(0xFF100E16),
                            contentColor = Color.White
                        ) {
                            listOf(
                                Triple("Home", Icons.Default.Home, "Home"),
                                Triple("Search", Icons.Default.Search, "Search"),
                                Triple("Library", Icons.Default.LibraryMusic, "Library"),
                                Triple("Settings", Icons.Default.Settings, "Settings")
                            ).forEach { (label, icon, destination) ->
                                NavigationBarItem(
                                    selected = tab == destination,
                                    onClick = { tab = destination; showAccount = false },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Pink,
                                        selectedTextColor = Pink,
                                        indicatorColor = Color(0xFF35243D),
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    displayName: String,
    recent: List<Track>,
    onSearch: () -> Unit,
    onPlay: (Track) -> Unit,
    onOpenLibrary: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Text(if (displayName.isBlank()) "Good music. No clutter." else "Welcome back, $displayName",
                fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Your listening space, all in one place.", color = Muted, fontSize = 13.sp)
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF633B72), Color(0xFF302044))))
                    .clickable { onSearch() }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("FIND YOUR NEXT REPEAT", color = Lavender, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("What are you\\nin the mood for?", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("SEARCH MUSIC  →", color = Pink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.GraphicEq, null, tint = Pink, modifier = Modifier.size(66.dp))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR LIBRARY", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenLibrary) { Text("VIEW ALL", color = Pink) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeTile("Liked songs", "Your saved tracks", Icons.Default.Favorite, Modifier.weight(1f), onOpenLibrary)
                HomeTile("Discover", "Find something new", Icons.Default.Explore, Modifier.weight(1f), onSearch)
            }
        }
        item {
            Text("RECENTLY DISCOVERED", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        if (recent.isEmpty()) {
            item {
                EmptyState("Your next favorite starts here", "Search the catalog and your recent discoveries will show up here.", Icons.Default.MusicNote)
            }
        } else {
            items(recent.take(8), key = { it.id }) { track ->
                TrackRow(track = track, isFavorite = false, onPlay = { onPlay(track) }, onFavorite = {})
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun HomeTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(SurfaceTwo)
            .clickable { onClick() }.padding(15.dp)
    ) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SearchScreen(
    query: String, onQuery: (String) -> Unit, onSearch: () -> Unit,
    source: String, onSource: (String) -> Unit,
    tracks: List<Track>, loading: Boolean, error: String?,
    favorites: Set<String>, onPlay: (Track) -> Unit, onFavorite: (Track) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("Search", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Songs, artists, sounds...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                IconButton(onClick = onSearch, enabled = query.isNotBlank() && !loading) {
                    Icon(Icons.Default.ArrowForward, "Search", tint = Pink)
                }
            },
            singleLine = true, shape = RoundedCornerShape(17.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Pink, unfocusedBorderColor = Color(0xFF51465C),
                focusedContainerColor = SurfaceOne, unfocusedContainerColor = SurfaceOne
            )
        )
        Spacer(Modifier.height(10.dp))
        Text("MUSIC SOURCES", color = Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All sources", "Audius", "Internet Archive").forEach { item ->
                FilterChip(selected = source == item, onClick = { onSource(item) }, label = { Text(item, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF49304F), selectedLabelColor = Pink))
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pink)
            error != null -> Text(error, color = Pink, modifier = Modifier.padding(8.dp))
            tracks.isEmpty() -> EmptyState("Search the catalog", "Look up a song, artist, or track name to start listening.", Icons.Default.Headphones)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(track, track.id in favorites, { onPlay(track) }, { onFavorite(track) })
            }
        }
    }
}

@Composable
private fun LibraryScreen(tracks: List<Track>, onPlay: (Track) -> Unit, onFavorite: (Track) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Your Library", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text("The tracks you've saved in SONORA.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp, bottom = 18.dp))
        if (tracks.isEmpty()) {
            EmptyState("Nothing saved yet", "Tap the heart beside a track to add it to your liked songs.", Icons.Default.FavoriteBorder)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(track, true, { onPlay(track) }, { onFavorite(track) })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onAccount: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Settings", fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
        SettingsRow(Icons.Default.Person, "Account", "Local profile and account options", onAccount)
        SettingsRow(Icons.Default.Equalizer, "Playback", "Music streams from the Audius catalog", {})
        SettingsRow(Icons.Default.Security, "Privacy", "Favorites are stored on this device", {})
        SettingsRow(Icons.Default.DeleteOutline, "Clear liked songs", "Remove all locally saved favorites", onClear)
        Spacer(Modifier.height(20.dp))
        Text("ABOUT SONORA", color = Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("SONORA is an independent music player using the Audius catalog. Spotify sign-in and liked-song import are not connected in this build.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("Version 0.2 • Preview", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun AccountScreen(displayName: String, onNameChange: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Profile", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(82.dp).clip(CircleShape).background(SurfaceTwo), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, tint = Pink, modifier = Modifier.size(45.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Your SONORA profile", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Choose a name for this device. No account or password is required for this preview.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = displayName, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") }, singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = Lavender)
            Spacer(Modifier.width(9.dp))
            Text("Spotify account connection is not implemented yet. This profile is stored only on your phone.", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Lavender, modifier = Modifier.size(23.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
    HorizontalDivider(color = Color(0xFF302A38))
}

@Composable
private fun TrackRow(track: Track, isFavorite: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(SurfaceOne)
            .clickable { onPlay() }.padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(45.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF382942)), contentAlignment = Alignment.Center) {
            if (!track.artwork.isNullOrBlank()) AsyncImage(model = track.artwork, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)))
            else Icon(Icons.Default.MusicNote, null, tint = Pink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onFavorite) {
            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (isFavorite) Pink else Muted)
        }
        Icon(Icons.Default.PlayCircle, "Play", tint = Pink, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun MiniPlayer(track: Track, isPlaying: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF33243D))
            .clickable { onOpen() }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!track.artwork.isNullOrBlank()) AsyncImage(model = track.artwork, contentDescription = "Album artwork", modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)))
        else Icon(Icons.Default.Album, null, tint = Pink, modifier = Modifier.size(31.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(track.artist, fontSize = 10.sp, color = Muted, maxLines = 1)
        }
        IconButton(onClick = onToggle) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Pink)
        }
    }
}

@Composable
private fun FullPlayer(track: Track, isPlaying: Boolean, onBack: () -> Unit, onToggle: () -> Unit, onFavorite: () -> Unit, isFavorite: Boolean) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Close player") }
            Text("NOW PLAYING", color = Lavender, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onFavorite) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = Pink) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF71477D), Color(0xFF30203D), Color(0xFF17131F)))),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artwork.isNullOrBlank()) AsyncImage(model = track.artwork, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)))
            else Icon(Icons.Default.GraphicEq, null, tint = Pink, modifier = Modifier.size(120.dp))
        }
        Spacer(Modifier.height(34.dp))
        Text(track.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(track.artist, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(25.dp))
        LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth(), color = Pink, trackColor = SurfaceTwo)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = {}) { Icon(Icons.Default.SkipPrevious, "Previous", tint = Muted, modifier = Modifier.size(35.dp)) }
            FilledIconButton(onClick = onToggle, modifier = Modifier.size(68.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Pink, contentColor = Ink)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = {}) { Icon(Icons.Default.SkipNext, "Next", tint = Muted, modifier = Modifier.size(35.dp)) }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Pink, modifier = Modifier.size(43.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

object Audius {
    private val client = OkHttpClient()
    private const val BASE = "https://api.audius.co/v1"
    private const val APP = "SONORA"

    suspend fun search(term: String): List<Track> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(term, "UTF-8")
        val request = Request.Builder().url("$BASE/tracks/search?query=$q&app_name=$APP&limit=30").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Audius request failed: ${response.code}")
            val root = JSONObject(response.body?.string() ?: "{}")
            val data = root.optJSONArray("data") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val user = item.optJSONObject("user")
                    val id = item.optString("id")
                    if (id.isNotBlank() && id != "null") add(Track(
                        id = id,
                        title = item.optString("title", "Untitled"),
                        artist = user?.optString("name") ?: "Unknown artist",
                        artwork = item.optJSONObject("artwork")?.optString("480x480") ?: item.optJSONObject("artwork")?.optString("150x150"),
                        source = "Audius"
                    ))
                }
            }
        }
    }

    fun streamUrl(trackId: String): String = "$BASE/tracks/$trackId/stream?app_name=$APP"
}


object ArchiveCatalog {
    private val client = OkHttpClient()

    suspend fun search(term: String): List<Track> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode("$term AND mediatype:audio", "UTF-8")
        val url = "https://archive.org/advancedsearch.php?q=$q&fl[]=identifier,title,creator&rows=20&page=1&output=json"
        val request = Request.Builder().url(url).header("User-Agent", "SONORA/0.3 Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Internet Archive search failed: ${response.code}")
            val docs = JSONObject(response.body?.string() ?: "{}")
                .optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until docs.length()) {
                    val item = docs.optJSONObject(i) ?: continue
                    val id = item.optString("identifier")
                    if (id.isNotBlank() && id != "null") add(
                        Track(
                            id = id,
                            title = item.optString("title", "Untitled recording"),
                            artist = item.optString("creator", "Internet Archive"),
                            artwork = "https://archive.org/services/img/$id",
                            source = "Internet Archive"
                        )
                    )
                }
            }
        }
    }

    suspend fun streamUrl(identifier: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://archive.org/metadata/${URLEncoder.encode(identifier, "UTF-8")}")
            .header("User-Agent", "SONORA/0.3 Android").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Could not load recording metadata")
            val files = JSONObject(response.body?.string() ?: "{}").optJSONArray("files")
                ?: error("No playable files found")
            val preferred = listOf("mp3", "ogg", "flac", "m4a", "wav")
            var selected: String? = null
            for (ext in preferred) {
                for (i in 0 until files.length()) {
                    val name = files.optJSONObject(i)?.optString("name") ?: continue
                    if (name.substringAfterLast('.', "").equals(ext, true)) {
                        selected = name
                        break
                    }
                }
                if (selected != null) break
            }
            val file = selected ?: error("No supported audio file in this recording")
            val encoded = file.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
            "https://archive.org/download/${URLEncoder.encode(identifier, "UTF-8")}/$encoded"
        }
    }
}

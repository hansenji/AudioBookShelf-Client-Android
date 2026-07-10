package dev.vikingsen.skald.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    bookId: String,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSeriesClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    onCollectionClick: (String) -> Unit = {},
    viewModel: DetailViewModel = koinViewModel()
) {
    LaunchedEffect(bookId) {
        viewModel.setBookId(bookId)
    }

    val bookDetail by viewModel.bookDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadingFileName by viewModel.downloadingFileName.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val showMiniPlayer by viewModel.showMiniPlayer.collectAsState()

    var showMenuBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenuBottomSheet = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && bookDetail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (bookDetail != null) {
            val book = bookDetail!!
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                DetailContent(
                    book = book,
                    showMiniPlayer = showMiniPlayer,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    downloadingFileName = downloadingFileName,
                    downloadError = downloadError,
                    onDownloadClick = { viewModel.downloadBook() },
                    onRemoveDownloadClick = { viewModel.deleteDownloadedBook() },
                    onPlayClick = { startPos -> 
                        viewModel.playBook(startPos)
                        onPlayClick()
                    },
                    onSeriesClick = onSeriesClick,
                    onPlaylistClick = onPlaylistClick,
                    onCollectionClick = onCollectionClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showMenuBottomSheet && bookDetail != null) {
        ItemMoreMenuBottomSheet(
            book = bookDetail!!,
            serverUrl = viewModel.serverUrl,
            onDismiss = { showMenuBottomSheet = false },
            onToggleFinished = { viewModel.toggleFinished() },
            onDiscardProgress = { viewModel.discardProgress() },
            onDeleteDownload = { viewModel.deleteDownloadedBook() }
        )
    }
}


@Composable
fun DetailContent(
    book: BookDetailUiModel,
    showMiniPlayer: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadingFileName: String?,
    downloadError: String?,
    onDownloadClick: () -> Unit,
    onRemoveDownloadClick: () -> Unit,
    onPlayClick: (Double) -> Unit,
    onSeriesClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val bottomPadding = if (showMiniPlayer) 80.dp else 16.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding)
    ) {
        // Book Header Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Book Cover
            Box(
                modifier = Modifier
                    .size(120.dp, 160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                         )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = book.title.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }

            // Info Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "By ${book.author}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.narrator.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Narrated by ${book.narrator}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                // Duration Info
                Text(
                    text = "Duration: ${book.durationText}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Playback Progress
                if (book.progress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Progress: ${(book.progress.progress * 100).toInt()}% (${book.progressLeftText ?: ""} left)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Play Button
            Button(
                onClick = { onPlayClick(book.progress?.currentTime ?: 0.0) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (book.progress != null && book.progress.currentTime > 0) "Resume" else "Listen",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Download Button
            if (book.isDownloaded) {
                Button(
                    onClick = onRemoveDownloadClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Live Download Progress Bar
        if (isDownloading) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Downloading: ${downloadingFileName ?: "audio files"}",
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.7f)
                    )
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(0.3f),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        if (downloadError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = downloadError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        // Series Association
        book.series?.let { series ->
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Series",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            val label = if (!series.sequence.isNullOrEmpty()) {
                "${series.name} #${series.sequence}"
            } else {
                series.name
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onSeriesClick(series.id) },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Series",
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
            }
        }

        // Playlists Association
        if (book.playlists.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Playlists",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                book.playlists.forEach { playlist ->
                    AssistChip(
                        onClick = { onPlaylistClick(playlist.id) },
                        label = { Text(playlist.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                contentDescription = "Playlist",
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }

        // Collections Association
        if (book.collections.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Collections",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                book.collections.forEach { collection ->
                    AssistChip(
                        onClick = { onCollectionClick(collection.id) },
                        label = { Text(collection.name) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Collection",
                                modifier = Modifier.size(AssistChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Description/Synopsis
        if (book.description.isNotEmpty()) {
            Text(
                text = "Synopsis",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = book.description.parseHtml(),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Chapters List
        if (book.chapters.isNotEmpty()) {
            var chaptersExpanded by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { chaptersExpanded = !chaptersExpanded }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters (${book.chapters.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Icon(
                    imageVector = if (chaptersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (chaptersExpanded) "Collapse Chapters" else "Expand Chapters",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            
            if (chaptersExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                book.chapters.forEachIndexed { index, chapter ->
                    ChapterItem(
                        chapter = chapter,
                        index = index,
                        onClick = { onPlayClick(chapter.start) }
                    )
                    Divider(color = Color(0x11FFFFFF))
                }
            }
        }
    }
}

@Composable
fun ChapterItem(
    chapter: ChapterUiModel,
    index: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chapter.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Start: ${chapter.startText}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = chapter.durationText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

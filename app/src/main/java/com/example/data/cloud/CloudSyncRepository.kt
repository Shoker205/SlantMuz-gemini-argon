package com.example.data.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.local.PlaylistWithTracks
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.remote.CompositeMusicRepository
import com.example.domain.CloudAccountState
import com.example.domain.SourceProvider
import com.example.domain.Track
import com.example.domain.UserTasteProfile
import com.example.domain.isPreviewSnippetUrl
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Handles Firebase Authentication (Google Sign-In via CredentialManager + Email/Password Auth)
 * and real-time Cloud Firestore synchronization for Musical DNA, Favorite Tracks, Liked Artists,
 * and Custom Playlists.
 */
class CloudSyncRepository(
    private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val musicRepo: CompositeMusicRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val auth: FirebaseAuth? = runCatching { Firebase.auth }.getOrNull()
    private val firestore: FirebaseFirestore? = runCatching { Firebase.firestore }.getOrNull()

    private val _accountState = MutableStateFlow(buildAccountStateFromUser(auth?.currentUser))
    val accountState: StateFlow<CloudAccountState> = _accountState.asStateFlow()

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _accountState.value = buildAccountStateFromUser(
                user = user,
                previous = _accountState.value
            )
        }
    }

    private fun buildAccountStateFromUser(
        user: FirebaseUser?,
        previous: CloudAccountState? = null
    ): CloudAccountState {
        if (user == null) {
            return CloudAccountState(
                isSignedIn = false,
                statusMessage = "Войдите в аккаунт для облачной синхронизации ДНК, медиатеки и плейлистов"
            )
        }
        val display = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "SlantMuz Listener"
        return CloudAccountState(
            isSignedIn = true,
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = display,
            photoUrl = user.photoUrl?.toString().orEmpty(),
            isSyncing = previous?.isSyncing ?: false,
            lastSyncedAtMs = previous?.lastSyncedAtMs ?: 0L,
            syncedTracksCount = previous?.syncedTracksCount ?: 0,
            syncedPlaylistsCount = previous?.syncedPlaylistsCount ?: 0,
            syncedArtistsCount = previous?.syncedArtistsCount ?: 0,
            statusMessage = previous?.statusMessage?.takeIf { it.isNotBlank() }
                ?: "Облачная синхронизация активна"
        )
    }

    /**
     * Authenticates the user with Google Sign-In using Jetpack CredentialManager (`GetSignInWithGoogleOption`).
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<CloudAccountState> =
        withContext(Dispatchers.Main) {
            val firebaseAuth = auth ?: return@withContext Result.failure(
                IllegalStateException("Firebase Auth не инициализирован")
            )
            _accountState.value = _accountState.value.copy(
                isSyncing = true,
                errorMessage = null,
                statusMessage = "Открытие окна входа Google…"
            )
            try {
                val res = activityContext.resources
                val pkg = activityContext.packageName
                val clientIdResId = res.getIdentifier("firebase_web_client_id", "string", pkg)
                    .takeIf { it != 0 }
                    ?: res.getIdentifier("default_web_client_id", "string", pkg)
                val webClientId = if (clientIdResId != 0) {
                    activityContext.getString(clientIdResId)
                } else {
                    ""
                }
                val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()

                val credentialManager = CredentialManager.create(activityContext)
                val result = credentialManager.getCredential(
                    request = request,
                    context = activityContext
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    val authResult = withContext(Dispatchers.IO) {
                        firebaseAuth.signInWithCredential(firebaseCred).await()
                    }
                    val user = authResult.user
                    val updated = buildAccountStateFromUser(user).copy(
                        isSyncing = false,
                        statusMessage = "Вход через Google выполнен • синхронизация…"
                    )
                    _accountState.value = updated
                    Result.success(updated)
                } else {
                    _accountState.value = _accountState.value.copy(
                        isSyncing = false,
                        errorMessage = "Неподдерживаемый тип учётных данных Google"
                    )
                    Result.failure(IllegalStateException("Неподдерживаемый тип учётных данных Google"))
                }
            } catch (cancelEx: GetCredentialCancellationException) {
                _accountState.value = _accountState.value.copy(
                    isSyncing = false,
                    errorMessage = null,
                    statusMessage = "Вход через Google отменён"
                )
                Result.failure(cancelEx)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Не удалось выполнить вход через Google"
                _accountState.value = _accountState.value.copy(
                    isSyncing = false,
                    errorMessage = msg,
                    statusMessage = "Ошибка входа через Google"
                )
                Result.failure(e)
            }
        }

    /**
     * Authenticates or registers a user via Firebase Email & Password Authentication.
     */
    suspend fun signInOrRegisterWithEmail(
        email: String,
        password: String,
        displayName: String = "",
        isRegister: Boolean
    ): Result<CloudAccountState> = withContext(Dispatchers.IO) {
        val firebaseAuth = auth ?: return@withContext Result.failure(
            IllegalStateException("Firebase Auth не инициализирован")
        )
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()

        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            val err = "Введите корректный Email адрес"
            _accountState.value = _accountState.value.copy(errorMessage = err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }
        if (password.length < 6) {
            val err = "Пароль должен содержать минимум 6 символов"
            _accountState.value = _accountState.value.copy(errorMessage = err)
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        _accountState.value = _accountState.value.copy(
            isSyncing = true,
            errorMessage = null,
            statusMessage = if (isRegister) "Создание облачного аккаунта SlantMuz…" else "Вход в аккаунт SlantMuz…"
        )

        try {
            val authResult = if (isRegister) {
                firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).await()
            } else {
                firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).await()
            }

            val user = authResult.user
            if (user != null && cleanName.isNotBlank()) {
                runCatching {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
            }

            val updated = buildAccountStateFromUser(firebaseAuth.currentUser).copy(
                displayName = cleanName.ifBlank {
                    firebaseAuth.currentUser?.displayName
                        ?: cleanEmail.substringBefore("@")
                },
                isSyncing = false,
                errorMessage = null,
                statusMessage = if (isRegister) "Аккаунт создан и синхронизирован" else "Вход выполнен успешно"
            )
            _accountState.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            val friendlyMsg = when {
                e.message?.contains("email address is already in use", ignoreCase = true) == true ->
                    "Этот Email уже зарегистрирован. Переключитесь на вкладку «Вход»."
                e.message?.contains("password is invalid", ignoreCase = true) == true ||
                    e.message?.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) == true ->
                    "Неверный Email или пароль."
                e.message?.contains("no user record", ignoreCase = true) == true ->
                    "Аккаунт не найден. Переключитесь на «Регистрация»."
                else -> e.localizedMessage ?: "Ошибка авторизации Firebase"
            }
            _accountState.value = _accountState.value.copy(
                isSyncing = false,
                errorMessage = friendlyMsg,
                statusMessage = "Ошибка авторизации"
            )
            Result.failure(e)
        }
    }

    fun signOut() {
        runCatching { auth?.signOut() }
        _accountState.value = CloudAccountState(
            isSignedIn = false,
            statusMessage = "Вы вышли из облачного аккаунта"
        )
    }

    /**
     * Performs a two-way synchronization with Cloud Firestore:
     * 1. Pulls remote Musical DNA, liked tracks, and playlists and merges them with local data.
     * 2. Pushes the merged state back to `users/{uid}`, `users/{uid}/liked_tracks`, and `users/{uid}/playlists`.
     */
    suspend fun syncWithCloud(
        tasteProfile: UserTasteProfile,
        favoriteTracks: List<Track>,
        likedArtists: Set<String>,
        likedAlbums: Set<String>,
        dislikedTrackIds: Set<String>,
        playlists: List<PlaylistWithTracks>
    ): Result<CloudAccountState> = withContext(Dispatchers.IO) {
        val user = auth?.currentUser ?: return@withContext Result.failure(
            IllegalStateException("Пользователь не авторизован")
        )
        val db = firestore ?: return@withContext Result.failure(
            IllegalStateException("Firestore недоступен")
        )

        _accountState.value = _accountState.value.copy(
            isSyncing = true,
            errorMessage = null,
            statusMessage = "Синхронизация ДНК и медиатеки с облаком…"
        )

        try {
            val uid = user.uid
            val userDocRef = db.collection("users").document(uid)

            // 1. Pull existing cloud profile if present and merge with local preferences
            val remoteSnap = runCatching { userDocRef.get().await() }.getOrNull()
            val mergedGenres = tasteProfile.selectedGenreIds.toMutableSet()
            val mergedSubgenres = tasteProfile.selectedSubgenreIds.toMutableSet()
            val mergedArtists = tasteProfile.selectedArtists.toMutableSet()
            val mergedFavArtists = likedArtists.toMutableSet()
            val mergedAlbums = likedAlbums.toMutableSet()
            val mergedDisliked = dislikedTrackIds.toMutableSet()

            if (remoteSnap != null && remoteSnap.exists()) {
                val remoteGenres = (remoteSnap.get("selectedGenreIds") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()
                val remoteSubgenres = (remoteSnap.get("selectedSubgenreIds") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()
                val remoteArtists = (remoteSnap.get("selectedArtists") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()
                val remoteFavArtists = (remoteSnap.get("favoriteArtists") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()
                val remoteAlbums = (remoteSnap.get("likedAlbums") as? List<*>)
                    ?.mapNotNull { it as? String }.orEmpty()

                mergedGenres.addAll(remoteGenres)
                mergedSubgenres.addAll(remoteSubgenres)
                mergedArtists.addAll(remoteArtists)
                mergedFavArtists.addAll(remoteFavArtists)
                mergedAlbums.addAll(remoteAlbums)

                if (mergedGenres.isNotEmpty() || mergedArtists.isNotEmpty() || mergedSubgenres.isNotEmpty()) {
                    prefsRepo.saveTasteOnboardingResult(
                        selectedGenreIds = mergedGenres,
                        selectedSubgenreIds = mergedSubgenres,
                        selectedArtists = mergedArtists,
                        discoveryBalance = tasteProfile.discoveryBalance,
                        preferFullLengthTracks = true
                    )
                }
                remoteFavArtists.forEach { artist ->
                    if (artist !in likedArtists) {
                        prefsRepo.toggleLikedArtist(artist)
                    }
                }
            }

            // 2. Pull cloud liked tracks & merge into Room
            val remoteLikedDocs = runCatching {
                userDocRef.collection("liked_tracks").limit(150).get().await()
            }.getOrNull()

            val cloudTracksToMerge = mutableListOf<Track>()
            remoteLikedDocs?.documents?.forEach { doc ->
                val trackId = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: return@forEach
                val artist = doc.getString("artist") ?: "Unknown Artist"
                val album = doc.getString("album").orEmpty()
                val artworkUrl = doc.getString("artworkUrl").orEmpty()
                val durationSec = (doc.getLong("durationSec")?.toInt() ?: 180).coerceAtLeast(60)
                val providerName = doc.getString("primaryProvider") ?: SourceProvider.YOUTUBE_PIPED.name
                val provider = runCatching { SourceProvider.valueOf(providerName) }
                    .getOrDefault(SourceProvider.YOUTUBE_PIPED)
                val rawStream = doc.getString("streamUrl").orEmpty()
                val safeStream = if (rawStream.isBlank() || isPreviewSnippetUrl(rawStream)) {
                    "fulltrack://${artist.trim()}|${title.trim()}|$durationSec"
                } else {
                    rawStream
                }
                val genre = doc.getString("genre").orEmpty()
                val bitrate = (doc.getLong("bitrateKbps")?.toInt() ?: 320).coerceIn(0, 1411)

                cloudTracksToMerge.add(
                    Track(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = artworkUrl,
                        durationSec = durationSec,
                        primaryProvider = provider,
                        streamUrl = safeStream,
                        isFavorite = true,
                        isPreviewOnly = false,
                        genre = genre,
                        bitrateKbps = bitrate
                    )
                )
            }

            if (cloudTracksToMerge.isNotEmpty()) {
                musicRepo.mergeCloudFavoriteTracks(cloudTracksToMerge)
            }

            // 3. Push merged UserAccountDocument to `users/{uid}`
            val now = System.currentTimeMillis()
            val displayName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "SlantMuz Listener"

            val userPayload = mapOf(
                "uid" to uid,
                "email" to user.email.orEmpty().take(200),
                "displayName" to displayName.take(120),
                "photoUrl" to (user.photoUrl?.toString().orEmpty().take(1000)),
                "selectedGenreIds" to mergedGenres.take(40),
                "selectedSubgenreIds" to mergedSubgenres.take(80),
                "selectedArtists" to mergedArtists.take(100),
                "favoriteArtists" to mergedFavArtists.take(150),
                "likedAlbums" to mergedAlbums.take(150),
                "dislikedTrackIds" to mergedDisliked.take(300),
                "preferFullLengthTracks" to true,
                "discoveryBalance" to tasteProfile.discoveryBalance.name,
                "onboardingCompleted" to (tasteProfile.isOnboardingCompleted || mergedGenres.isNotEmpty() || mergedArtists.isNotEmpty()),
                "updatedAtMs" to now
            )
            userDocRef.set(userPayload, SetOptions.merge()).await()

            // 4. Push local favorite tracks to `users/{uid}/liked_tracks` (up to 80 most recent)
            val allFavorites = (favoriteTracks + cloudTracksToMerge).distinctBy { it.id }.take(80)
            val batch = db.batch()
            allFavorites.forEach { track ->
                val docId = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(120)
                val trackRef = userDocRef.collection("liked_tracks").document(docId)
                val safeStream = if (isPreviewSnippetUrl(track.streamUrl)) {
                    "fulltrack://${track.artist.trim()}|${track.title.trim()}|${track.durationSec.coerceAtLeast(180)}"
                } else {
                    track.streamUrl.take(1500)
                }
                val trackData = mapOf(
                    "id" to track.id.take(150),
                    "title" to track.title.take(300),
                    "artist" to track.artist.take(300),
                    "album" to track.album.take(300),
                    "artworkUrl" to track.artworkUrl.take(1000),
                    "durationSec" to track.durationSec.coerceIn(0, 86400),
                    "primaryProvider" to track.primaryProvider.name.take(60),
                    "streamUrl" to safeStream,
                    "genre" to track.genre.take(120),
                    "bitrateKbps" to track.bitrateKbps.coerceIn(0, 2000),
                    "addedAtMs" to now
                )
                batch.set(trackRef, trackData, SetOptions.merge())
            }

            // 5. Push custom playlists to `users/{uid}/playlists`
            playlists.take(25).forEach { plWithTracks ->
                val plId = plWithTracks.playlist.playlistId
                val plRef = userDocRef.collection("playlists").document("pl_$plId")
                val plData = mapOf(
                    "playlistId" to plId,
                    "name" to plWithTracks.playlist.name.take(160),
                    "description" to plWithTracks.playlist.description.take(500),
                    "trackIds" to plWithTracks.tracks.map { it.id }.take(300),
                    "updatedAtMs" to now
                )
                batch.set(plRef, plData, SetOptions.merge())
            }

            batch.commit().await()

            val syncedState = buildAccountStateFromUser(user).copy(
                isSyncing = false,
                lastSyncedAtMs = now,
                syncedTracksCount = allFavorites.size,
                syncedPlaylistsCount = playlists.size,
                syncedArtistsCount = (mergedArtists + mergedFavArtists).distinct().size,
                errorMessage = null,
                statusMessage = "Синхронизировано: ${allFavorites.size} треков • ${(mergedArtists + mergedFavArtists).distinct().size} артистов"
            )
            _accountState.value = syncedState
            Result.success(syncedState)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Ошибка синхронизации с облаком"
            _accountState.value = _accountState.value.copy(
                isSyncing = false,
                errorMessage = errMsg,
                statusMessage = "Локальные данные сохранены (облако: $errMsg)"
            )
            Result.failure(e)
        }
    }

    fun triggerBackgroundSync(
        tasteProfile: UserTasteProfile,
        favoriteTracks: List<Track>,
        likedArtists: Set<String>,
        likedAlbums: Set<String>,
        dislikedTrackIds: Set<String>,
        playlists: List<PlaylistWithTracks>
    ) {
        if (auth?.currentUser == null) return
        scope.launch {
            syncWithCloud(
                tasteProfile = tasteProfile,
                favoriteTracks = favoriteTracks,
                likedArtists = likedArtists,
                likedAlbums = likedAlbums,
                dislikedTrackIds = dislikedTrackIds,
                playlists = playlists
            )
        }
    }
}

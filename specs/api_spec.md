# Audiobookshelf Server API - Client Integration Specification

This document defines the server API endpoints, request payloads, and data structures utilized by the Skald, merging insights from the official Audiobookshelf mobile reference app (`audiobookshelf-app`) and our client application's current implementation.

---

## HTTP Headers Reference

The Audiobookshelf server relies on specific HTTP headers for authentication, payload formats, and conditional request handling:

| Header | Scope | Format | Description |
| :--- | :--- | :--- | :--- |
| **`Authorization`** | Request | `Bearer <jwt_token>` | Required for all `/api/...` endpoints (except `/api/authorize` when authenticating for the first time). |
| **`Content-Type`** | Request | `application/json` | Required for all requests carrying a JSON payload (`POST`, `PATCH`). |
| **`x-return-tokens`** | Request | `true` | Sent during login (`/login` or `/api/authorize`) to instruct the server to return refresh tokens. |
| **`x-refresh-token`** | Request | `<refresh_token_string>` | Custom header required by `/auth/refresh` to request a new access token. |
| **`If-None-Match`** | Request | `<etag_hash>` | Sent in conditional `GET` requests. Server returns `304 Not Modified` if the data matches. |
| **`ETag`** | Response | `<etag_hash>` | Returned by server `GET` requests to identify the unique state/version of resources. |
| **`Set-Cookie`** | Response | `abs-refresh-token=<token>; ...` | Returned by the server on successful login; contains the refresh token. |

---

## 1. Authentication & Session Management

### A. Login (User Authorization)

*   **Our App Endpoint**: `POST /login`
*   **Reference App Endpoint**: `POST /api/authorize`

#### Headers
*   **Request**: 
    *   `Content-Type: application/json`
    *   `x-return-tokens: true` (Tells the server to return the refresh token)
*   **Response**: 
    *   `Set-Cookie: abs-refresh-token=<refresh_token>; HttpOnly; Secure; ...` (Contains the refresh token for future access token renewals)

#### Request Schema (`POST /login` - Our App)
```json
{
  "username": "user",
  "password": "password"
}
```

#### Response Schema (`POST /login` - Our App)

```json
{
  "user": {
    "username": "user",
    "token": "jwt_token_here"
  }
}
```

#### Response Schema (`POST /api/authorize` - Reference App)
Returns the full user profile, settings, and initial list of in-progress media progress objects:
```json
{
  "user": {
    "id": "user_id_uuid",
    "username": "user",
    "token": "jwt_token_here",
    "mediaProgress": [
      {
        "id": "progress_id",
        "libraryItemId": "item_id",
        "episodeId": null,
        "duration": 3600.0,
        "progress": 0.25,
        "currentTime": 900.0,
        "isFinished": false,
        "lastUpdate": 1716945600000
      }
    ]
  }
}
```

---

### B. Auth Token Refresh (Reference App)

*   **Endpoint**: `POST /auth/refresh`

#### Headers
*   **Request**: 
    *   `Content-Type: application/json`
    *   `x-refresh-token: <refresh_token>`
*   **Response**: None

#### Response Schema
```json
{
  "user": {
    "accessToken": "new_jwt_access_token",
    "refreshToken": "optional_new_refresh_token"
  }
}
```

---

## 2. Library & Content Loading

### A. Fetch Libraries
Retrieves the collections of media (audiobooks or podcasts) configured on the server.

*   **Our App Endpoint**: `GET /api/libraries`
*   **Reference App Endpoint**: `GET /api/libraries?include=stats`

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
*   **Response**: None

#### Response Schema (Merged Overview)
```json
{
  "libraries": [
    {
      "id": "lib_id",
      "name": "Audiobooks",
      "mediaType": "book",
      "icon": "audiobooks",
      "stats": { // Only if include=stats is appended
        "totalItems": 42,
        "totalSize": 12884901888,
        "totalDuration": 151200.0,
        "numAudioFiles": 180
      }
    }
  ]
}
```

---

### B. Fetch Library Items
Retrieves books or podcasts in a specified library.

*   **Endpoint**: `GET /api/libraries/{libraryId}/items`
*   **Our Parameters**: `limit` (Int), `page` (Int), `minified` (Int) [defaults to `1` on server, set to `0` to request enhanced/non-minified metadata]
*   **Reference Parameters**: `limit` (Int), `minified` (Boolean/Int), `filter` (String), `sort` (String), `collapseseries` (Int)

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `If-None-Match: <etag>` (Optional, for caching)
*   **Response**: 
    *   `ETag: <etag>` (Returned with `200 OK`)
    *   *Status Code: `304 Not Modified` is returned if matching etag is sent*

#### Response Schema (Merged DTO)
```json
{
  "results": [
    {
      "id": "item_uuid",
      "mediaType": "book", // "book" or "podcast"
      "media": {
        "metadata": {
          "title": "Title",
          "subtitle": "Subtitle",
          "authorName": "Author Name",
          "narratorName": "Narrator Name",
          "seriesName": "Series Title",
          "authors": [{ "id": "author_id", "name": "Author Name" }],
          "narrators": ["Narrator Name"],
          "genres": ["Fiction"],
          "publishedYear": "2026",
          "description": "Detailed book description, returned when minified=0..."
        },
        "numChapters": 12,
        "duration": 3600.0,
        "size": 52428800
      }
    }
  ],
  "total": 1
}
```

---

### C. Fetch Item Details
Gets the complete manifest for a single book or podcast, including individual tracks and chapters.

*   **Endpoint**: `GET /api/items/{itemId}`
*   **Reference Parameters**: `expanded=1`, `include=progress`

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `If-None-Match: <etag>` (Optional, for caching)
*   **Response**: 
    *   `ETag: <etag>` (Returned with `200 OK`)
    *   *Status Code: `304 Not Modified` is returned if matching etag is sent*

#### Response Schema (Merged DTO)
```json
{
  "id": "item_uuid",
  "mediaType": "book",
  "media": {
    "metadata": {
      "title": "Title",
      "authors": [{ "id": "author_id", "name": "Author Name" }],
      "narrators": ["Narrator Name"],
      "description": "Book synopsis...",
      "publishedYear": "2026",
      "publisher": "Publisher Co"
    },
    "audioFiles": [
      {
        "index": 1,
        "ino": "file_inode_string",
        "duration": 1800.0,
        "mimeType": "audio/mpeg",
        "metadata": {
          "filename": "part1.mp3",
          "ext": "mp3",
          "size": 26214400
        }
      }
    ],
    "chapters": [
      {
        "id": 1,
        "start": 0.0,
        "end": 900.0,
        "title": "Chapter 1"
      }
    ]
  }
}
```

---

### D. Fetch Library Series
Retrieves series configured in a library.

*   **Endpoint**: `GET /api/libraries/{libraryId}/series`
*   **Parameters**: `limit` (Int), `minified` (Int) [defaults to `1` on server. Set to `0` to request non-minified metadata which includes the list of books in the series and their sequence numbers, allowing correct ordering]

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `If-None-Match: <etag>` (Optional, for caching)

#### Response Schema (minified = 0)
```json
{
  "results": [
    {
      "id": "series_uuid",
      "name": "Series Name",
      "description": "Series description...",
      "addedAt": 1716945600000,
      "updatedAt": 1716945600000,
      "books": [
        {
          "id": "book_uuid",
          "media": {
            "metadata": {
              "title": "Book Title",
              "series": [
                {
                  "id": "series_uuid",
                  "name": "Series Name",
                  "sequence": "1"
                }
              ]
            }
          }
        }
      ]
    }
  ],
  "total": 1
}
```

---


## 3. Playback Sessions & Progress Syncing

### A. Start Playback Session
Prepares the server to track active playback.

*   **Endpoint**: `POST /api/items/{itemId}/play` (or `/api/items/{itemId}/play/{episodeId}`)

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `Content-Type: application/json`
*   **Response**: None

#### Request Schema
```json
{
  "deviceInfo": {
    "clientName": "Skald Android",
    "deviceId": "unique_device_uuid",
    "deviceName": "Pixel 10 Pro"
  },
  "supportedMimeTypes": ["audio/mpeg", "audio/mp4", "audio/aac", "audio/ogg", "application/x-mpegURL"],
  "mediaPlayer": "ExoPlayer",
  "forceTranscode": false,
  "forceDirectPlay": true
}
```

#### Response Schema (Our App - Basic Session)
```json
{
  "id": "playback_session_uuid",
  "libraryItemId": "item_uuid"
}
```

#### Response Schema (Reference App - Full Session)
The reference app receives `audioTracks` representing the actual files or streaming endpoints to pass directly to the player:
```json
{
  "id": "playback_session_uuid",
  "userId": "user_uuid",
  "libraryItemId": "item_uuid",
  "episodeId": null,
  "mediaType": "book",
  "duration": 3600.0,
  "playMethod": 1, // 0 = local, 1 = directplay, 2 = transcode
  "currentTime": 0.0,
  "audioTracks": [
    {
      "index": 1,
      "contentUrl": "/public/session/playback_session_uuid/track/1",
      "mimeType": "audio/mpeg",
      "duration": 3600.0
    }
  ]
}
```

---

### B. Active Session Progress Sync
Sent periodically (e.g., every 10 seconds) during active listening.

*   **Endpoint**: `POST /api/session/{sessionId}/sync`

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `Content-Type: application/json`
*   **Response**: None

#### Request Schema
```json
{
  "timeListened": 10.0,
  "currentTime": 910.0
}
```

---

### C. Manual / Offline Progress Sync

#### 1. Retrieve Current Server Progress
*   **Endpoint**: `GET /api/me/progress/{itemId}` (or `/api/me/progress/{itemId}/{episodeId}`)

##### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
*   **Response**: None

##### Response Schema
```json
{
  "currentTime": 910.0,
  "isFinished": false,
  "progress": 0.2527,
  "lastUpdate": 1716945600000
}
```

#### 2. Retrieve All Progress (User Profile)
Retrieves the logged-in user profile, which contains the list of all active media progress states. Used for global and regular progress synchronization.

*   **Endpoint**: `GET /api/me`

##### Headers
*   **Request**:
    *   `Authorization: Bearer <jwt_token>`
    *   `If-None-Match: <etag>` (Optional, to use cache and avoid overloading the server)
*   **Response**:
    *   `ETag: <etag>` (Returned with `200 OK`)
    *   *Status Code: `304 Not Modified` is returned if matching ETag is sent*

##### Response Schema (`GET /api/me` - 200 OK)
Returns the root user profile directly, containing the `mediaProgress` list of all active items:
```json
{
  "id": "user_id_uuid",
  "username": "user",
  "mediaProgress": [
    {
      "id": "progress_id",
      "libraryItemId": "item_id",
      "episodeId": null,
      "duration": 3600.0,
      "progress": 0.25,
      "currentTime": 900.0,
      "isFinished": false,
      "lastUpdate": 1716945600000,
      "startedAt": 1716945000000,
      "finishedAt": null
    }
  ]
}
```

#### 3. Update Progress State
*   **Our App Endpoint**: `POST /api/me/progress`
*   **Reference App Endpoint**: `PATCH /api/me/progress/{itemId}`

##### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `Content-Type: application/json`
*   **Response**: None

##### Our Request Payload (`POST /api/me/progress` - Array format)
```json
[
  {
    "libraryItemId": "item_uuid",
    "currentTime": 910.0,
    "progress": 0.2527,
    "isFinished": false
  }
]
```

##### Reference Request Payload (`PATCH /api/me/progress/{itemId}`)
```json
{
  "currentTime": 910.0,
  "progress": 0.2527,
  "isFinished": false,
  "lastUpdate": 1716945600000
}
```

> [!WARNING]
> The reference app utilizes `lastUpdate` timestamps to enforce client-server conflict resolution, ensuring that a newer local playback state doesn't get overwritten by an older server state, or vice versa.

---

### D. Offline Session Syncing (Undocumented)
Used exclusively by mobile clients to synchronize queued sessions compiled during offline usage.

#### 1. Sync Single Local Session
*   **Endpoint**: `POST /api/session/local`

##### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `Content-Type: application/json`
*   **Response**: None

##### Payload
Full `PlaybackSession` object representing the offline event.

#### 2. Batch Sync Local Sessions
*   **Endpoint**: `POST /api/session/local-all`

##### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
    *   `Content-Type: application/json`
*   **Response**: None

##### Payload
```json
{
  "sessions": [
    {
      "id": "session_uuid",
      "userId": "user_id",
      "libraryItemId": "item_uuid",
      "currentTime": 1800.0,
      "duration": 3600.0,
      "timeListening": 900000,
      "updatedAt": 1716945600000
    }
  ],
  "deviceInfo": {
    "deviceId": "device_uuid",
    "clientName": "Skald Android"
  }
}
```

---

## 4. File Downloads

Used to cache audio tracks locally for offline-first support.

*   **Endpoint**: `GET /api/items/{itemId}/file/{ino}/download`

#### Headers
*   **Request**: 
    *   `Authorization: Bearer <jwt_token>`
*   **Response**: 
    *   `Content-Type: audio/mpeg` (or other audio MIME type)
    *   `Content-Length: <file_size_bytes>`

---

## 5. Playlists

Playlists represent user-constructed lists of audio tracks or library items that can be played sequentially. Playlists are stored globally for the user account.

### A. Fetch Playlists

Retrieves the list of all playlists defined on the server for the authorized user.

*   **Endpoint**: `GET /api/playlists`

#### Headers
*   **Request**:
    *   `Authorization: Bearer <jwt_token>`
    *   `If-None-Match: <etag>` (Optional, for caching)
*   **Response**:
    *   `ETag: <etag>` (Returned with `200 OK`)
    *   *Status Code: `304 Not Modified` is returned if matching ETag is sent*

#### Response Schema (Merged DTO)

The server returns a JSON object wrapper rather than a raw array:

```json
{
  "playlists": [
    {
      "id": "playlist_uuid",
      "name": "My Playlist",
      "description": "Playlist description",
      "userId": "user_uuid",
      "coverPath": "/api/playlists/playlist_uuid/cover",
      "totalDuration": 7200.0,
      "items": [
        {
          "id": "playlist_item_uuid", // Optional/nullable, see warning below
          "libraryItemId": "item_uuid",
          "episodeId": null,
          "sequence": 1,
          "libraryItem": {
            "id": "item_uuid",
            "media": {
              "metadata": {
                "title": "Book Title",
                "authorName": "Author Name"
              },
              "duration": 3600.0
            }
          }
        }
      ],
      "lastUpdate": 1716945600000
    }
  ]
}
```

> [!WARNING]
> **Playlist Item ID Fallback**: Playlist items returned in the `items` list may lack a unique `id` field from the server. To avoid deserialization errors and maintain database primary key integrity:
> 1. The client-side DTO models the playlist item `id` as optional and nullable (`val id: String? = null`).
> 2. If the `id` field is missing, the client must generate a fallback unique identifier locally using the structure: `"${playlistId}_${libraryItemId}_${sequence}"` to insert into the local database.

---

### B. Fetch Playlist Details

Retrieves detailed information for a single playlist.

*   **Endpoint**: `GET /api/playlists/{playlistId}`

#### Headers
*   **Request**:
    *   `Authorization: Bearer <jwt_token>`
*   **Response**: None

#### Response Schema

Returns a single playlist object directly:

```json
{
  "id": "playlist_uuid",
  "name": "My Playlist",
  "description": "Playlist description",
  "userId": "user_uuid",
  "coverPath": "/api/playlists/playlist_uuid/cover",
  "totalDuration": 7200.0,
  "items": [
    {
      "id": null,
      "libraryItemId": "item_uuid",
      "sequence": 1,
      "libraryItem": {
        "id": "item_uuid",
        "media": {
          "metadata": {
            "title": "Book Title",
            "authorName": "Author Name"
          },
          "duration": 3600.0
        }
      }
    }
  ],
  "lastUpdate": 1716945600000
}
```

---

### C. Update Playlist (Reordering/Deletion)

Updates the metadata and ordered list of items in a playlist. Used for adding, reordering, and deleting items.

*   **Endpoint**: `PATCH /api/playlists/{playlistId}`

#### Headers
*   **Request**:
    *   `Authorization: Bearer <jwt_token>`
    *   `Content-Type: application/json`
*   **Response**: None

#### Request Schema

```json
{
  "name": "My Updated Playlist",
  "description": "New description details",
  "items": [
    {
      "libraryItemId": "item_uuid_1",
      "episodeId": null
    },
    {
      "libraryItemId": "item_uuid_2",
      "episodeId": null
    }
  ]
}
```

#### Response Schema

Returns the updated playlist object in the same format as **Fetch Playlist Details**.

package dev.vikingsen.skald.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.paging.PagingSource
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromAudioFileList(value: List<LocalAudioFile>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toAudioFileList(value: String): List<LocalAudioFile> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromChapterList(value: List<LocalChapter>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toChapterList(value: String): List<LocalChapter> {
        return Json.decodeFromString(value)
    }
}

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Query("SELECT * FROM books")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookByIdFlow(id: String): Flow<BookEntity?>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)

    @RawQuery(observedEntities = [BookEntity::class, PlaybackProgressEntity::class])
    fun getBooksPaged(query: SupportSQLiteQuery): PagingSource<Int, BookWithProgressEntity>

    @Query("""
        SELECT b.* FROM books b 
        INNER JOIN playback_progress p ON b.id = p.bookId 
        WHERE b.libraryId = :libraryId AND p.progress > 0 AND p.isFinished = 0 
        ORDER BY p.lastUpdated DESC
    """)
    fun getBooksInProgressFlow(libraryId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE libraryId = :libraryId AND isDownloaded = 1")
    fun getDownloadedBooksFlow(libraryId: String): Flow<List<BookEntity>>

    @Transaction
    @Query("""
        SELECT b.*, 
        p.bookId AS progress_bookId, p.currentTime AS progress_currentTime, 
        p.progress AS progress_progress, p.isFinished AS progress_isFinished, 
        p.lastUpdated AS progress_lastUpdated 
        FROM books b LEFT JOIN playback_progress p ON b.id = p.bookId 
        WHERE b.libraryId = :libraryId AND b.seriesId IS NOT NULL
    """)
    fun getBooksWithProgressForLibraryFlow(libraryId: String): Flow<List<BookWithProgressEntity>>

    @Transaction
    @Query("""
        SELECT b.*, 
        p.bookId AS progress_bookId, p.currentTime AS progress_currentTime, 
        p.progress AS progress_progress, p.isFinished AS progress_isFinished, 
        p.lastUpdated AS progress_lastUpdated 
        FROM books b LEFT JOIN playback_progress p ON b.id = p.bookId 
        WHERE b.seriesId = :seriesId
    """)
    fun getBooksForSeriesWithProgressFlow(seriesId: String): Flow<List<BookWithProgressEntity>>

    @Transaction
    @Query("""
        SELECT b.*, 
        p.bookId AS progress_bookId, p.currentTime AS progress_currentTime, 
        p.progress AS progress_progress, p.isFinished AS progress_isFinished, 
        p.lastUpdated AS progress_lastUpdated 
        FROM books b LEFT JOIN playback_progress p ON b.id = p.bookId 
        INNER JOIN author_books ab ON b.id = ab.bookId
        WHERE ab.authorId = :authorId
    """)
    fun getBooksForAuthorWithProgressFlow(authorId: String): Flow<List<BookWithProgressEntity>>

    @Transaction
    @Query("""
        SELECT b.*, 
        p.bookId AS progress_bookId, p.currentTime AS progress_currentTime, 
        p.progress AS progress_progress, p.isFinished AS progress_isFinished, 
        p.lastUpdated AS progress_lastUpdated 
        FROM books b LEFT JOIN playback_progress p ON b.id = p.bookId 
        INNER JOIN collection_books cb ON b.id = cb.bookId
        WHERE cb.collectionId = :collectionId
        ORDER BY cb.sortOrder ASC
    """)
    fun getBooksForCollectionWithProgressFlow(collectionId: String): Flow<List<BookWithProgressEntity>>
}

@Dao
interface SeriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(seriesList: List<SeriesEntity>)

    @Query("SELECT * FROM series WHERE libraryId = :libraryId")
    fun getSeriesFlow(libraryId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :seriesId")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :seriesId")
    fun getSeriesByIdFlow(seriesId: String): Flow<SeriesEntity?>

    @Query("SELECT COUNT(*) FROM series WHERE libraryId = :libraryId")
    fun getSeriesCountFlow(libraryId: String): Flow<Int>

    @Query("DELETE FROM series WHERE libraryId = :libraryId")
    suspend fun deleteSeriesForLibrary(libraryId: String)

    @Transaction
    suspend fun replaceSeriesForLibrary(libraryId: String, seriesList: List<SeriesEntity>) {
        deleteSeriesForLibrary(libraryId)
        insertAll(seriesList)
    }
}

@Dao
interface AuthorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(authorsList: List<AuthorEntity>)

    @Query("SELECT * FROM authors WHERE libraryId = :libraryId")
    fun getAuthorsFlow(libraryId: String): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM authors WHERE id = :authorId")
    suspend fun getAuthorById(authorId: String): AuthorEntity?

    @Query("SELECT COUNT(*) FROM authors WHERE libraryId = :libraryId")
    fun getAuthorsCountFlow(libraryId: String): Flow<Int>

    @Query("DELETE FROM authors WHERE libraryId = :libraryId")
    suspend fun deleteAuthorsForLibrary(libraryId: String)

    @Transaction
    suspend fun replaceAuthorsForLibrary(libraryId: String, authorsList: List<AuthorEntity>) {
        deleteAuthorsForLibrary(libraryId)
        insertAll(authorsList)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthorBookCrossRefs(crossRefs: List<AuthorBookCrossRef>)

    @Query("DELETE FROM author_books WHERE authorId = :authorId")
    suspend fun deleteAuthorBookCrossRefsForAuthor(authorId: String)

    @Transaction
    suspend fun replaceAuthorBookCrossRefsForAuthor(authorId: String, crossRefs: List<AuthorBookCrossRef>) {
        deleteAuthorBookCrossRefsForAuthor(authorId)
        insertAuthorBookCrossRefs(crossRefs)
    }
}

@Dao
interface PlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE bookId = :bookId")
    suspend fun getProgressForBook(bookId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE bookId = :bookId")
    fun getProgressForBookFlow(bookId: String): Flow<PlaybackProgressEntity?>

    @Query("SELECT * FROM playback_progress")
    fun getAllProgressFlow(): Flow<List<PlaybackProgressEntity>>

    @Query("DELETE FROM playback_progress WHERE bookId = :bookId")
    suspend fun deleteProgressForBook(bookId: String)

}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(libraries: List<LibraryEntity>)

    @Query("SELECT * FROM libraries")
    suspend fun getAllLibraries(): List<LibraryEntity>

    @Query("DELETE FROM libraries")
    suspend fun deleteAll()
}

@Dao
interface HomeShelfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShelves(shelves: List<HomeShelfEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShelfItems(items: List<HomeShelfItemEntity>)

    @Query("DELETE FROM home_shelves WHERE libraryId = :libraryId")
    suspend fun deleteShelvesForLibrary(libraryId: String)

    @Transaction
    @Query("SELECT * FROM home_shelves WHERE libraryId = :libraryId ORDER BY verticalSortOrder ASC")
    fun getShelvesWithItemsFlow(libraryId: String): Flow<List<HomeShelfWithItems>>

    @Transaction
    suspend fun replaceShelvesForLibrary(
        libraryId: String,
        shelves: List<HomeShelfEntity>,
        items: List<HomeShelfItemEntity>
    ) {
        deleteShelvesForLibrary(libraryId)
        insertShelves(shelves)
        insertShelfItems(items)
    }
}

@Database(
    entities = [
        BookEntity::class,
        PlaybackProgressEntity::class,
        LibraryEntity::class,
        HomeShelfEntity::class,
        HomeShelfItemEntity::class,
        SeriesEntity::class,
        AuthorEntity::class,
        AuthorBookCrossRef::class,
        CollectionEntity::class,
        CollectionBookCrossRef::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun libraryDao(): LibraryDao
    abstract fun homeShelfDao(): HomeShelfDao
    abstract fun seriesDao(): SeriesDao
    abstract fun authorDao(): AuthorDao
    abstract fun collectionDao(): CollectionDao
    abstract fun playlistDao(): PlaylistDao


    companion object {
        private const val DB_NAME = "skald_db"

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            ).fallbackToDestructiveMigration()
                .build()
        }
    }
}


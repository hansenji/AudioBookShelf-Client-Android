package dev.vikingsen.skald.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(collections: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<CollectionBookCrossRef>)

    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId")
    suspend fun deleteCrossRefsForCollection(collectionId: String)

    @Query("DELETE FROM collections WHERE libraryId = :libraryId")
    suspend fun deleteCollectionsForLibrary(libraryId: String)

    @Query("SELECT * FROM collections WHERE libraryId = :libraryId")
    fun getCollectionsFlow(libraryId: String): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: String): CollectionEntity?

    @Query("SELECT bookId FROM collection_books WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    fun getBookIdsForCollectionFlow(collectionId: String): Flow<List<String>>

    @Query("SELECT bookId FROM collection_books WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    suspend fun getBookIdsForCollection(collectionId: String): List<String>

    @Query("SELECT COUNT(*) FROM collections WHERE libraryId = :libraryId")
    fun getCollectionsCountFlow(libraryId: String): Flow<Int>

    @Query("""
        SELECT cb.collectionId, cb.bookId, b.coverPath 
        FROM collection_books cb
        LEFT JOIN books b ON cb.bookId = b.id
        ORDER BY cb.sortOrder ASC
    """)
    fun getCollectionBookCoversFlow(): Flow<List<CollectionBookCoverInfo>>

    @Transaction
    suspend fun replaceCollectionsForLibrary(
        libraryId: String,
        collections: List<CollectionEntity>,
        crossRefs: List<CollectionBookCrossRef>
    ) {
        deleteCollectionsForLibrary(libraryId)
        insertAll(collections)
        insertCrossRefs(crossRefs)
    }

    @Transaction
    suspend fun replaceCrossRefsForCollection(
        collectionId: String,
        crossRefs: List<CollectionBookCrossRef>
    ) {
        deleteCrossRefsForCollection(collectionId)
        insertCrossRefs(crossRefs)
    }

    @Query("""
        SELECT c.* FROM collections c
        INNER JOIN collection_books cb ON c.id = cb.collectionId
        WHERE cb.bookId = :bookId
    """)
    fun getCollectionsContainingBookFlow(bookId: String): Flow<List<CollectionEntity>>
}

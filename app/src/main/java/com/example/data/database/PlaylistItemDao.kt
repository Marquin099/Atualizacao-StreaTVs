package com.example.data.database

import androidx.room.*
import com.example.data.model.PlaylistItem
import com.example.data.model.FavoriteItem
import com.example.data.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Query("SELECT * FROM playlist_items")
    fun getAllItems(): Flow<List<PlaylistItem>>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type = :type 
        ORDER BY 
            CASE WHEN :type = 'live' THEN rowid END ASC,
            CASE WHEN :type != 'live' THEN id END DESC
    """)
    fun getItemsByType(type: String): Flow<List<PlaylistItem>>

    @Query("SELECT COUNT(*) FROM playlist_items")
    suspend fun getItemsCount(): Int

    @Query("SELECT url FROM playlist_items")
    suspend fun getAllUrls(): List<String>

    @Query("SELECT title FROM playlist_items WHERE title IN (:titles)")
    suspend fun getExistingTitles(titles: List<String>): List<String>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type IN ('movie', 'series')
        ORDER BY CASE WHEN (groupName LIKE '%Tendências%' OR groupName LIKE '%Tendencias%') THEN 0 ELSE 1 END, id DESC
        LIMIT 10
    """)
    fun getBannerItems(): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Int): PlaylistItem?

    @Query("""
        SELECT * FROM playlist_items 
        WHERE groupName = :groupName 
        ORDER BY 
            CASE WHEN type = 'live' THEN rowid END ASC,
            CASE WHEN type != 'live' THEN id END DESC
    """)
    fun getItemsByGroupName(groupName: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE groupName IN ('Tendências', 'Tendencias', 'Os Mais Populares', 'Últimos Trailers') ORDER BY rowid ASC")
    suspend fun getMainSectionsItems(): List<PlaylistItem>

    @Query("DELETE FROM playlist_items WHERE groupName IN ('Tendências', 'Tendencias', 'Os Mais Populares', 'Últimos Trailers')")
    suspend fun clearMainSectionsItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistItem>)

    @Query("DELETE FROM playlist_items")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM playlist_categories")
    suspend fun clearAllCategories()

    @Query("SELECT categoryName FROM playlist_categories WHERE type = :type ORDER BY displayOrder ASC")
    fun getCategoriesFromCategoriesTable(type: String): Flow<List<String>>

    @Transaction
    suspend fun replaceAll(items: List<PlaylistItem>) {
        clearAll()
        items.chunked(500).forEach { chunk ->
            insertAll(chunk)
        }
    }

    @Transaction
    suspend fun replaceMainSectionsItems(items: List<PlaylistItem>) {
        clearMainSectionsItems()
        insertAll(items)
    }

    @Query("DELETE FROM playlist_items WHERE type = :type")
    suspend fun clearAllByType(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteItem)

    @Query("DELETE FROM favorite_items WHERE profileId = :profileId AND itemId = :itemId")
    suspend fun deleteFavorite(profileId: Int, itemId: Int)

    @Query("""
        SELECT p.* FROM playlist_items p 
        INNER JOIN favorite_items f ON p.id = f.itemId 
        WHERE f.profileId = :profileId AND p.type = :type
    """)
    fun getFavoriteItems(profileId: Int, type: String): Flow<List<PlaylistItem>>

    @Query("""
        SELECT p.* FROM playlist_items p 
        INNER JOIN favorite_items f ON p.id = f.itemId 
        WHERE f.profileId = :profileId
        ORDER BY p.id DESC
    """)
    fun getAllFavoriteItems(profileId: Int): Flow<List<PlaylistItem>>

    @Query("SELECT itemId FROM favorite_items WHERE profileId = :profileId")
    fun getFavoriteItemIds(profileId: Int): Flow<List<Int>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_items WHERE profileId = :profileId AND itemId = :itemId)")
    suspend fun isFavorite(profileId: Int, itemId: Int): Boolean

    @Query("SELECT groupName FROM playlist_items WHERE type = :type GROUP BY groupName ORDER BY MIN(rowid) ASC")
    fun getCategoriesByType(type: String): Flow<List<String>>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type IN ('movie', 'series') 
          AND (metadataLoaded = 0 OR posterUrl IS NULL OR backdropUrl IS NULL OR description IS NULL OR clearLogoUrl IS NULL)
        ORDER BY 
          CASE WHEN groupName IN ('Tendências', 'Tendencias', 'Os Mais Populares', 'Últimos Trailers') THEN 0 ELSE 1 END,
          id DESC
    """)
    suspend fun getPriorityUnenrichedItems(): List<PlaylistItem>

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type IN ('movie', 'series') 
          AND (metadataLoaded = 0 OR posterUrl IS NULL OR backdropUrl IS NULL OR description IS NULL OR clearLogoUrl IS NULL)
        ORDER BY id DESC
    """)
    suspend fun getAllUnenrichedItems(): List<PlaylistItem>

    @Query("SELECT * FROM playlist_items WHERE title LIKE :query OR cleanTitle LIKE :query")
    suspend fun getItemsByTitleQuery(query: String): List<PlaylistItem>

    @Update
    suspend fun updateItem(item: PlaylistItem)

    @Update
    suspend fun updateItems(items: List<PlaylistItem>)

    @Query("""
        SELECT * FROM playlist_items 
        WHERE type = :type AND groupName = :groupName 
        ORDER BY 
            CASE WHEN :type = 'live' THEN rowid END ASC,
            CASE WHEN :type != 'live' THEN id END DESC
    """)
    fun getItemsByTypeAndCategory(type: String, groupName: String): Flow<List<PlaylistItem>>
}

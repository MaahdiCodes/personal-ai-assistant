package dev.maahdi.mavick.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.security.AndroidKeystoreKeyWrapper
import dev.maahdi.mavick.data.security.DatabaseKeyException
import dev.maahdi.mavick.data.security.DatabaseKeyRepository
import dev.maahdi.mavick.testing.containsSequence
import dev.maahdi.mavick.testing.task
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone: real SQLCipher, real Android Keystore. Uses its own test database and key. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyFile = File(context.noBackupFilesDir, "encryption-test.key")

    @Before
    fun setUp() = cleanUp()

    @After
    fun tearDown() = cleanUp()

    private fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
        keyFile.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS)
    }

    private fun openDatabase(): MavickDatabase = MavickDatabase.open(
        context,
        DatabaseKeyRepository(keyFile, AndroidKeystoreKeyWrapper(KEY_ALIAS)),
        DATABASE_NAME,
    )

    @Test
    fun databaseFilesAreEncryptedOnDisk() = runTest {
        val database = openDatabase()
        database.taskDao().insert(task(title = SECRET_TITLE))
        database.close()

        val mainFile = context.getDatabasePath(DATABASE_NAME)
        val allFiles = mainFile.parentFile!!.listFiles { file -> file.name.startsWith(DATABASE_NAME) }!!
        assertThat(mainFile.exists()).isTrue()
        // A plain SQLite file always starts with this header; an encrypted one never does.
        assertThat(mainFile.readBytes().copyOf(16)).isNotEqualTo("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        allFiles.forEach { file ->
            assertThat(file.readBytes().containsSequence(SECRET_TITLE.toByteArray(Charsets.UTF_8))).isFalse()
        }
    }

    @Test
    fun dataSurvivesReopeningWithTheStoredKey() = runTest {
        val saved = task(title = "Keep me")
        openDatabase().apply {
            taskDao().insert(saved)
            close()
        }

        val reopened = openDatabase()
        assertThat(reopened.taskDao().findById(saved.id)).isEqualTo(saved)
        reopened.close()
    }

    @Test
    fun aWrongKeyCannotReadTheDatabase() = runTest {
        openDatabase().apply {
            taskDao().insert(task())
            close()
        }

        val wrongKey = DatabaseKeyRepository.toRawKeyPassphrase(ByteArray(DatabaseKeyRepository.KEY_SIZE_BYTES) { 1 })
        val intruder = Room.databaseBuilder(context, MavickDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(wrongKey))
            .build()
        val result = runCatching { intruder.taskDao().countActive() }
        intruder.close()

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun aLostKeyIsReportedInsteadOfReplaced() = runTest {
        openDatabase().apply {
            taskDao().insert(task())
            close()
        }
        keyFile.delete()

        val error = runCatching { openDatabase() }.exceptionOrNull()

        assertThat(error).isInstanceOf(DatabaseKeyException::class.java)
        assertThat(keyFile.exists()).isFalse()
    }

    private companion object {
        const val DATABASE_NAME = "encryption-test.db"
        const val KEY_ALIAS = "mavick.test.encrypted-database"
        const val SECRET_TITLE = "Secret title 7Q9X"
    }
}

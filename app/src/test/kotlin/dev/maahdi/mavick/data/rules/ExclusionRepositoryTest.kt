package dev.maahdi.mavick.data.rules

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.MavickDatabase
import dev.maahdi.mavick.data.settings.SettingsRepository
import dev.maahdi.mavick.testing.MONDAY_10AM
import dev.maahdi.mavick.testing.MutableClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExclusionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = MutableClock(MONDAY_10AM)
    private val preferences = context.getSharedPreferences("rules-test", Context.MODE_PRIVATE)
    private lateinit var database: MavickDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var repository: ExclusionRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MavickDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(preferences)
        repository = ExclusionRepository(database.exclusionRuleDao(), settings, clock = { clock })
    }

    @After
    fun tearDown() {
        database.close()
        preferences.edit().clear().commit()
    }

    @Test
    fun `the default keywords are added the first time rules are needed`() = runTest {
        val rules = repository.all()

        assertThat(rules.map { it.value }).containsExactly("OTP", "password", "PIN", "verification code")
        assertThat(rules.all { it.type == RuleType.KEYWORD && it.effect == RuleEffect.EXCLUDE && it.app == null && it.accountKey == null }).isTrue()
        assertThat(settings.current.defaultRulesAdded).isTrue()
    }

    @Test
    fun `a deleted default stays deleted`() = runTest {
        val pin = repository.all().single { it.value == "PIN" }

        repository.remove(pin.id)

        assertThat(ExclusionRepository(database.exclusionRuleDao(), settings, clock = { clock }).all().map { it.value }).doesNotContain("PIN")
    }

    @Test
    fun `defaults are added once even when asked for at the same time`() = runTest {
        listOf(async { repository.all() }, async { repository.all() }).awaitAll()

        assertThat(database.exclusionRuleDao().getAll()).hasSize(ExclusionRepository.DEFAULT_KEYWORDS.size)
    }

    @Test
    fun `a rule is saved trimmed, with its scope`() = runTest {
        val rule = repository.add(RuleType.CHAT, RuleEffect.EXCLUDE, "  s:family  ", "Family", SourceApp.WHATSAPP, "0")!!

        assertThat(rule.value).isEqualTo("s:family")
        assertThat(rule.displayName).isEqualTo("Family")
        assertThat(rule.app).isEqualTo(SourceApp.WHATSAPP)
        assertThat(rule.accountKey).isEqualTo("0")
        assertThat(rule.createdAt).isEqualTo(clock.instant())
        assertThat(repository.all()).contains(rule)
    }

    @Test
    fun `blank rules and repeats are not added`() = runTest {
        assertThat(repository.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "   ", "")).isNull()
        assertThat(repository.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "salary", "salary")).isNotNull()
        assertThat(repository.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "SALARY", "SALARY")).isNull()
        assertThat(repository.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "otp", "otp")).isNull() // a default already
    }

    @Test
    fun `the same value with another type, effect or scope is a different rule`() = runTest {
        assertThat(repository.add(RuleType.SENDER, RuleEffect.EXCLUDE, "Sam", "Sam")).isNotNull()
        assertThat(repository.add(RuleType.SENDER, RuleEffect.ALLOW, "Sam", "Sam")).isNotNull()
        assertThat(repository.add(RuleType.CHAT, RuleEffect.EXCLUDE, "Sam", "Sam")).isNotNull()
        assertThat(repository.add(RuleType.SENDER, RuleEffect.EXCLUDE, "Sam", "Sam", app = SourceApp.GMAIL)).isNotNull()
    }

    @Test
    fun `an empty display name falls back to the value`() = runTest {
        assertThat(repository.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "rent", " ")!!.displayName).isEqualTo("rent")
    }

    @Test
    fun `matching rules record when`() = runTest {
        val rule = repository.add(RuleType.KEYWORD, RuleEffect.EXCLUDE, "rent", "rent")!!

        repository.markMatched(listOf(rule.id), clock.instant())
        repository.markMatched(emptyList(), clock.instant()) // nothing to do

        assertThat(repository.all().single { it.id == rule.id }.lastMatchedAt).isEqualTo(clock.instant())
    }
}

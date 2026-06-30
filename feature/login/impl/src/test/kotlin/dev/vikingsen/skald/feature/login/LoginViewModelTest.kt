package dev.vikingsen.skald.feature.login

import dev.vikingsen.skald.core.model.Library
import dev.vikingsen.skald.core.model.LoggedUser
import dev.vikingsen.skald.domain.fakes.FakeAudiobookshelfRepository
import dev.vikingsen.skald.domain.fakes.FakeSettingsRepository
import dev.vikingsen.skald.domain.usecase.FetchLibrariesUseCase
import dev.vikingsen.skald.domain.usecase.LoginUseCase
import dev.vikingsen.skald.domain.usecase.SyncLibraryBooksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakeAudiobookshelfRepository: FakeAudiobookshelfRepository
    private lateinit var fakeSettingsRepository: FakeSettingsRepository

    private lateinit var loginUseCase: LoginUseCase
    private lateinit var fetchLibrariesUseCase: FetchLibrariesUseCase
    private lateinit var syncLibraryBooksUseCase: SyncLibraryBooksUseCase

    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeAudiobookshelfRepository = FakeAudiobookshelfRepository()
        fakeSettingsRepository = FakeSettingsRepository()

        loginUseCase = LoginUseCase(fakeAudiobookshelfRepository)
        fetchLibrariesUseCase = FetchLibrariesUseCase(fakeAudiobookshelfRepository)
        syncLibraryBooksUseCase = SyncLibraryBooksUseCase(fakeAudiobookshelfRepository)

        viewModel = LoginViewModel(
            loginUseCase = loginUseCase,
            fetchLibrariesUseCase = fetchLibrariesUseCase,
            syncLibraryBooksUseCase = syncLibraryBooksUseCase,
            settingsRepository = fakeSettingsRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_defaultsAreCorrect() {
        assertEquals("", viewModel.serverUrl.value)
        assertEquals("", viewModel.username.value)
        assertEquals("", viewModel.password.value)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun initialState_loadsFromRepository() {
        fakeSettingsRepository.fakeUrl = "https://demo.server"
        fakeSettingsRepository.fakeUsername = "viking_user"

        val localViewModel = LoginViewModel(
            loginUseCase = loginUseCase,
            fetchLibrariesUseCase = fetchLibrariesUseCase,
            syncLibraryBooksUseCase = syncLibraryBooksUseCase,
            settingsRepository = fakeSettingsRepository
        )

        assertEquals("https://demo.server", localViewModel.serverUrl.value)
        assertEquals("viking_user", localViewModel.username.value)
    }

    @Test
    fun updateInputs_updatesStateFlows() {
        viewModel.updateServerUrl("https://my-server")
        viewModel.updateUsername("my-username")
        viewModel.updatePassword("my-password")

        assertEquals("https://my-server", viewModel.serverUrl.value)
        assertEquals("my-username", viewModel.username.value)
        assertEquals("my-password", viewModel.password.value)
    }

    @Test
    fun login_withEmptyInputs_setsError() {
        var successCalled = false

        viewModel.login { successCalled = true }

        assertTrue(viewModel.error.value == "All fields are required")
        assertFalse(successCalled)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun login_successfulFlow_authenticatesFetchesLibrariesAndSyncs() {
        viewModel.updateServerUrl("https://demo.server")
        viewModel.updateUsername("user")
        viewModel.updatePassword("password")

        val expectedUser = LoggedUser(
            username = "user",
            token = "fake-token"
        )
        fakeAudiobookshelfRepository.loginResult = Result.success(expectedUser)

        val mockLibraries = listOf(
            Library(id = "lib-1", name = "My Books", type = "audiobook"),
            Library(id = "lib-2", name = "My Podcasts", type = "podcast")
        )
        fakeAudiobookshelfRepository.fetchLibrariesResult = Result.success(mockLibraries)

        var successCalled = false

        viewModel.login { successCalled = true }

        assertTrue(successCalled)
        assertFalse(viewModel.isLoading.value)
        assertNull(viewModel.error.value)
        assertEquals("lib-1", fakeSettingsRepository.fakeLibraryId)
    }

    @Test
    fun login_failedAuth_setsErrorUIState() {
        viewModel.updateServerUrl("https://demo.server")
        viewModel.updateUsername("user")
        viewModel.updatePassword("password")

        fakeAudiobookshelfRepository.loginResult = Result.failure(Exception("Invalid credentials"))

        var successCalled = false
        viewModel.login { successCalled = true }

        assertFalse(successCalled)
        assertFalse(viewModel.isLoading.value)
        assertEquals("Invalid credentials", viewModel.error.value)
    }

    @Test
    fun login_failedFetchLibraries_setsErrorUIState() {
        viewModel.updateServerUrl("https://demo.server")
        viewModel.updateUsername("user")
        viewModel.updatePassword("password")

        val expectedUser = LoggedUser(
            username = "user",
            token = "fake-token"
        )
        fakeAudiobookshelfRepository.loginResult = Result.success(expectedUser)
        fakeAudiobookshelfRepository.fetchLibrariesResult = Result.failure(Exception("Network Timeout"))

        var successCalled = false
        viewModel.login { successCalled = true }

        assertFalse(successCalled)
        assertFalse(viewModel.isLoading.value)
        assertEquals("Failed to fetch libraries: Network Timeout", viewModel.error.value)
    }
}

package dev.vikingsen.skald.feature.login

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoginScreenTest {






    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun loginScreen_rendersCorrectly() {
        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = {},
                viewModel = viewModel
            )
        }

        // Verify title and prompt are visible
        composeTestRule.onNodeWithText("Audiobookshelf").assertExists()
        composeTestRule.onNodeWithText("Connect to your server").assertExists()

        // Verify fields are visible
        composeTestRule.onNodeWithText("Server URL").assertExists()
        composeTestRule.onNodeWithText("Username").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
        composeTestRule.onNodeWithText("Login").assertExists()
    }


    @Test
    fun loginScreen_inputtingText_updatesViewModelState() {
        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Server URL").performTextInput("https://demo.server")
        composeTestRule.onNodeWithText("Username").performTextInput("viking")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")

        assertEquals("https://demo.server", viewModel.serverUrl.value)
        assertEquals("viking", viewModel.username.value)
        assertEquals("password123", viewModel.password.value)
    }

    @Test
    fun loginScreen_successfulLogin_triggersOnLoginSuccess() {
        var loginSuccessCalled = false

        // Configure mock success responses
        fakeAudiobookshelfRepository.loginResult = Result.success(
            LoggedUser(username = "viking", token = "fake-token")
        )
        fakeAudiobookshelfRepository.fetchLibrariesResult = Result.success(
            listOf(Library(id = "lib-1", name = "Audiobooks", type = "audiobook"))
        )

        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = { loginSuccessCalled = true },
                viewModel = viewModel
            )
        }

        // Fill credentials and login
        composeTestRule.onNodeWithText("Server URL").performTextInput("https://demo.server")
        composeTestRule.onNodeWithText("Username").performTextInput("viking")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")

        composeTestRule.onNodeWithText("Login").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify state updates and callbacks
        assertTrue(loginSuccessCalled)
        assertEquals("lib-1", fakeSettingsRepository.fakeLibraryId)
    }


    @Test
    fun loginScreen_failedLogin_displaysErrorMessage() {
        fakeAudiobookshelfRepository.loginResult = Result.failure(
            Exception("Invalid credentials")
        )

        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Server URL").performTextInput("https://demo.server")
        composeTestRule.onNodeWithText("Username").performTextInput("viking")
        composeTestRule.onNodeWithText("Password").performTextInput("password123")

        composeTestRule.onNodeWithText("Login").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Verify the error label appears on screen
        composeTestRule.onNodeWithText("Invalid credentials").assertExists()
    }
}


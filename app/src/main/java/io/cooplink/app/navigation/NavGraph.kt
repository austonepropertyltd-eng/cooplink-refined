package io.cooplink.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.cooplink.app.auth.ForgotPasswordScreen
import io.cooplink.app.auth.LoginScreen
import io.cooplink.app.auth.OnboardingScreen
import io.cooplink.app.feature.admin.dashboard.AdminShell
import io.cooplink.app.feature.member.overview.MemberShell

object Routes {
    const val ONBOARDING      = "onboarding"
    const val LOGIN           = "login"
    const val FORGOT_PASSWORD = "forgot_password"
    const val MEMBER_SHELL    = "member"
    const val ADMIN_SHELL     = "admin"
}

@Composable
fun CoopLinkNavHost(
    startDestination: String = Routes.LOGIN,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { isAdmin ->
                    navController.navigate(
                        if (isAdmin) Routes.ADMIN_SHELL else Routes.MEMBER_SHELL
                    ) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onForgotPassword = {
                    navController.navigate(Routes.FORGOT_PASSWORD)
                },
            )
        }

        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.MEMBER_SHELL) {
            MemberShell(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.ADMIN_SHELL) {
            AdminShell(
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}

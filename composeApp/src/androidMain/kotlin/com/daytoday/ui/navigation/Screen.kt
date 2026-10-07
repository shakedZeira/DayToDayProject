package com.daytoday.ui.navigation

sealed interface Screen {
    val route: String

    data object Home : Screen {
        override val route: String = "Home"
    }

    data object NbaScoreboard : Screen {
        override val route: String = "NbaScoreboard"
    }

    data object NbaGameDetail : Screen {
        override val route: String = "NbaGameDetail"
    }

    data object NbaNews : Screen {
        override val route: String = "NbaNews"
    }

    data object NbaInjuries : Screen {
        override val route: String = "NbaInjuries"
    }

    data object WorkoutActive : Screen {
        override val route: String = "WorkoutActive"
    }

    data object WorkoutHistory : Screen {
        override val route: String = "WorkoutHistory"
    }

    data object WorkoutProgress : Screen {
        override val route: String = "WorkoutProgress"
    }

    data object PdfMerge : Screen {
        override val route: String = "PdfMerge"
    }

    data object Settings : Screen {
        override val route: String = "Settings"
    }

    data object Goals : Screen {
        override val route: String = "Goals"
    }
}

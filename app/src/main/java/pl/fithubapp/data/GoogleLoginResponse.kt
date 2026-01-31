package pl.fithubapp.data

data class GoogleLoginResponse(
    val token: String,
    val user: NewUserDto
)

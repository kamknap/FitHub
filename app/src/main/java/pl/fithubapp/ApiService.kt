package pl.fithubapp

import pl.fithubapp.data.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("/api/users")
    suspend fun getUsers(): List<NewUserDto>

    @GET("/api/users/me")
    suspend fun getCurrentUser(): NewUserDto

    @POST("/api/users")
    suspend fun createUser(@Body user: CreateUserDto): NewUserDto

    @POST("/api/user-goals")
    suspend fun createUserGoal(@Body userGoal: CreateUserGoalDto): UserGoalDto

    @GET("/api/user-goals/me")
    suspend fun getCurrentUserGoals(): List<UserGoalDto>

    @PUT("/api/users/me")
    suspend fun updateUser(@Body updateData: UpdateUserDto): NewUserDto

    @PUT("/api/user-goals/{id}")
    suspend fun updateUserGoal(
        @Path("id") id: String,
        @Body updateData: UpdateUserGoalDto
    ): UserGoalDto

    // Foods endpoints
    @GET("/api/foods")
    suspend fun getFoods(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("skip") skip: Int = 0
    ): FoodsResponse

    @GET("/api/foods/{id}")
    suspend fun getFoodById(@Path("id") id: String): FoodDto

    @GET("/api/foods/barcode/{barcode}")
    suspend fun getFoodByBarcode(@Path("barcode") barcode: String): FoodDto

    @POST("/api/foods")
    suspend fun createFood(@Body food: CreateFoodDto): FoodDto

    @DELETE("/api/foods/{id}")
    suspend fun deleteFood(@Path("id") id: String)

    // Nutrition endpoints
    @GET("/api/nutrition/me/{date}")
    suspend fun getDailyNutrition(
        @Path("date") date: String
    ): DailyNutritionWithFoodsDto

    @POST("/api/nutrition/me/{date}/meal")
    suspend fun addMeal(
        @Path("date") date: String,
        @Body addMealDto: AddMealDto
    ): DailyNutritionWithFoodsDto

    @DELETE("/api/nutrition/me/{date}/food/{itemId}")
    suspend fun deleteFoodByItemId(
        @Path("date") date: String,
        @Path("itemId") itemId: String
    ): DailyNutritionWithFoodsDto

    @PUT("/api/nutrition/me/{date}/food/{itemId}")
    suspend fun updateFoodQuantity(
        @Path("date") date: String,
        @Path("itemId") itemId: String,
        @Body request: UpdateFoodQuantityDto
    ): DailyNutritionWithFoodsDto

    // Weight History endpoints
    @GET("/api/weight-history/me")
    suspend fun getUserWeightHistory(): List<UserWeightHistoryDto>

    @POST("/api/weight-history")
    suspend fun createWeightMeasurement(@Body measurement: CreateWeightMeasurementDto): UserWeightHistoryDto

    //Exercises endpoints
    @GET("/api/exercises")
    suspend fun getExercisesByName(@Query("name") name: String): List<ExerciseDto>

    @GET("/api/exercises")
    suspend fun getExercisesByMuscleId(@Query("muscleId") name: String): List<ExerciseDto>

    // User Exercise Plans endpoints
    @GET("/api/user-exercise-plans/me")
    suspend fun getUserExercisePlans(): List<UserExercisePlanDto>

    @GET("/api/user-exercise-plans/{id}")
    suspend fun getUserExercisePlanById(@Path("id") id: String): UserExercisePlanDto

    @POST("/api/user-exercise-plans/me")
    suspend fun createUserExercisePlan(@Body plan: CreateUserExercisePlanDto): UserExercisePlanDto

    @PUT("/api/user-exercise-plans/{id}")
    suspend fun updateUserExercisePlan(
        @Path("id") id: String,
        @Body updateData: UpdateUserExercisePlanDto
    ): UserExercisePlanDto

    @DELETE("/api/user-exercise-plans/{id}")
    suspend fun deleteUserExercisePlan(@Path("id") id: String): DeletePlanResponseDto

    @POST("/api/user-exercise-plans/{id}/exercises")
    suspend fun addExerciseToPlan(
        @Path("id") planId: String,
        @Body exercise: AddExerciseToPlanDto
    ): UserExercisePlanDto

    @DELETE("/api/user-exercise-plans/{id}/exercises/{exercise_id}")
    suspend fun removeExerciseFromPlan(
        @Path("id") planId: String,
        @Path("exercise_id") exerciseId: String
    ): UserExercisePlanDto

    // User Progress endpoints
    @GET("/api/user-progress/me")
    suspend fun getUserProgress(): UserProgressDto

    @PUT("/api/user-progress/me")
    suspend fun updateUserProgress(@Body progress: UserProgressDto): UserProgressDto

    // Photos endpoints
    @GET("/api/photos")
    suspend fun getAllPhotos(): List<PhotoDto>

    @POST("/api/photos")
    suspend fun addPhoto(@Body photo: PhotoDto): PhotoDto

    @PUT("/api/photos/{id}")
    suspend fun updatePhoto(@Path("id") id: String, @Body photo: PhotoDto): PhotoDto

    @DELETE("/api/photos/{id}")
    suspend fun deletePhoto(@Path("id") id: String): Unit

    // Badges endpoints
    @GET("/api/badges")
    suspend fun getAllBadges(): List<BadgeDto>

    // Challenges endpoints
    @GET("/api/challenges")
    suspend fun getAllChallenges(): List<ChallengeDto>

    //Logowanie Google
    @POST("/api/users/google-login")
    suspend fun googleLogin(@Body request: GoogleLoginRequest): Response<GoogleLoginResponse>
}
package pl.fithubapp

import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val user = FirebaseAuth.getInstance().currentUser
        val token = if (user != null) {
            val task = user.getIdToken(false)
            try {
                Tasks.await(task).token
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val requestBuilder = chain.request().newBuilder()
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}

object NetworkModule {

    private const val USE_AZURE = true

    private val BASE_URL = if (USE_AZURE) {
        "https://fithubapp-backend.calmriver-05379b6c.polandcentral.azurecontainerapps.io"
    } else {
        // Lokalny serwer
        if (isEmulator()) {
            "http://10.0.2.2:4000" // emulator
        } else {
            "http://192.168.1.28:4000" // fizyczny telefon
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("NetworkModule", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val gson = GsonBuilder()
        .serializeNulls()
        .create()

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(AuthInterceptor())
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
    private val offClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "MyFitHub/1.0 (kamil.knapik@outlook.com)")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // serwis dla wyszukiwania po nazwie (polska baza)
    val offApi: OpenFoodFactsService by lazy {
        Retrofit.Builder()
            .baseUrl("https://pl.openfoodfacts.org/")
            .client(offClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsService::class.java)
    }

    // serwis do wyszukiwania po barcode (światowa baza)
    val offBarcodeApi: OpenFoodFactsService by lazy {
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(offClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsService::class.java)
    }

}
package pl.fithubapp

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import pl.fithubapp.data.FoodDto
import pl.fithubapp.data.NutritionData
import pl.fithubapp.data.OpenFoodFactsProduct

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //onboarding
        val buttonOnboarding = findViewById<Button>(R.id.btnNavigateBMI)
        buttonOnboarding.setOnClickListener {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
        }

        //muscle model
        val buttonMuscle = findViewById<Button>(R.id.btnMuscle)
        buttonMuscle.setOnClickListener {
            val intent = Intent(this, MuscleModel::class.java)
            startActivity(intent)
        }

        //diary
        val buttonDiary = findViewById<Button>(R.id.btnDiary)
        buttonDiary.setOnClickListener {
            val intent = Intent(this, UserMainActivity::class.java)
            startActivity(intent)
        }

        //api
        val btnApi = findViewById<Button>(R.id.btnApi)
        btnApi.setOnClickListener {

            lifecycleScope.launch {
                try {
                    val searchResult = NetworkModule.offApi.searchProducts(
                        query = "pierogi z serem",
                        limit = 10
                    )

                    if (searchResult.products?.isNotEmpty() == true) {

                        Log.d("OpenFoodFacts", "--- Nazwy przed mapowaniem ---")
                        searchResult.products.forEach { product ->
                            Log.d("OpenFoodFacts", "Oryginalny produkt: ${product.product_name ?: "Brak nazwy"}")
                        }

                        val foodDtoList = searchResult.products.map { product ->
                            mapOpenFoodFactsToFood(product)
                        }

                        Log.d("OpenFoodFacts", "--- Nazwy PO mapowaniu ---")
                        foodDtoList.forEach { food ->
                            Log.d("OpenFoodFacts", "Zmapowany produkt: ${food.name}")
                        }

                        Toast.makeText(this@MainActivity, "Zmapowano ${foodDtoList.size} produktów. Sprawdź Logcat.", Toast.LENGTH_LONG).show()

                    } else {
                        Toast.makeText(this@MainActivity, "Brak wyników", Toast.LENGTH_SHORT).show()
                        Log.d("OpenFoodFacts", "Empty response: $searchResult")
                    }

                } catch (e: Exception) {
                    val errorMessage = "Błąd OpenFoodFacts: ${e.message}"
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    Log.e("OpenFoodFactsError", errorMessage, e)
                }
            }
        }

    }
    private fun mapOpenFoodFactsToFood(offProduct: OpenFoodFactsProduct): FoodDto {
        val nutrition = offProduct.nutriments

        return FoodDto(
            id = offProduct.code ?: "",
            name = offProduct.product_name ?: "Nieznany produkt",
            brand = offProduct.brands,
            barcode = offProduct.code,
            nutritionPer100g = NutritionData(
                calories = nutrition?.energy_kcal_100g ?: 0.0,
                protein = nutrition?.proteins_100g ?: 0.0,
                fat = nutrition?.fat_100g ?: 0.0,
                carbs = nutrition?.carbohydrates_100g ?: 0.0,
                fiber = nutrition?.fiber_100g ?: 0.0,
                sugar = nutrition?.sugars_100g ?: 0.0,
                sodium = nutrition?.sodium_100g ?: 0.0
            ),
            category = "OpenFoodFacts",
            verified = false,
            addedBy = "OpenFoodFacts",
            createdAt = "",
            updatedAt = ""
        )
    }
}
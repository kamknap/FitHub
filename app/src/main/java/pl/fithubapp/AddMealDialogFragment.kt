package pl.fithubapp

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.data.CreateFoodDto
import pl.fithubapp.data.FoodDto
import pl.fithubapp.data.NutritionData
import pl.fithubapp.data.OpenFoodFactsProduct
import kotlinx.coroutines.launch
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.view.inputmethod.InputMethodManager
import pl.fithubapp.data.ChallengeType
import pl.fithubapp.data.PointsManager
import pl.fithubapp.data.ChallengeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import pl.fithubapp.data.AddMealDto
import pl.fithubapp.data.FoodItemDto
import pl.fithubapp.data.MealDto


class AddMealDialogFragment : DialogFragment() {


    interface OnMealAddedListener {
        fun onMealAdded()
    }

    var onMealAddedListener: OnMealAddedListener? = null
    private var searchJob: Job? = null
    private var llLoadingIndicator: LinearLayout? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()){ result ->
        if(result.contents != null){
            searchByBarcode(result.contents)
        }
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val mealType = arguments?.getString("mealType") ?: "Posiłek"

        val mainLayout = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search, null) as LinearLayout

        val etSearch = mainLayout.findViewById<EditText>(R.id.etSearch)
        val llSearchResults = mainLayout.findViewById<LinearLayout>(R.id.llSearchResults)
        val llActionButtons = mainLayout.findViewById<LinearLayout>(R.id.llActionButtons)
        val tilSearch = mainLayout.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilSearch)
        llLoadingIndicator = mainLayout.findViewById(R.id.llLoadingIndicator)
        
        tilSearch.hint = "Wyszukaj produkt..."

        val btnScanner = Button(requireContext()).apply {
            text = "Zeskanuj kod kreskowy"
            setTextColor(resources.getColor(R.color.white, null))
            background = resources.getDrawable(R.drawable.bg_button_rounded, null)
            isAllCaps = false
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_large),
                resources.getDimensionPixelSize(R.dimen.spacing_medium),
                resources.getDimensionPixelSize(R.dimen.spacing_large),
                resources.getDimensionPixelSize(R.dimen.spacing_medium)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener{
                Toast.makeText(requireContext(), "Uruchamiam skaner..", Toast.LENGTH_SHORT).show()
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                    setPrompt("Zeskanuj kod kreskowy")
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                }
                barcodeLauncher.launch(options)
            }
        }

        val btnAddOwnProduct = Button(
            ContextThemeWrapper(requireContext(), R.style.Widget_Fithub_Button_Outlined),
            null,
            0
        ).apply {
            text = "Dodaj produkt ręcznie"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }
            setOnClickListener {
                showAddOwnProductDialog()
            }
        }

        llActionButtons.addView(btnScanner)
        llActionButtons.addView(btnAddOwnProduct)

        etSearch.doAfterTextChanged { text ->
            val query = text.toString().trim()
            searchJob?.cancel()
            if (query.length >= 2) {
                showLoading(true)
                llSearchResults.removeAllViews()
//                opoznienie 500ms przed wyszukiwaniem
                searchJob = lifecycleScope.launch {
                    delay(500)
                    searchFoods(query, llSearchResults)
                }
            } else {
                showLoading(false)
                llSearchResults.removeAllViews()
            }
        }

        return AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle("Dodaj $mealType")
            .setView(mainLayout)
            .setNegativeButton("Zamknij") { _, _ ->
            }
            .create()
    }

    private fun showLoading(show: Boolean) {
        llLoadingIndicator?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun searchFoods(query: String, container: LinearLayout) {
        Log.d("AddMealDialog", "Wyszukiwanie: $query")
        lifecycleScope.launch {
            try {
                val localFoods = NetworkModule.api.getFoods(search = query)
                val offFoods = NetworkModule.offApi.searchProducts(query = query, limit = 10)
                var offFoodDtoList = listOf<FoodDto>()

                if(offFoods.products?.isNotEmpty() == true){
                    offFoodDtoList = offFoods.products.map { product ->
                        mapOpenFoodFactsToFood(product)
                    }
                }

                // lączenie list i nałożenie filtra na ćwiczenia
                val foodList = (localFoods.foods + offFoodDtoList)
                    .filter { food ->
                        food.category != "Exercise" &&
                                food.brand != "TrainingPlan" &&
                                food.brand != "SingleExercise"
                    }
                    .distinctBy { food ->
                        // klucz unikalności:
                        // Nazwa małymi literami
                        val nameKey = food.name.trim().lowercase()

                        // Marka
                        val brandKey = food.brand?.trim()?.lowercase() ?: ""

                        // Kalorie
                        val kcalKey = food.nutritionPer100g.calories.toInt()

                        "$nameKey|$brandKey|$kcalKey"
                    }

                showLoading(false)
                container.removeAllViews()

                if (foodList.isEmpty()) {
                    val tvEmpty = TextView(requireContext()).apply {
                        text = "Brak produktów spożywczych"
                        setPadding(16, 16, 16, 16)
                    }
                    container.addView(tvEmpty)
                }

                foodList.forEach { food ->
                    val foodView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_food_search_result, container, false)

                    val tvFoodName = foodView.findViewById<TextView>(R.id.tvFoodName)
                    val tvFoodNutrition = foodView.findViewById<TextView>(R.id.tvFoodNutrition)

                    tvFoodName.text = food.name
                    
                    val caloriesText = if (food.nutritionPer100g.calories == 0.0) {
                        "Brak danych"
                    } else {
                        "${food.nutritionPer100g.calories.toInt()} kcal/100g"
                    }
                    tvFoodNutrition.text = caloriesText

                    foodView.setOnClickListener {
                        showQuantityDialog(food.name, food.id, food)
                    }

                    container.addView(foodView)
                }
            }
            catch(e: Exception) {
                showLoading(false)
                
                if (e is CancellationException) {
                    Log.d("AddMealDialog", "Wyszukiwanie anulowane")
                    return@launch
                }
                
                Log.e("AddMealDialog", "Błąd wyszukiwania", e)
                Toast.makeText(requireContext(), "Błąd wyszukiwania: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mapOpenFoodFactsToFood(offProduct: OpenFoodFactsProduct): FoodDto {
        val nutrition = offProduct.nutriments

        val calories = nutrition?.energyKcal100g
            ?: nutrition?.energy_kcal_100g
            ?: nutrition?.energy_100g
            ?: nutrition?.energy_kj_100g?.div(4.184)
            ?: run {
                val protein = nutrition?.proteins_100g ?: 0.0
                val carbs = nutrition?.carbohydrates_100g ?: 0.0
                val fat = nutrition?.fat_100g ?: 0.0
                (protein * 4) + (carbs * 4) + (fat * 9)
            }

        Log.d("OpenFoodFacts", "Obliczone kalorie: $calories")

        return FoodDto(
            id = offProduct.code ?: "",
            name = offProduct.product_name ?: "Nieznany produkt",
            brand = offProduct.brands,
            barcode = offProduct.code,
            nutritionPer100g = NutritionData(
                calories = calories,
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

    private fun showQuantityDialog(foodName: String, foodId: String, food: FoodDto) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_quantity_input, null)
        
        val tilQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilQuantity)
        val etQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etQuantity)
        val tvProtein = dialogView.findViewById<TextView>(R.id.tvProtein)
        val tvCarbs = dialogView.findViewById<TextView>(R.id.tvCarbs)
        val tvFat = dialogView.findViewById<TextView>(R.id.tvFat)
        val tvCalories = dialogView.findViewById<TextView>(R.id.tvCalories)
        
        etQuantity.setText("100")
        
        val updateNutritionInfo = {
            val quantity = etQuantity.text.toString().toDoubleOrNull() ?: 0.0
            val factor = quantity / 100.0

            val calories = food.nutritionPer100g.calories * factor
            val protein = food.nutritionPer100g.protein * factor
            val carbs = food.nutritionPer100g.carbs * factor
            val fat = food.nutritionPer100g.fat * factor

            tvProtein.text = "Białko: %.1f g".format(protein)
            tvCarbs.text = "Węglowodany: %.1f g".format(carbs)
            tvFat.text = "Tłuszcze: %.1f g".format(fat)
            tvCalories.text = "%.0f kcal".format(calories)
        }

        etQuantity.doAfterTextChanged {
            updateNutritionInfo()
        }

        updateNutritionInfo()

        val dialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle("Dodaj $foodName")
            .setView(dialogView)
            .setPositiveButton("Dodaj") { _, _ ->
                val quantity = etQuantity.text.toString().toDoubleOrNull() ?: 100.0
                addFoodToMeal(foodId, quantity, food)
            }
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.setOnShowListener {
            etQuantity.requestFocus()
            etQuantity.selectAll()

            etQuantity.postDelayed({
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        dialog.show()
    }

    private fun addFoodToMeal(foodId: String, quantity: Double, food: FoodDto) {
        val date = arguments?.getString("date")
        val mealType = arguments?.getString("mealType")

        if(date.isNullOrEmpty() || mealType.isNullOrEmpty()){
            Toast.makeText(requireContext(), "Błąd danych", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val user = NetworkModule.api.getCurrentUser()
                
                // sprawdzanie czy produkt istnieje w lokalnej bazie
                var actualFoodId = foodId
                val existingProduct = try{
                    NetworkModule.api.getFoodById(actualFoodId)
                } catch (e: Exception){
                    null
                }
                // jesli produktu nie ma w bazie to dodaje go
                    if(existingProduct == null || existingProduct.id.isNullOrEmpty()){
                        val createFoodDto = CreateFoodDto(
                            name = food.name,
                            brand = food.brand,
                            barcode = food.barcode,
                            nutritionPer100g = food.nutritionPer100g,
                            category = food.category,
                            addedBy = user.id
                        )
                        val createdFood = NetworkModule.api.createFood(createFoodDto)
                        actualFoodId = createdFood.id
                        Log.d("AddMealDialog", "Produkt dodany z ID: $actualFoodId")
                    } else {
                        Log.d("AddMealDialog", "Produkt już istnieje z ID: ${existingProduct.id}")
                        actualFoodId = existingProduct.id
        }

                // jesli produkt znajduje sie w lokalnej bazie to dodaje go do posiłku
                    val foodItem = FoodItemDto(
                        foodId = actualFoodId,
                        quantity = quantity
                    )

                    val meal = MealDto(
                        name = mealType,
                        foods = listOf(foodItem)
                    )

                    val addMealData = AddMealDto(meal = meal)

                    NetworkModule.api.addMeal(
                        date = date,
                        addMealDto = addMealData
                    )

                    ChallengeManager.checkChallengeProgress(ChallengeType.MEAL_COUNT, 1.0)

                    try {
                        Log.d("AddMealDialog", "Posiłek dodany, przyznaję punkty...")
                        val leveledUp = PointsManager.addPoints(PointsManager.ActionType.MEAL)

                        if (leveledUp) {
                            (activity as? UserMainActivity)?.showLevelUpDialog()
                        }
                    } catch (e: Exception) {
                        Log.e("AddMealDialog", "Nie udało się dodać punktów: ${e.message}")
                    }

                    Toast.makeText(
                        requireContext(),
                        "Dodano ${food.name} do $mealType",
                        Toast.LENGTH_SHORT
                    ).show()
                    onMealAddedListener?.onMealAdded()
                    dismiss()
                }
            catch (e: Exception){
                if (e is CancellationException) {
                    throw e
                }
                Log.e("AddMealDialog", "Błąd wyszukiwania", e)
            }
        }
    }

    private fun searchByBarcode(barcode: String) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Szukam produktu: $barcode", Toast.LENGTH_SHORT).show()

                try {
                    val localProduct = NetworkModule.api.getFoodByBarcode(barcode)
                    showQuantityDialog(localProduct.name, localProduct.id, localProduct)
                    return@launch
                } catch (e: Exception) {
                    Log.d("BarcodeScanner", "Produkt nie znaleziony lokalnie, sprawdzam OFF")
                }

                val offProduct = NetworkModule.offBarcodeApi.getProductByBarcode(barcode)

                if (offProduct.status == 1 && offProduct.product != null) {
                    val mappedFood = mapOpenFoodFactsToFood(offProduct.product)
                    if (mappedFood.name == "Nieznany produkt"){
                        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
                            .setTitle("Błąd")
                            .setMessage("Nie znaleziono produktu dla kodu kreskowego: $barcode. Spróbuj ponownie lub dodaj produkt przy pomocy ręcznego dodawania")
                            .setPositiveButton("OK") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                    else{
                        showQuantityDialog(mappedFood.name, mappedFood.id, mappedFood)
                    }
                } else {
                    AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
                        .setTitle("Błąd")
                        .setMessage("Nie znaleziono produktu dla kodu kreskowego: $barcode")
                        .setPositiveButton("OK") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }

            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Błąd skanowania", e)
                Toast.makeText(
                    requireContext(),
                    "Błąd: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showAddOwnProductDialog() {
        val dialogView = ScrollView(requireContext())
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        dialogView.addView(layout)

        val etName = EditText(requireContext()).apply { hint = "Nazwa produktu" }
        val etBrand = EditText(requireContext()).apply { hint = "Marka (opcjonalnie)" }
        val etBarcode = EditText(requireContext()).apply { hint = "Kod kreskowy (opcjonalnie)" }
        val etCalories = EditText(requireContext()).apply {
            hint = "Kalorie (kcal/100g)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etProtein = EditText(requireContext()).apply {
            hint = "Białko (g/100g)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etCarbs = EditText(requireContext()).apply {
            hint = "Węglowodany (g/100g)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val etFat = EditText(requireContext()).apply {
            hint = "Tłuszcze (g/100g)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        layout.addView(etName)
        layout.addView(etBrand)
        layout.addView(etBarcode)
        layout.addView(etCalories)
        layout.addView(etProtein)
        layout.addView(etCarbs)
        layout.addView(etFat)

        val dialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Fithub_Dialog)
            .setTitle("Dodaj własny produkt")
            .setView(dialogView)
            .setPositiveButton("Dodaj", null)
            .setNegativeButton("Anuluj", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val name = etName.text.toString().trim()
                val caloriesText = etCalories.text.toString()

                if (name.isBlank()) {
                    etName.error = "Nazwa jest wymagana"
                    return@setOnClickListener
                }
                if (caloriesText.isBlank()) {
                    etCalories.error = "Kalorie są wymagane"
                    return@setOnClickListener
                }

                val calories = caloriesText.toDoubleOrNull()
                if (calories == null) {
                    etCalories.error = "Nieprawidłowa wartość kalorii"
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    try {
                        val user = NetworkModule.api.getCurrentUser()
                        
                        val newFood = CreateFoodDto(
                            name = name,
                            brand = etBrand.text.toString().ifEmpty { null },
                            barcode = etBarcode.text.toString().ifEmpty { null },
                            nutritionPer100g = NutritionData(
                                calories = calories,
                                protein = etProtein.text.toString().toDoubleOrNull() ?: 0.0,
                                carbs = etCarbs.text.toString().toDoubleOrNull() ?: 0.0,
                                fat = etFat.text.toString().toDoubleOrNull() ?: 0.0,
                                fiber = 0.0,
                                sugar = 0.0,
                                sodium = 0.0
                            ),
                            category = "User",
                            addedBy = user.id
                        )
                        
                        val checkFoodExist = NetworkModule.api.getFoods(name)
                        if (checkFoodExist.foods.isNotEmpty()) {
                            etName.error = "Produkt o tej nazwie już istnieje"
                        } else {
                            val createdFood = NetworkModule.api.createFood(newFood)
                            Log.d("AddOwnProduct", "Dodano nowy produkt: ${createdFood.name}")
                            Toast.makeText(requireContext(), "Dodano produkt: ${createdFood.name}", Toast.LENGTH_SHORT).show()

                            dialog.dismiss()
                            showQuantityDialog(createdFood.name, createdFood.id, createdFood)
                        }
                    } catch (e: Exception) {
                        Log.e("AddOwnProduct", "Błąd dodawania produktu", e)
                        Toast.makeText(requireContext(), "Błąd: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

}




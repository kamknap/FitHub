package pl.fithubapp

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import pl.fithubapp.data.FoodDto
import pl.fithubapp.data.MealWithFoodsDto
import pl.fithubapp.data.PointsManager
import kotlinx.coroutines.launch
import pl.fithubapp.data.UpdateFoodQuantityDto
import pl.fithubapp.logic.UserCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class UserDiaryFragment : Fragment(R.layout.fragment_user_diary), AddMealDialogFragment.OnMealAddedListener {

    companion object {
        private val sectionStates = mutableMapOf<Int, Boolean>()
    }

    private lateinit var llDaysContainer: LinearLayout
    private lateinit var hsvWeek: HorizontalScrollView
    private var selectedDate = LocalDate.now()
    private lateinit var btnBreakfast: ImageButton
    private lateinit var btnLunch: ImageButton
    private lateinit var btnDinner: ImageButton
    private lateinit var btnSnacks: ImageButton
    private lateinit var llBreakfastMeals: LinearLayout
    private lateinit var llLunchMeals: LinearLayout
    private lateinit var llDinnerMeals: LinearLayout
    private lateinit var llSnacksMeals: LinearLayout
    private lateinit var tvDailyTotal: TextView
    private lateinit var tvBreakfast: TextView
    private lateinit var tvLunch: TextView
    private lateinit var tvDinner: TextView
    private lateinit var tvSnacks: TextView
    private lateinit var tvTraining: TextView
    private lateinit var llTraining: LinearLayout
    private lateinit var btnTraining: ImageButton
    private var breakfastCalories = 0.0
    private var breakfastProtein = 0.0
    private var breakfastFat = 0.0
    private var breakfastCarbs = 0.0
    private var lunchCalories = 0.0
    private var lunchProtein = 0.0
    private var lunchFat = 0.0
    private var lunchCarbs = 0.0
    private var dinnerCalories = 0.0
    private var dinnerProtein = 0.0
    private var dinnerFat = 0.0
    private var dinnerCarbs = 0.0
    private var snacksCalories = 0.0
    private var snacksProtein = 0.0
    private var snacksFat = 0.0
    private var snacksCarbs = 0.0
    private var dailyTotalCalories = 0.0
    private var dailyTotalProtein = 0.0
    private var dailyTotalFat = 0.0
    private var dailyTotalCarbs = 0.0
    private var loadedBreakfast = false
    private var loadedLunch = false
    private var loadedDinner = false
    private var loadedSnacks = false
    private var currentLoadId = 0
    private var trainingCalories = 0.0
    private val currentUserId: String
        get() = AuthManager.currentUserId ?: ""
    enum class TrainingDeleteItemType{
        NONE,
        SINGLE_EXERCISE,
        FULL_TRAINING
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llDaysContainer = view.findViewById(R.id.llDaysContainer)
        hsvWeek = view.findViewById(R.id.hsvWeek)
        llBreakfastMeals = view.findViewById(R.id.llBreakfastMeals)
        llLunchMeals = view.findViewById(R.id.llLunchMeals)
        llDinnerMeals = view.findViewById(R.id.llDinnerMeals)
        llSnacksMeals = view.findViewById(R.id.llSnacksMeals)
        llTraining = view.findViewById(R.id.llTraining)
        tvDailyTotal = view.findViewById<TextView>(R.id.tvDailyTotal)
        tvBreakfast = view.findViewById(R.id.tvBreakfast)
        tvLunch = view.findViewById(R.id.tvLunch)
        tvDinner = view.findViewById(R.id.tvDinner)
        tvSnacks = view.findViewById(R.id.tvSnacks)
        tvTraining = view.findViewById(R.id.tvTraining)
        btnBreakfast = view.findViewById(R.id.btnAddBreakfast)
        btnLunch = view.findViewById(R.id.btnAddLunch)
        btnDinner = view.findViewById(R.id.btnAddDinner)
        btnSnacks = view.findViewById(R.id.btnAddSnacks)
        btnTraining = view.findViewById(R.id.btnTraining)

        initDaysView()

        btnBreakfast.setOnClickListener {
            openMealDialog("Śniadanie")

        }
        btnLunch.setOnClickListener {
            openMealDialog("Obiad")
        }
        btnDinner.setOnClickListener {
            openMealDialog("Kolacja")
        }
        btnSnacks.setOnClickListener {
            openMealDialog("Przekąski")
        }
        btnTraining.setOnClickListener {
            openExerciseDialog()
        }

        // Rozwijanie i zwijanie posilkow
        tvBreakfast.setOnClickListener {
            toggleMealSection(llBreakfastMeals)
        }

        tvLunch.setOnClickListener {
            toggleMealSection(llLunchMeals)
        }

        tvDinner.setOnClickListener {
            toggleMealSection(llDinnerMeals)
        }

        tvSnacks.setOnClickListener {
            toggleMealSection(llSnacksMeals)
        }

        tvTraining.setOnClickListener {
            toggleMealSection(llTraining)
        }

        // Przywracanie stanów sekcji po zmianie widoku
        restoreSectionStates()
    }

    private fun toggleMealSection(container: LinearLayout) {
        if (container.visibility == View.VISIBLE) {
            // animacja
            container.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    container.visibility = View.GONE
                    container.alpha = 1f
                    // Zapisanie stanu
                    sectionStates[container.id] = false
                }
        } else {
            container.visibility = View.VISIBLE
            container.alpha = 0f
            container.animate()
                .alpha(1f)
                .setDuration(200)
            sectionStates[container.id] = true
        }
    }

    private fun restoreSectionStates() {
        // Przywróć widoczność sekcji bez animacji przy powrocie do fragmentu
        view?.let { v ->
            restoreSectionState(v, R.id.llBreakfastMeals)
            restoreSectionState(v, R.id.llLunchMeals)
            restoreSectionState(v, R.id.llDinnerMeals)
            restoreSectionState(v, R.id.llSnacksMeals)
            restoreSectionState(v, R.id.llTraining)
        }
    }

    private fun restoreSectionState(rootView: View, viewId: Int) {
        val container = rootView.findViewById<LinearLayout>(viewId)
        val isVisible = sectionStates[viewId] ?: true // Domyślnie widoczne
        container?.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun openMealDialog(mealType: String){
        val formattedDate = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val dialog = AddMealDialogFragment().apply{
            arguments = Bundle().apply {
                putString("mealType", mealType)
                putString("userId", currentUserId)
                putString("date", formattedDate)
            }
        }
        dialog.onMealAddedListener = this
        dialog.show(parentFragmentManager, "AddMealDialog")
    }

    override fun onMealAdded() {
        loadDataForDate(selectedDate)
    }

    private fun addMealToList
                (container: LinearLayout,
                 mealName: String,
                 itemId: String,
                 protein: Int = 0,
                 fat: Int = 0,
                 carbs: Int = 0,
                 calories: Int = 0,
                 currentQuantity: Double,
                 foodDto: FoodDto,
                 trainingType: TrainingDeleteItemType = TrainingDeleteItemType.NONE
    ) {
        val mealView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_meal, container, false)

        val tvMealName = mealView.findViewById<TextView>(R.id.tvMealName)
        val tvMealProtein = mealView.findViewById<TextView>(R.id.tvProtein)
        val tvMealFat = mealView.findViewById<TextView>(R.id.tvFat)
        val tvMealCarbs = mealView.findViewById<TextView>(R.id.tvCarbs)
        val tvMealCalories = mealView.findViewById<TextView>(R.id.tvMealCalories)
        val btnDeleteMeal = mealView.findViewById<ImageButton>(R.id.btnDeleteMeal)

        tvMealName.text = mealName
        tvMealProtein.text = "$protein B"
        tvMealFat.text = "$fat T"
        tvMealCarbs.text = "$carbs W"
        tvMealCalories.text = "$calories Kcal"

        btnDeleteMeal.setOnClickListener {
            deleteFoodByItemId(itemId, trainingType)
        }


        mealView.setOnClickListener {
            when (trainingType) {
                TrainingDeleteItemType.NONE ->
                    showEditQuantityDialog(mealName, itemId, currentQuantity, foodDto)
                else ->
                    Toast.makeText(requireContext(), "Nie można edytować treningu", Toast.LENGTH_SHORT).show()
            }
        }

        container.addView(mealView)
    }

    private fun showEditQuantityDialog(foodName: String, itemId: String, currentQuantity: Double, food: FoodDto) {
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val inputLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val etQuantity = EditText(requireContext()).apply {
            hint = "Ilość"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format("%.0f", currentQuantity))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val unitTextView = TextView(requireContext()).apply {
            text = "g"
            textSize = 18F
        }

        inputLayout.addView(etQuantity)
        inputLayout.addView(unitTextView)

        val tvNutritionInfo = TextView(requireContext()).apply {
            textSize = 14f
            setPadding(0, 24, 0, 0)
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        val updateNutritionInfo = {
            val quantity = etQuantity.text.toString().toDoubleOrNull() ?: 0.0
            val factor = quantity / 100.0

            val calories = food.nutritionPer100g.calories * factor
            val protein = food.nutritionPer100g.protein * factor
            val carbs = food.nutritionPer100g.carbs * factor
            val fat = food.nutritionPer100g.fat * factor

            tvNutritionInfo.text = String.format(
                "Kalorie: %.1f kcal\nBiałko: %.1f g\nWęglowodany: %.1f g\nTłuszcze: %.1f g",
                calories, protein, carbs, fat
            )
        }

        etQuantity.doAfterTextChanged {
            updateNutritionInfo()
        }

        updateNutritionInfo()

        dialogLayout.addView(inputLayout)
        dialogLayout.addView(tvNutritionInfo)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edytuj $foodName")
            .setView(dialogLayout)
            .setPositiveButton("Zapisz") { _, _ ->
                val newQuantity = etQuantity.text.toString().toDoubleOrNull() ?: currentQuantity
                updateFoodQuantity(itemId, newQuantity)
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

    private fun updateFoodQuantity(itemId: String, newQuantity: Double) {
        val dateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

        lifecycleScope.launch {
            try {
                val updateDto = UpdateFoodQuantityDto(quantity = newQuantity)
                NetworkModule.api.updateFoodQuantity(
                    date = dateStr,
                    itemId = itemId,
                    request = updateDto
                )
                Toast.makeText(requireContext(), "Zaktualizowano ilość", Toast.LENGTH_SHORT).show()
                loadDataForDate(selectedDate)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Błąd aktualizacji: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initDaysView() {
        val currentMonth = selectedDate.monthValue
        val currentYear = selectedDate.year

        val yearMonth = YearMonth.of(currentYear, currentMonth)
        val lastDayOfMonth = yearMonth.lengthOfMonth()

        for (day in 1..lastDayOfMonth) {
            val dayDate = LocalDate.of(currentYear, currentMonth, day)
            addDayView(dayDate, day)
        }

        selectTodayAndScroll()
    }

    private fun addDayView(dayDate: LocalDate, dayNumber: Int) {
        val dayView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_day, llDaysContainer, false)

        val tvDayLetter = dayView.findViewById<TextView>(R.id.tvDayLetter)
        val tvDayNumber = dayView.findViewById<TextView>(R.id.tvDayNumber)

        val dayOfWeek = dayDate.dayOfWeek
        tvDayLetter.text = getDayLetter(dayOfWeek)

        tvDayNumber.text = dayNumber.toString()

        dayView.setOnClickListener {
            selectDay(dayView, dayDate)
        }

        llDaysContainer.addView(dayView)
    }

    private fun getDayLetter(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "P"
            DayOfWeek.TUESDAY -> "W"
            DayOfWeek.WEDNESDAY -> "Ś"
            DayOfWeek.THURSDAY -> "C"
            DayOfWeek.FRIDAY -> "P"
            DayOfWeek.SATURDAY -> "S"
            DayOfWeek.SUNDAY -> "N"
        }
    }

    private fun isSameDay(date1: LocalDate, date2: LocalDate): Boolean {
        return date1 == date2
    }

    private fun selectDay(dayView: View, dayDate: LocalDate) {
        for (i in 0 until llDaysContainer.childCount) {
            llDaysContainer.getChildAt(i).isSelected = false
        }

        dayView.isSelected = true
        selectedDate = dayDate

        loadDataForDate(selectedDate)
    }

    private fun selectTodayAndScroll() {
        val today = LocalDate.now()

        for (i in 0 until llDaysContainer.childCount) {
            val dayView = llDaysContainer.getChildAt(i)
            val dayDate = LocalDate.of(
                selectedDate.year,
                selectedDate.monthValue,
                i + 1
            )

            if (isSameDay(dayDate, today)) {
                dayView.isSelected = true
                selectedDate = dayDate

                dayView.post {
                    scrollToDay(dayView)
                }

                loadDataForDate(selectedDate)
                break
            }
        }
    }

    private fun scrollToDay(dayView: View) {
        val scrollViewWidth = hsvWeek.width
        val dayViewLeft = dayView.left
        val dayViewWidth = dayView.width

        val scrollX = dayViewLeft - (scrollViewWidth / 2) + (dayViewWidth / 2)

        hsvWeek.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
    }


    private fun loadAllMealsForUser(date: String) {
        val loadIdAtStart = currentLoadId

        lifecycleScope.launch {
            try {
                val dailyNutrition = NetworkModule.api.getDailyNutrition(date)

                if (loadIdAtStart != currentLoadId) return@launch

                val breakfastMeals = dailyNutrition.meals.filter { meal ->
                    meal.name.lowercase().contains("śniadanie")
                }
                val lunchMeals = dailyNutrition.meals.filter { meal ->
                    meal.name.lowercase().contains("obiad")
                }
                val dinnerMeals = dailyNutrition.meals.filter { meal ->
                    meal.name.lowercase().contains("kolacja")
                }
                val snacksMeals = dailyNutrition.meals.filter { meal ->
                    meal.name.lowercase().contains("przekąski")
                }
                val trainingMeals = dailyNutrition.meals.filter { meal ->
                    meal.name.lowercase().contains("trening")
                }

                // wyswietlanie posilkow
                displayMeals(breakfastMeals, "śniadanie")
                displayMeals(lunchMeals, "obiad")
                displayMeals(dinnerMeals, "kolacja")
                displayMeals(snacksMeals, "przekąski")
                displayMeals(trainingMeals, "trening")

                // flagi
                loadedBreakfast = true
                loadedLunch = true
                loadedDinner = true
                loadedSnacks = true

                // aktualizacja totals
                if (loadIdAtStart == currentLoadId) {
                    updateDailyTotals(dailyNutrition.dailyTotals.calorieGoal)
                }

            } catch (e: Exception) {
                if (loadIdAtStart != currentLoadId) return@launch
                Toast.makeText(requireContext(), "Błąd: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayMeals(meals: List<MealWithFoodsDto>, mealType: String) {
        val container = when(mealType.lowercase()) {
            "śniadanie" -> llBreakfastMeals
            "obiad" -> llLunchMeals
            "kolacja" -> llDinnerMeals
            "przekąski" -> llSnacksMeals
            "trening" -> llTraining
            else -> return
        }

        container.removeAllViews()

        when (mealType.lowercase()) {
            "śniadanie" -> {
                breakfastCalories = 0.0; breakfastProtein = 0.0; breakfastFat = 0.0; breakfastCarbs = 0.0
            }
            "obiad" -> {
                lunchCalories = 0.0; lunchProtein = 0.0; lunchFat = 0.0; lunchCarbs = 0.0
            }
            "kolacja" -> {
                dinnerCalories = 0.0; dinnerProtein = 0.0; dinnerFat = 0.0; dinnerCarbs = 0.0
            }
            "przekąski" -> {
                snacksCalories = 0.0; snacksProtein = 0.0; snacksFat = 0.0; snacksCarbs = 0.0
            }
            "trening" -> {
                trainingCalories = 0.0
            }
        }

        meals.forEach { meal ->
            meal.foods.forEach { foodItem ->
                val food = foodItem.foodId
                val quantity = foodItem.quantity / 100.0

                val protein = (food.nutritionPer100g.protein * quantity).roundToInt()
                val fat = (food.nutritionPer100g.fat * quantity).roundToInt()
                val carbs = (food.nutritionPer100g.carbs * quantity).roundToInt()
                val calories = (food.nutritionPer100g.calories * quantity).roundToInt()

                when (mealType.lowercase()) {
                    "śniadanie" -> {
                        breakfastCalories += calories
                        breakfastProtein += protein
                        breakfastFat += fat
                        breakfastCarbs += carbs
                    }

                    "obiad" -> {
                        lunchCalories += calories
                        lunchProtein += protein
                        lunchFat += fat
                        lunchCarbs += carbs
                    }

                    "kolacja" -> {
                        dinnerCalories += calories
                        dinnerProtein += protein
                        dinnerFat += fat
                        dinnerCarbs += carbs
                    }

                    "przekąski" -> {
                        snacksCalories += calories
                        snacksProtein += protein
                        snacksFat += fat
                        snacksCarbs += carbs
                    }

                    "trening" -> {
                        trainingCalories += calories
                    }
                }

                val itemTrainingType = if (mealType.lowercase() == "trening") {
                    when (foodItem.foodId.brand) {
                        "TrainingPlan" -> TrainingDeleteItemType.FULL_TRAINING
                        "SingleExercise" -> TrainingDeleteItemType.SINGLE_EXERCISE
                        else -> TrainingDeleteItemType.SINGLE_EXERCISE //stare wpisy
                    }
                } else {
                    TrainingDeleteItemType.NONE
                }

                if (mealType.lowercase() == "trening") {
                    addMealToList(
                        container = container,
                        mealName = food.name,
                        itemId = foodItem.itemId,
                        calories = calories,
                        currentQuantity = foodItem.quantity,
                        foodDto = food,
                        trainingType = itemTrainingType
                    )
                } else {
                    addMealToList(
                        container = container,
                        mealName = "${food.name} (${foodItem.quantity.toInt()}g)",
                        itemId = foodItem.itemId,
                        protein = protein,
                        fat = fat,
                        carbs = carbs,
                        calories = calories,
                        currentQuantity = foodItem.quantity,
                        foodDto = food,
                        trainingType = TrainingDeleteItemType.NONE
                    )
                }
            }
        }

        when (mealType.lowercase()) {
            "śniadanie" -> tvBreakfast.text = formatMealTitle(
                "Śniadanie",
                "${breakfastCalories.roundToInt()} kcal, ${breakfastProtein.roundToInt()} B, ${breakfastFat.roundToInt()} T, ${breakfastCarbs.roundToInt()} W"
            )
            "obiad" -> tvLunch.text = formatMealTitle(
                "Obiad",
                "${lunchCalories.roundToInt()} kcal, ${lunchProtein.roundToInt()} B, ${lunchFat.roundToInt()} T, ${lunchCarbs.roundToInt()} W"
            )
            "kolacja" -> tvDinner.text = formatMealTitle(
                "Kolacja",
                "${dinnerCalories.roundToInt()} kcal, ${dinnerProtein.roundToInt()} B, ${dinnerFat.roundToInt()} T, ${dinnerCarbs.roundToInt()} W"
            )
            "przekąski" -> tvSnacks.text = formatMealTitle(
                "Przekąski",
                "${snacksCalories.roundToInt()} kcal, ${snacksProtein.roundToInt()} B, ${snacksFat.roundToInt()} T, ${snacksCarbs.roundToInt()} W"
            )
            "trening" -> tvTraining.text = formatMealTitle(
                "Trening",
                "${abs(trainingCalories).roundToInt()} kcal spalono"
            )
        }
    }

    private fun formatMealTitle(title: String, macros: String): SpannableString {
        val fullText = "$title ($macros)"
        val spannable = SpannableString(fullText)
        
        val startIndex = title.length
        val endIndex = fullText.length
        
        spannable.setSpan(
            RelativeSizeSpan(0.8f),
            startIndex,
            endIndex,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        return spannable
    }


    private fun loadDataForDate(date: LocalDate) {
        currentLoadId++
        clearAllMacrosAndUi()

        val formattedDate = date.toString()

        loadAllMealsForUser(formattedDate)
    }

    private fun clearAllMacrosAndUi() {
        breakfastCalories = 0.0; breakfastProtein = 0.0; breakfastFat = 0.0; breakfastCarbs = 0.0
        lunchCalories     = 0.0; lunchProtein     = 0.0; lunchFat     = 0.0; lunchCarbs     = 0.0
        dinnerCalories    = 0.0; dinnerProtein    = 0.0; dinnerFat    = 0.0; dinnerCarbs    = 0.0
        snacksCalories    = 0.0; snacksProtein    = 0.0; snacksFat    = 0.0; snacksCarbs    = 0.0
        dailyTotalCalories = 0.0; dailyTotalProtein = 0.0; dailyTotalFat = 0.0; dailyTotalCarbs = 0.0
        trainingCalories = 0.0


        loadedBreakfast = false
        loadedLunch = false
        loadedDinner = false
        loadedSnacks = false

        llBreakfastMeals.removeAllViews()
        llLunchMeals.removeAllViews()
        llDinnerMeals.removeAllViews()
        llSnacksMeals.removeAllViews()
        llTraining.removeAllViews()

        tvBreakfast.text = "Śniadanie"
        tvLunch.text = "Obiad"
        tvDinner.text = "Kolacja"
        tvSnacks.text = "Przekąski"
        tvDailyTotal.text = ""
        tvTraining.text = "Trening"
    }

    private fun updateDailyTotals(calorieGoal: Double) {
        if (loadedBreakfast && loadedLunch && loadedDinner && loadedSnacks) {
            val calculator = UserCalculator()
            val targets = calculator.calculateMacroTargets(calorieGoal)
            dailyTotalCalories = breakfastCalories + lunchCalories + dinnerCalories + snacksCalories + trainingCalories
            dailyTotalProtein  = breakfastProtein  + lunchProtein  + dinnerProtein  + snacksProtein
            dailyTotalFat      = breakfastFat      + lunchFat      + dinnerFat      + snacksFat
            dailyTotalCarbs    = breakfastCarbs    + lunchCarbs    + dinnerCarbs    + snacksCarbs

            val tertiaryColor = ContextCompat.getColor(requireContext(), R.color.text_tertiary)

            val builder = SpannableStringBuilder()

            fun appendMacro(current: Double, target: Int, unit: String, isLast: Boolean = false) {
                builder.append("${current.roundToInt()}")

                val targetPart = "/$target "
                val startTarget = builder.length
                builder.append(targetPart)
                builder.setSpan(
                    RelativeSizeSpan(0.8f),
                    startTarget,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val unitPart = if (isLast) unit else "$unit, "
                val startUnit = builder.length
                builder.append(unitPart)
                builder.setSpan(
                    ForegroundColorSpan(tertiaryColor),
                    startUnit,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            appendMacro(dailyTotalCalories, calorieGoal.toInt(), "kcal")
            appendMacro(dailyTotalProtein, targets.protein, "B")
            appendMacro(dailyTotalFat, targets.fat, "T")
            appendMacro(dailyTotalCarbs, targets.carbs, "W", isLast = true)

            tvDailyTotal.text = builder
        }
    }

    private fun deleteFoodByItemId(itemId: String, trainingType: TrainingDeleteItemType) {
        val dateStr = selectedDate.toString()

        lifecycleScope.launch {
            try {
                NetworkModule.api.deleteFoodByItemId(
                    date = dateStr,
                    itemId = itemId
                )

                try {
                    val actionType = when (trainingType) {
                        TrainingDeleteItemType.FULL_TRAINING -> PointsManager.ActionType.TRAINING_FULL
                        TrainingDeleteItemType.SINGLE_EXERCISE -> PointsManager.ActionType.TRAINING
                        TrainingDeleteItemType.NONE -> PointsManager.ActionType.MEAL
                    }

                    PointsManager.removePoints(actionType)
                    Log.d("UserDiary", "Odjęto punkty za: $actionType")

                } catch (e: Exception) {
                    Log.e("UserDiary", "Błąd odejmowania punktów: ${e.message}")
                }

                loadDataForDate(selectedDate)
                val message = when(trainingType) {
                    TrainingDeleteItemType.FULL_TRAINING -> "Usunięto plan treningowy"
                    TrainingDeleteItemType.SINGLE_EXERCISE -> "Usunięto ćwiczenie"
                    TrainingDeleteItemType.NONE -> "Usunięto produkt"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Błąd usuwania: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openExerciseDialog() {
        val formattedDate = selectedDate.toString()

        val dialog = AddExerciseDialogFragment().apply {
            arguments = Bundle().apply {
                putString("userId", currentUserId)
                putString("date", formattedDate)
            }
        }
        dialog.onExerciseAddedListener = object : AddExerciseDialogFragment.OnExerciseAddedListener {
            override fun onExerciseAdded() {
                loadDataForDate(selectedDate)
            }
        }
        dialog.show(parentFragmentManager, "AddExerciseDialog")
    }

}